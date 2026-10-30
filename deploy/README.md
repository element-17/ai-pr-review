# 部署说明

详细安全清单见 [../docs/部署与安全检查.md](../docs/部署与安全检查.md)。

> 范围：本目录负责后端、MySQL、Redis 与 Nginx 反向代理。正式前端由前端负责人构建部署，本仓库只保留占位页，不修改 `frontend/`。

## 快速启动

```bash
cd deploy
cp .env.example .env
# 编辑 .env
docker compose up -d --build
```

只构建、启动后端时：

```bash
docker compose build backend
docker compose up -d mysql redis backend
docker compose logs -f backend
```

后端以 `prod` profile 启动，MySQL/Redis/模型/GitHub 配置全部由 Compose 环境变量注入；JDBC driver 在 `application.yml` 中带有默认值。变量与 Spring 配置的对应关系见 [部署与安全检查](../docs/部署与安全检查.md)。

首次启动会自动创建 MySQL 表（当前 9 张：含 `user_github_binding`、`ai_model_config` 及新增字段）；如果已有 `mysql-data` 卷，请按日期顺序手动执行迁移脚本：

1. `sql/migration-20261022-retry-of-task.sql`
2. `sql/migration-20261023-rename-sys-user-to-user.sql`

已执行过的迁移不要重复执行。2026-10-30 的 GitHub 登录/模型配置增量按项目约定不新增 migration 脚本：全新库直接使用最新 `sql/schema.sql`；已有库需要手工同步新增表/字段（本地测试库已完成），生产上线前再按目标库情况评估 DDL。

## 前端

正式前端位于 `frontend/`，由前端负责人构建部署。构建后将 `frontend/dist` 内容复制到 `deploy/nginx/html/`，或把 Compose 中的静态目录挂载改成实际产物目录。现有 Nginx 已配置 `/api/` 反向代理和页面路由回退；仓库中的占位页由前端构建产物替换。

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
