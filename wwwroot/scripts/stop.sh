#!/usr/bin/env bash
set -euo pipefail
for s in apboa-file apboa-proxy apboa-websocket apboa-runtime apboa-console; do
  systemctl stop "$s.service" 2>/dev/null || true
  echo "stopped: $s"
done
echo "全部服务已停止。"
