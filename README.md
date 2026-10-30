# AI PR Review 助手平台

面向开发者的 GitHub Pull Request 智能评审平台：输入公开 PR，异步完成分层总结、重点文件识别、候选问题提取、证据/行号校验，并输出可追溯的报告、人工反馈和 Markdown 导出。

> 当前状态：后端与 React 正式前端已集成，2026-10-23 真实 GitHub + Spring Boot + MySQL/Redis 联调 8/8 通过（模型使用 fake provider）；真实模型质量及 Docker/Nginx 目标机实测仍待验收；X01–X08 加分项未实现。
> 当前实现基线：`backend-dev` 最新提交（2026-10-24，含正式 React 前端与 F04/F16 修复）。

## 目录

```text
backend/        Spring Boot 后端（Java 17、MyBatis-Plus、MySQL、Redis、Sa-Token）
deploy/         Docker Compose、Nginx、SQL 初始化/迁移脚本
docs/           需求、设计、接口、测试、部署、答辩文档（入口见 docs/README.md）
frontend/       React 19 + TypeScript + Vite 正式前端、mock 回归与真实后端联调测试
```

## 技术栈

- 后端：Spring Boot 4.0.8、Java 17、Maven Wrapper、MyBatis-Plus、MySQL 8.x/9.x、Redis 7.x
- 前端：React 19、TypeScript、Vite、Playwright，中英文与主题切换
- 认证：Sa-Token opaque access-token + Redis refresh-token、BCrypt
- AI：OpenAI 兼容接口，两阶段 agent、只读工具循环、结构化校验
- 部署：Dockerfile、Docker Compose、Nginx、环境变量示例

## 快速开始

```bash
# 1. 启动 MySQL 和 Redis，创建数据库并执行 deploy/sql/schema.sql
# 2. 复制 backend/src/main/resources/application-dev.yml.example 为 application-dev.yml
# 3. 填写本地数据库、Redis、模型 Key（该文件已被 gitignore 排除）
cd backend
./mvnw -q -DskipTests package
java -jar target/backend-0.0.1-SNAPSHOT.jar --server.port=8080
```

接口前缀 `/api`；Swagger 在 `/swagger-ui.html`。详细后端配置见 [backend/README.md](backend/README.md)。没有模型 Key 时，在本地配置中使用 `pr.ai.provider: fake` 验证业务链路。

另开终端启动正式前端：

```bash
cd frontend
npm ci
npm run dev
# http://localhost:5173，/api 默认代理至后端 8080
```

真实后端种子账号为 `demo / demo123`（与 mock 演示密码不同）。执行 `npm run test:integration` 可复现 8 项真实接口浏览器联调，环境要求和测试边界见 [前端 README](frontend/README.md) 及 [前端回归与验收](docs/前端回归与验收.md)。

## 文档入口

- [docs/README.md](docs/README.md)：全部文档索引、阅读路径和当前状态
- [接口文档](docs/接口文档.md)
- [非平凡需求设计说明](docs/非平凡需求设计说明.md)
- [交互流程](docs/交互流程.md)
- [数据与状态变更](docs/数据与状态变更.md)
- [测试用例与指标](docs/测试用例与指标.md)
- [全量业务回归与状态验证](docs/全量业务回归与状态验证.md)
- [部署与安全检查](docs/部署与安全检查.md)
- [答辩材料（后端）](docs/答辩/README.md)

## 安全

- 真实数据库密码、GitHub Token、模型 Key 只放在本地 `application-dev.yml`、环境变量或 `deploy/.env`，不得提交。
- 日志统一脱敏；不记录完整 prompt、完整代码或访问令牌。
- 生产环境必须修改 schema 中的默认 admin/demo 密码，并由外部负载均衡或 Nginx 终止 TLS。
