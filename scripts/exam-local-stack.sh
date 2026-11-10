#!/bin/bash
# 考试版（exam-local）本地服务一键管理：启动 / 守护 / 停止 / 状态
#
# 用法：
#   ./scripts/exam-local-stack.sh start    # 补齐启动所有未运行的服务（幂等）
#   ./scripts/exam-local-stack.sh watch    # 守护模式：每 15 秒检查，掉了自动拉起
#   ./scripts/exam-local-stack.sh stop     # 停止全部服务
#   ./scripts/exam-local-stack.sh status   # 查看服务状态
#
# 受环境限制，后台进程一段时间后会退出；watch 模式可自动补拉。
# 进程用 nohup 脱离终端，日志在 logs/exam-local/ 下。

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
JAVA="/opt/homebrew/Cellar/openjdk@21/21.0.12/libexec/openjdk.jdk/Contents/Home/bin/java"
LOG_DIR="$ROOT/logs/exam-local"

if [ ! -x "$JAVA" ]; then
  echo "未找到 JDK 21：$JAVA" >&2
  exit 1
fi

mkdir -p "$LOG_DIR"

CONSOLE_PORT=3160
RUNTIME_PORT=3161
WEBSOCKET_PORT=3164
UI_PORT=4100

is_running() {
  lsof -nP -iTCP:"$1" -sTCP:LISTEN >/dev/null 2>&1
}

pid_of() {
  lsof -nP -iTCP:"$1" -sTCP:LISTEN -t 2>/dev/null | head -1
}

start_console() {
  nohup "$JAVA" -jar "$ROOT/runner-console/target/runner-console-1.0-SNAPSHOT.jar" \
    --spring.profiles.active=exam-local --server.port="$CONSOLE_PORT" \
    --jwt.secret="${JWT_SECRET:?请先 export JWT_SECRET 再启动考试版服务}" \
    >>"$LOG_DIR/console.log" 2>&1 &
  disown 2>/dev/null || true
}

start_runtime() {
  nohup "$JAVA" -jar "$ROOT/runner-runtime/target/runner-runtime-1.0-SNAPSHOT.jar" \
    --spring.profiles.active=exam-local --server.port="$RUNTIME_PORT" \
    --spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver \
    --jwt.secret="${JWT_SECRET:?请先 export JWT_SECRET 再启动考试版服务}" \
    >>"$LOG_DIR/runtime.log" 2>&1 &
  disown 2>/dev/null || true
}

start_websocket() {
  nohup "$JAVA" -jar "$ROOT/runner-websocket/target/runner-websocket-1.0-SNAPSHOT.jar" \
    --spring.profiles.active=exam-local --server.port="$WEBSOCKET_PORT" \
    --jwt.secret="${JWT_SECRET:?请先 export JWT_SECRET 再启动考试版服务}" \
    >>"$LOG_DIR/websocket.log" 2>&1 &
  disown 2>/dev/null || true
}

start_ui() {
  (cd "$ROOT/ui" && nohup node node_modules/vite/bin/vite.js --mode exam-local \
    >>"$LOG_DIR/ui.log" 2>&1 &)
}

ensure_one() {
  local name="$1"
  local port="$2"
  local starter="$3"
  if is_running "$port"; then
    echo "[跳过] $name 已在运行（端口 $port，pid $(pid_of "$port")）"
  else
    echo "[启动] $name -> 端口 $port"
    "$starter"
  fi
}

start_all() {
  ensure_one console "$CONSOLE_PORT" start_console
  ensure_one runtime "$RUNTIME_PORT" start_runtime
  ensure_one websocket "$WEBSOCKET_PORT" start_websocket
  ensure_one ui "$UI_PORT" start_ui
}

stop_all() {
  local port
  local pid
  for port in "$CONSOLE_PORT" "$RUNTIME_PORT" "$WEBSOCKET_PORT" "$UI_PORT"; do
    pid="$(pid_of "$port")"
    if [ -n "$pid" ]; then
      echo "[停止] 端口 $port（pid $pid）"
      kill "$pid" 2>/dev/null
    fi
  done
}

show_status() {
  local port
  local pid
  local name
  for port in "$CONSOLE_PORT" "$RUNTIME_PORT" "$WEBSOCKET_PORT" "$UI_PORT"; do
    pid="$(pid_of "$port")"
    case "$port" in
      "$CONSOLE_PORT") name=console ;;
      "$RUNTIME_PORT") name=runtime ;;
      "$WEBSOCKET_PORT") name=websocket ;;
      *) name=ui ;;
    esac
    if [ -n "$pid" ]; then
      echo "[运行中] $name  端口 $port  pid $pid"
    else
      echo "[已停止] $name  端口 $port"
    fi
  done
}

watch_mode() {
  echo "守护模式已启动，每 15 秒检查一次"
  while true; do
    sleep 15
    if ! is_running "$CONSOLE_PORT"; then
      echo "$(date '+%H:%M:%S') [补拉] console 已退出，重新启动"
      start_console
    fi
    if ! is_running "$RUNTIME_PORT"; then
      echo "$(date '+%H:%M:%S') [补拉] runtime 已退出，重新启动"
      start_runtime
    fi
    if ! is_running "$WEBSOCKET_PORT"; then
      echo "$(date '+%H:%M:%S') [补拉] websocket 已退出，重新启动"
      start_websocket
    fi
    if ! is_running "$UI_PORT"; then
      echo "$(date '+%H:%M:%S') [补拉] ui 已退出，重新启动"
      start_ui
    fi
  done
}

case "${1:-start}" in
  start)  start_all ;;
  stop)   stop_all ;;
  status) show_status ;;
  watch)  start_all; watch_mode ;;
  *)      echo "用法：$0 {start|watch|stop|status}" >&2; exit 1 ;;
esac
