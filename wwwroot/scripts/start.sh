#!/usr/bin/env bash
set -euo pipefail
for s in apboa-console apboa-runtime apboa-websocket apboa-proxy apboa-file; do
  systemctl start "$s.service"
  echo "started: $s"
done
echo "全部服务已启动。查看状态：systemctl status apboa-*"
