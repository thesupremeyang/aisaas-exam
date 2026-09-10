#!/usr/bin/env bash
# ============================================================
# 在「构建机」上执行（需 JDK21 + Maven + Node20.19+/22.12+ + pnpm）
# 产物输出到本目录（wwwroot）下的 jars/ 与 dist-main/
# ============================================================
set -euo pipefail

WWWROOT="$(cd "$(dirname "$0")/.." && pwd)"
PROJECT_ROOT="$(cd "$WWWROOT/.." && pwd)"
JARS_DIR="$WWWROOT/jars"
DIST_DIR="$WWWROOT/dist-main"

echo "==> 项目根目录 : $PROJECT_ROOT"
echo "==> 部署输出目录 : $WWWROOT"

# 1) 后端：构建 5 个 runner（连带依赖模块一起打包）
echo "==> [1/2] 构建后端 jar ..."
( cd "$PROJECT_ROOT" && mvn clean package -DskipTests \
    -pl runner-console,runner-runtime,runner-websocket,runner-proxy,runner-file -am )

mkdir -p "$JARS_DIR"
for m in runner-console runner-runtime runner-websocket runner-proxy runner-file; do
  cp -f "$PROJECT_ROOT/$m/target/$m-1.0-SNAPSHOT.jar" "$JARS_DIR/"
done

# 2) 前端：构建主应用（base=/web/，产物 dist-main）
echo "==> [2/2] 构建前端 ..."
( cd "$PROJECT_ROOT/ui" && pnpm install && pnpm build:main )

rm -rf "$DIST_DIR"
cp -r "$PROJECT_ROOT/ui/dist-main" "$DIST_DIR"

echo "==> 构建完成。"
echo "    将整个 $WWWROOT 目录同步到服务器 /wwwroot 后，"
echo "    在服务器上执行：sudo bash $WWWROOT/scripts/install.sh"
