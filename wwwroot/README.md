# Apboa 生产环境部署说明（非 Docker / 原生 Linux）

本目录是一套**纯原生部署**包：后端 5 个 Spring Boot runner 用 systemd 守护，
前端静态文件用 Nginx 托管并反向代理后端 API。

> 关键决策：生产环境**不使用 `application-dev.yml`**，而是直接复用已生成的
> `application-docker.yml`——它本身就是一个「全环境变量驱动」的生产配置，
> 通过 `SPRING_PROFILES_ACTIVE=docker` 激活。所有连接地址与密钥都从
> `apboa.env` 注入，不写死在代码/配置里。

---

## 一、目录结构

```
wwwroot/                         # 上传到服务器的 /wwwroot
├── apboa.env.example           # 环境变量模板（入库）
├── .gitignore                  # 忽略 jars/ dist-main/ apboa.env
├── jars/                       # 由 build.sh 生成：5 个 runner-*.jar
├── dist-main/                  # 由 build.sh 生成：前端构建产物（base=/web/）
├── services/                   # 5 个 systemd 单元文件
│   ├── apboa-console.service      :3060 管理控制台
│   ├── apboa-runtime.service      :3061 AI 执行引擎
│   ├── apboa-websocket.service    :3064 WebSocket 推送
│   ├── apboa-proxy.service        :3062 命令执行代理
│   └── apboa-file.service         :3063 技能文件同步
├── nginx/
│   └── apboa.conf             # Nginx 站点配置（静态 + 反代）
└── scripts/
    ├── build.sh               # 构建机执行：产出 jars/ 与 dist-main/
    ├── install.sh             # 服务器 root 执行：注册服务 + 安装 nginx
    ├── start.sh               # 启动全部服务
    └── stop.sh               # 停止全部服务
```

---

## 二、服务器前置依赖

| 组件 | 版本 | 用途 |
|------|------|------|
| JDK | 21 | 运行 5 个 runner |
| MySQL | 8.x | 主业务库 `apboa_next` |
| Redis | 7.x | 缓存 / 会话 / token |
| PostgreSQL | 16 + pgvector | 向量库 `apboa_vector` |
| Nginx | 任意新版 | 静态托管 + 反代 |
| Maven / Node20.19+ / pnpm | — | **仅构建机**需要 |

> 前端构建后的 `dist-main/` 不依赖 Node 运行，生产服务器只需 Nginx。

---

## 三、步骤

### 1. 准备中间件（在服务器上）

```bash
# MySQL：建库并导入初始化 SQL
mysql -uroot -p -e "CREATE DATABASE apboa_next DEFAULT CHARSET utf8mb4;"
mysql -uroot -p apboa_next < /path/to/sql/once_db_init/db_init.sql
mysql -uroot -p -e "CREATE USER 'apboa'@'127.0.0.1' IDENTIFIED BY '强密码';"
mysql -uroot -p -e "GRANT ALL ON apboa_next.* TO 'apboa'@'127.0.0.1';"

# PostgreSQL：建库 + 安装向量扩展（runtime 自动建表）
sudo -u postgres psql -c "CREATE DATABASE apboa_vector;"
sudo -u postgres psql -d apboa_vector -c "CREATE EXTENSION IF NOT EXISTS vector;"
sudo -u postgres psql -c "CREATE USER apboa WITH PASSWORD '强密码';"
sudo -u postgres psql -c "GRANT ALL ON DATABASE apboa_vector TO apboa;"

# Redis：设置密码（/etc/redis/redis.conf 中 requirepass）
```

> 默认管理员账号：`admin`（密码见 `sql/once_db_init/db_init.sql` 中 `account` 表插入记录，
> 登录后请立即修改）。

### 2. 构建产物（在构建机上）

```bash
cd wwwroot
bash scripts/build.sh
# 产出 wwwroot/jars/*.jar 与 wwwroot/dist-main/
```

### 3. 上传到服务器

```bash
rsync -avz wwwroot/ user@服务器IP:/wwwroot/
# 或 scp -r wwwroot user@服务器IP:/wwwroot
```

### 4. 配置环境变量（在服务器上，**最重要**）

```bash
cd /wwwroot
cp apboa.env.example apboa.env
vim apboa.env          # 把全部 CHANGE_ME_xxx 改成真实强口令！
```

必须修改的项：`REDIS_PASSWORD`、`MYSQL_PASSWORD`、`PG_PASSWORD`、`JWT_SECRET`、`ASSESSMENT_ARK_API_KEY`
（**console / runtime / websocket 三处的 JWT_SECRET 必须完全一致**）。
同机部署时 `REDIS_HOST / MYSQL_HOST / PG_HOST / CONSOLE_HOST / WEBSOCKET_HOST / PROXY_HOST`
保持 `127.0.0.1` 即可。

考试版的 AI 初评通过 `ASSESSMENT_ARK_API_KEY` 注入评审模型凭据；该值只放在服务器的
`apboa.env` 中，不要写入源码、JAR 或 Nginx 配置。

### 5. 安装并启动（服务器 root 执行）

```bash
cd /wwwroot
sudo bash scripts/install.sh     # 建用户、注册 systemd、装 nginx 配置
sudo bash scripts/start.sh       # 启动 5 个服务
```

### 6. 验证

```bash
systemctl status apboa-*         # 5 个服务均 active(running)
curl -s http://127.0.0.1:3060/api/health || true   # 控制台可达
```

浏览器访问 `http://服务器IP/web/` 即可看到登录页。

---

## 四、常用运维命令

```bash
sudo bash /wwwroot/scripts/start.sh   # 启动全部
sudo bash /wwwroot/scripts/stop.sh    # 停止全部
journalctl -u apboa-runtime -f       # 看某个服务日志
sudo nginx -t && sudo systemctl reload nginx   # 改完 nginx 后生效
```

升级版本：在构建机重新 `build.sh` → 上传 `jars/` 与 `dist-main/` →
`sudo bash /wwwroot/scripts/restart.sh`（或 stop 后 start）。

---

## 五、注意事项 / 已知坑

1. **WebSocket 与 HTTPS**：前端 `ui/src/websocket/const/websocket.ts` 当前硬编码 `ws://`。
   若用 HTTPS 访问，浏览器会因 mixed content 拦截 WebSocket。两种处理：
   - 保持 **HTTP** 访问（本包默认）；或
   - 把该文件改为 `wss://` 后重新 `pnpm build:main` 再部署。

2. **不要用 `application-dev.yml`**：它写死了 `127.0.0.1` 的弱口令与 localhost 中间件，
   仅供本地开发。生产一律走 `docker` profile + `apboa.env`。

3. **防火墙**：生产只需对外开放 **80/443**（Nginx），3060~3064 仅监听本机，
   不要对公网暴露。

4. **pgvector 扩展**：必须先在 `apboa_vector` 库执行
   `CREATE EXTENSION IF NOT EXISTS vector;`，否则 runtime 启动/建表失败。

5. **JWT_SECRET 一致性**：三处 runner 必须相同，否则登录 token 校验不通过。
