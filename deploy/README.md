# 部署说明

详细安全清单见 [../docs/部署与安全检查.md](../docs/部署与安全检查.md)。

## 快速启动

```bash
cd deploy
cp .env.example .env
# 编辑 .env
docker compose up -d --build
```

首次启动会自动创建 MySQL 表；如果已有 `mysql-data` 卷，请手动执行迁移脚本。

## 前端

正式前端位于 `frontend/`（React + TypeScript + Vite）。在该目录执行 `npm ci`、`npm run build`，再把 `frontend/dist` 内容复制到 `deploy/nginx/html/`，或者把 Compose 中的静态目录挂载改成实际产物目录。现有 Nginx 已配置 `/api/` 反向代理和页面路由回退；仓库中的占位页需由构建产物替换。当前完成本地真实后端联调，Docker/Nginx 目标机部署仍须复验。

## 注意

- 不要把 `deploy/.env`、`application-dev.yml` 或真实 Key 提交到仓库。
- 生产环境修改 `schema.sql` 的默认 admin/demo 密码。
- MySQL/Redis 不映射宿主机端口；只通过 Compose 内部网络访问。
- TLS 在外部负载均衡或 Nginx 终止；当前配置只监听 80。
- 日志在 `backend-logs` 卷，错误日志单独写入 `ai-pr-review-error.log`。
