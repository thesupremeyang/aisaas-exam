#!/usr/bin/env bash
# 停止本地考试版（exam-local）服务：console 3160 / runtime 3161 / websocket 3164 / 前端 4100
set -u

# 先杀前端守护循环（它会每 3 秒把 Vite 拉起来，不先杀就停不掉）
sups=$(pgrep -f "vite.js --mode exam-local" 2>/dev/null)
if [ -n "$sups" ]; then
  echo "停止前端守护进程：$sups"
  echo "$sups" | xargs kill 2>/dev/null
fi

for p in 3160 3161 3164 4100; do
  pids=$(lsof -nP -tiTCP:"$p" -sTCP:LISTEN 2>/dev/null)
  if [ -n "$pids" ]; then
    echo "停止端口 $p 上的进程：$pids"
    echo "$pids" | xargs kill 2>/dev/null
  else
    echo "端口 $p 无进程"
  fi
done

sleep 2
echo
echo "=== 剩余监听 ==="
for p in 3160 3161 3164 4100; do
  lsof -nP -iTCP:"$p" -sTCP:LISTEN >/dev/null 2>&1 && echo "  $p 仍在监听" || echo "  $p 已停止"
done
