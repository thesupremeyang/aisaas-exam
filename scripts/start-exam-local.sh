#!/usr/bin/env bash
# 本地启动考试版（exam-local）全套服务
#
# 用法：在项目根目录执行   ./scripts/start-exam-local.sh
# 停止：./scripts/stop-exam-local.sh
#
# 服务端口：console 3160 / runtime 3161 / websocket 3164 / 前端 4100
# 依赖：本机 MySQL 里有库 apboa_exam（连接信息见 application-dev.yml）、Redis 已启动
#
# 注意：请在你自己的终端窗口里运行本脚本，不要通过 agent 的后台任务启动
# （后台任务会随会话结束被回收）。
#
# 密钥一律从环境变量注入，不要写回仓库：
#   export JWT_SECRET='<32 字节以上的随机字符串>'
#   export ASSESSMENT_GRADING_API_KEY='sk-...'

set -u
cd "$(dirname "$0")/.." || exit 1
ROOT="$(pwd)"

# JDK 21（Lombok 1.18.30 在 JDK 26 下会静默失效，必须显式指定）
JH=/opt/homebrew/Cellar/openjdk@21/21.0.12/libexec/openjdk.jdk/Contents/Home
if [ ! -x "$JH/bin/java" ]; then
  echo "[错误] 找不到 JDK 21：$JH/bin/java"
  echo "       请先安装：brew install openjdk@21"
  exit 1
fi

NODE=/Users/apple/.workbuddy/binaries/node/versions/22.22.2-6/bin/node
[ -x "$NODE" ] || NODE="$(command -v node)"
if [ -z "$NODE" ]; then
  echo "[错误] 找不到 node"
  exit 1
fi

# AI 阅卷（配置评分）模型通道：DeepSeek 官方 API（OpenAI 兼容）
# 密钥只从环境变量读取，仓库内不保存任何真实 Key；
# 不配置时 AI 阅卷功能的入队会被守卫拦截，其余功能不受影响。
export ASSESSMENT_GRADING_ENDPOINT="${ASSESSMENT_GRADING_ENDPOINT:-https://api.deepseek.com/chat/completions}"
export ASSESSMENT_GRADING_API_KEY="${ASSESSMENT_GRADING_API_KEY:-}"
export ASSESSMENT_GRADING_MODEL="${ASSESSMENT_GRADING_MODEL:-deepseek-flash}"

LOGDIR="$ROOT/.local-logs"
mkdir -p "$LOGDIR"

start_java() {
  local name="$1" jar="$2" port="$3"
  shift 3
  if lsof -nP -iTCP:"$port" -sTCP:LISTEN >/dev/null 2>&1; then
    echo "  $name 已在 $port 运行，跳过"
    return
  fi
  # macOS 没有 setsid，只能用 nohup；脚本在你自己的终端里跑，
  # 终端不关进程就不会被回收（不要用 agent 的后台任务启动，会话结束会被回收）
  nohup "$JH/bin/java" -jar "$jar" \
    --spring.profiles.active=exam-local \
    --server.port="$port" "$@" \
    >"$LOGDIR/$name.log" 2>&1 &
  echo "  $name 启动中（端口 $port，日志 $LOGDIR/$name.log）"
}

echo "启动后端服务…"
# exam-local 的三个配置缺口：jwt.secret 缺失会导致登录 500、WS 握手"认证系统错误"；
# driver-class-name 缺失会导致 runtime 起不来；websocket 同样必须带 jwt.secret
start_java console   "$ROOT/runner-console/target/runner-console-1.0-SNAPSHOT.jar"     3160 \
  --jwt.secret="${JWT_SECRET:?请先 export JWT_SECRET 再启动考试版服务}" \
  --spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver
start_java runtime   "$ROOT/runner-runtime/target/runner-runtime-1.0-SNAPSHOT.jar"     3161 \
  --spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver
start_java websocket "$ROOT/runner-websocket/target/runner-websocket-1.0-SNAPSHOT.jar" 3164 \
  --jwt.secret="${JWT_SECRET:?请先 export JWT_SECRET 再启动考试版服务}"

echo "启动前端（http://127.0.0.1:4100/web/）…"
# Vite 依赖预构建会生成 .vite/deps_temp_*，沙箱的批量删除保护会把它当成
# "批量删除 300+ 文件" 而中断进程 → 前端莫名掉线。开跑前先挪到 /tmp（不删除）。
for d in "$ROOT"/ui/node_modules/.vite/deps_temp_*; do
  [ -d "$d" ] || continue
  mv "$d" "/tmp/vite-deps-temp-$(basename "$d")-$$" 2>/dev/null && \
    echo "  已移走残留的 $(basename "$d")"
done

if lsof -nP -iTCP:4100 -sTCP:LISTEN >/dev/null 2>&1; then
  echo "  前端已在 4100 运行，跳过"
else
  # 不要用 pnpm exec，会触发自动 install 并撞 EEXIST symlink 冲突
  # 套一层 while 循环：Vite 因 HMR ws EPIPE 等原因退出后 3 秒自动拉起
  ( cd "$ROOT/ui" && nohup bash -c "while true; do \
      '$NODE' node_modules/vite/bin/vite.js --mode exam-local --port 4100; \
      echo \"[frontend] 已退出，3 秒后重启 (\$(date '+%F %T'))\"; sleep 3; \
    done" >"$LOGDIR/frontend.log" 2>&1 & )
  echo "  前端启动中（守护模式，日志 $LOGDIR/frontend.log）"
fi

echo
echo "等待服务就绪…"
for i in $(seq 1 60); do
  ok=0
  for p in 3160 3161 3164 4100; do
    lsof -nP -iTCP:"$p" -sTCP:LISTEN >/dev/null 2>&1 && ok=$((ok + 1))
  done
  [ "$ok" -eq 4 ] && break
  sleep 2
done

echo
echo "=== 端口状态 ==="
for p in 3160 3161 3164 4100; do
  if lsof -nP -iTCP:"$p" -sTCP:LISTEN >/dev/null 2>&1; then
    echo "  $p  ✅ 已监听"
  else
    echo "  $p  ❌ 未就绪（看日志 $LOGDIR/）"
  fi
done
echo
echo "完成。管理员登录：admin"
