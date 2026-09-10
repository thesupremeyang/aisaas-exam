#!/usr/bin/env bash
# ============================================================
# 在「服务器」上以 root 执行：注册服务 + 安装 nginx 配置
# 前置：已把 wwwroot 放到 /wwwroot，并已创建 apboa.env
# ============================================================
set -euo pipefail

if [[ $EUID -ne 0 ]]; then
  echo "请用 root 运行：sudo bash install.sh"
  exit 1
fi

WWWROOT="$(cd "$(dirname "$0")/.." && pwd)"
SERVICES_DIR="$WWWROOT/services"

if [[ ! -f "$WWWROOT/apboa.env" ]]; then
  echo "!! 缺少 $WWWROOT/apboa.env，请先：cp apboa.env.example apboa.env 并填写"
  exit 1
fi

# 1) 运行用户
id -u apboa >/dev/null 2>&1 || useradd -r -s /usr/sbin/nologin -d /wwwroot apboa

# 2) 目录权限（jar/前端只读，.apboa 工作目录可写）
chown -R apboa:apboa "$WWWROOT/jars" "$WWWROOT/dist-main" 2>/dev/null || true
chmod -R 755 "$WWWROOT/dist-main"
mkdir -p "$WWWROOT/.apboa"
chown -R apboa:apboa "$WWWROOT/.apboa"
chmod 600 "$WWWROOT/apboa.env"
chown apboa:apboa "$WWWROOT/apboa.env"

# 3) 安装 systemd 服务
echo "==> 安装 systemd 服务 ..."
for s in apboa-console apboa-runtime apboa-websocket apboa-proxy apboa-file; do
  install -m 644 "$SERVICES_DIR/$s.service" /etc/systemd/system/
  systemctl daemon-reload
  systemctl enable "$s.service"
done

# 4) 安装 nginx 配置
if command -v nginx >/dev/null 2>&1; then
  echo "==> 安装 nginx 配置 ..."
  install -m 644 "$WWWROOT/nginx/apboa.conf" /etc/nginx/conf.d/apboa.conf
  nginx -t
  systemctl reload nginx 2>/dev/null || systemctl restart nginx
else
  echo "!! 未检测到 nginx，请先安装，再复制 $WWWROOT/nginx/apboa.conf 到 /etc/nginx/conf.d/"
fi

echo "==> 安装完成。启动全部服务：sudo bash $WWWROOT/scripts/start.sh"
