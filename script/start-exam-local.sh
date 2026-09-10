#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
LOG_DIR="$ROOT_DIR/.tmp_logs/exam-local"
PID_DIR="$ROOT_DIR/.apboa-exam/run"

# 项目目标版本为 Java 21；本机若未显式设置 JAVA_HOME，优先使用 Homebrew 的 Java 21。
if [[ -z "${JAVA_HOME:-}" && -x "/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home/bin/java" ]]; then
  export JAVA_HOME="/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home"
  export PATH="$JAVA_HOME/bin:$PATH"
fi

mkdir -p "$LOG_DIR" "$PID_DIR"
cd "$ROOT_DIR"

echo "正在构建考试版后端及其依赖..."
mvn install -DskipTests \
  -pl runner-console,runner-file,runner-runtime,runner-websocket,runner-proxy \
  -am
echo "后端构建完成，开始启动服务。"

start_backend() {
  local name="$1"
  local profile="$2"
  local port="$3"
  local pid_file="$PID_DIR/$name.pid"

  if [[ -f "$pid_file" ]] && kill -0 "$(<"$pid_file")" 2>/dev/null; then
    echo "${name} 已在运行，跳过（端口 ${port}）。"
    return
  fi
  if lsof -nP -iTCP:"$port" -sTCP:LISTEN >/dev/null 2>&1; then
    echo "端口 ${port} 已被其他进程占用，未启动 ${name}。" >&2
    exit 1
  fi

  nohup mvn -pl "$name" spring-boot:run \
    -Dspring-boot.run.profiles="$profile" \
    -Dspring-boot.run.jvmArguments="-Dapboa.root-dir=.apboa-exam" \
    > "$LOG_DIR/$name.log" 2>&1 &
  echo $! > "$pid_file"
  echo "已启动 ${name}（端口 ${port}），日志：${LOG_DIR}/${name}.log"
}

start_backend runner-console dev,exam-local 3160
start_backend runner-runtime dev,exam-local 3161
start_backend runner-proxy exam-local 3162
start_backend runner-file dev,exam-local 3163
start_backend runner-websocket dev-simple,exam-local 3164

if [[ ! -f "$PID_DIR/ui.pid" ]] || ! kill -0 "$(<"$PID_DIR/ui.pid")" 2>/dev/null; then
  if lsof -nP -iTCP:4100 -sTCP:LISTEN >/dev/null 2>&1; then
    echo "端口 4100 已被其他进程占用，未启动考试版 UI。" >&2
    exit 1
  fi
  (cd "$ROOT_DIR/ui" && nohup node node_modules/vite/bin/vite.js --mode exam-local > "$LOG_DIR/ui.log" 2>&1 & echo $! > "$PID_DIR/ui.pid")
  echo "已启动考试版 UI（端口 4100），日志：$LOG_DIR/ui.log"
fi

echo "考试版访问地址：http://localhost:4100/web/"
