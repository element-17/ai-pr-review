# 部署说明

详细安全清单见 [../docs/部署与安全检查.md](../docs/部署与安全检查.md)。

仓库中的 `nginx/html/` 是按 `/ai-pr-review/` 子路径构建的前端产物，
`artifacts/backend-0.0.1-SNAPSHOT.jar` 是对应的后端产物。使用已有产物部署时，
先将 JAR 复制到 `../backend/target/backend-0.0.1-SNAPSHOT.jar`，再根据环境填写
`.env` 并启动 Compose。更新代码后请重新执行下面的构建步骤，并同步更新这两处产物。
后端 JAR 打包规则会排除本地的 `application-dev.yml` 和 `application-local.yml`；
仍需避免将真实凭据写入其他资源文件或前端环境变量。

## 生产子路径部署

```bash
cd deploy
cp .env.example .env
# 填写密钥；不要提交 .env
cd ../backend
./mvnw -q -DskipTests package
cp target/backend-0.0.1-SNAPSHOT.jar ../deploy/artifacts/backend-0.0.1-SNAPSHOT.jar
cd ../frontend
VITE_BASE_PATH=/ai-pr-review/ VITE_API_BASE_URL=/ai-pr-review/api npm ci
VITE_BASE_PATH=/ai-pr-review/ VITE_API_BASE_URL=/ai-pr-review/api npm run build
mkdir -p ../deploy/nginx/html/assets ../deploy/nginx/html/images
cp -R dist/assets/. ../deploy/nginx/html/assets/
cp -R dist/images/. ../deploy/nginx/html/images/
cp dist/favicon.svg ../deploy/nginx/html/
cp dist/index.html ../deploy/nginx/html/index.html
cd ../deploy
docker compose up -d --build
```

只构建、启动后端时：

```bash
docker compose build backend
docker compose up -d mysql redis backend
docker compose logs -f backend
```

Compose 使用 `backend/Dockerfile.runtime` 将本地重新打包的 JAR 构建成运行时镜像，避免在目标机重复下载 Maven 依赖。后端以 `prod` profile 启动，MySQL/Redis/模型/GitHub 配置全部由 Compose 环境变量注入；JDBC driver 在 `application.yml` 中带有默认值。API Key 只在容器运行时注入，不包含在 JAR 中。变量与 Spring 配置的对应关系见 [部署与安全检查](../docs/部署与安全检查.md)。

应用 Nginx 只绑定服务器本机 `127.0.0.1:8081`，以免占用现有站点的 80/443。将 `nginx/server-location.conf` 中的 location 块加入现有 HTTPS `server`，再执行 `nginx -t && systemctl reload nginx`。该路径只代理 `/ai-pr-review/`，不替换站点根路径。

后端根据 `PUBLIC_BASE_URL` 的来源允许浏览器请求，地址必须使用浏览器访问的正式 HTTPS 域名。外层代理必须覆盖 `X-Forwarded-Proto`，应用 Nginx 会继续透传该协议。部署前端时需复制完整的 `dist` 目录，包括 `assets` 和 `images`；更新时先上传资源，最后替换 `index.html`，并保留旧的带哈希文件以兼容仍打开的页面。

可选的 GitHub 专用 HTTP CONNECT 代理见 `docker-compose.github-proxy.yml.example`：仅 GitHub API 与 OAuth 客户端使用该代理，其他外部服务不受影响。订阅链接、节点凭据与配置文件必须留在服务器，代理不应对公网发布端口；在代理配置中限制目标为 GitHub，并启用节点健康检查。运营商节点不可用或订阅过期时，GitHub 登录和访问仍会失败。

首次启动会自动创建 MySQL 表（当前 9 张：含 `user_github_binding`、`ai_model_config` 及新增字段）；如果已有 `mysql-data` 卷，请按日期顺序手动执行迁移脚本：

1. `sql/migration-20261022-retry-of-task.sql`
2. `sql/migration-20261023-rename-sys-user-to-user.sql`

已执行过的迁移不要重复执行。2026-10-30 的 GitHub 登录/模型配置增量按项目约定不新增 migration 脚本：全新库直接使用最新 `sql/schema.sql`；已有库需要手工同步新增表/字段（本地测试库已完成），生产上线前再按目标库情况评估 DDL。

## 前后端配置

正式前端位于 `frontend/`。生产构建需设置 `VITE_BASE_PATH=/ai-pr-review/` 和 `VITE_API_BASE_URL=/ai-pr-review/api`，并将 `dist` 内容复制到 `deploy/nginx/html/`。API Key 只通过 `deploy/.env` 在容器运行时注入，不要写进前端、JAR 或镜像。`PR_SECURITY_ENCRYPTION_KEY` 应使用 `openssl rand -base64 32` 生成。GitHub OAuth 凭据缺失时，GitHub 登录/绑定不可用；配置 OAuth App 时，其回调地址必须与 `.env` 中的 `PR_GITHUB_OAUTH_CALLBACK_URL` 完全一致。

## 注意

- 不要把 `deploy/.env`、`application-dev.yml` 或真实 Key 提交到仓库。
- 生产环境修改 `schema.sql` 的默认 admin/demo 密码。
- MySQL/Redis 不映射宿主机端口；只通过 Compose 内部网络访问。
- TLS 在外部负载均衡或 Nginx 终止；当前配置只监听 80。
- 日志在 `backend-logs` 卷，错误日志单独写入 `ai-pr-review-error.log`。

## 验证状态

2026-10-30 已在本地完成：

- `.\mvnw.cmd test`：16 个用例，0 失败，0 错误，1 跳过；
- `.\mvnw.cmd -q -DskipTests package`：构建通过；
- 使用与 Compose 等价的 `prod` 环境变量启动 jar，登录、`/api/auth/me`、`/v3/api-docs`、退出登录均返回成功；
- `docker-compose.yml` 语法和后端环境变量覆盖检查通过。

开发机未安装 Docker，`docker compose build/up` 和 `nginx -t` 仍需在目标机复验。
