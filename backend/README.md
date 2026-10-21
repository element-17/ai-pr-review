# AI PR Review 后端

## 技术栈

- Java 17
- Spring Boot 4.0.8
- MyBatis-Plus 3.5.17（单表 CRUD、分页、逻辑删除、自动填充）
- MyBatis 原生 XML（复杂查询、聚合统计）
- MySQL 8/9
- Redis（Sa-Token 会话 + refresh-token）
- Sa-Token 1.46.0（access-token + 权限注解）
- springdoc-openapi 3.1.1（Swagger UI）

## 启动步骤

1. 准备 MySQL 和 Redis，并创建数据库：

   ```bash
   mysql -u root -p < src/main/resources/db/schema.sql
   ```

   脚本会创建 `sys_user` 表，并写入两个本地种子账号：

   - `admin / admin123`（ADMIN）
   - `demo / demo123`（USER）

2. 复制本地配置示例并填写真实连接信息：

   ```bash
   cp src/main/resources/application-dev.yml.example src/main/resources/application-dev.yml
   ```

   `application-dev.yml` 已被 `.gitignore` 忽略，**不要提交真实密码**。

3. 启动：

   ```bash
   ./mvnw spring-boot:run
   ```

4. 打开接口文档：

   - Swagger UI：`http://localhost:8080/swagger-ui.html`
   - OpenAPI JSON：`http://localhost:8080/v3/api-docs`

## 认证接口

| 方法 | 路径 | 说明 | 是否需要登录 |
| --- | --- | --- | --- |
| POST | `/api/auth/login` | 用户名密码登录，返回双 token | 否 |
| POST | `/api/auth/refresh` | 使用 refresh-token 换取新双 token | 否 |
| POST | `/api/auth/register` | 注册普通用户 | 否 |
| POST | `/api/auth/logout` | 退出登录并撤销 refresh-token | 是 |
| GET | `/api/auth/me` | 当前登录用户 | 是 |

登录后请求头携带：

```text
Authorization: Bearer <accessToken>
```

## 用户管理接口（ADMIN）

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/users/page` | MyBatis-Plus 分页 |
| GET | `/api/users/search` | 原生 XML 复杂条件分页 |
| GET | `/api/users/{id}` | 详情 |
| POST | `/api/users` | 新增用户 |
| PUT | `/api/users/{id}` | 更新用户 |
| DELETE | `/api/users/{id}` | 逻辑删除 |
| PUT | `/api/users/{id}/status` | 启用/禁用 |
| GET | `/api/users/stats/roles` | 按角色聚合统计 |

## 业务接口（平凡需求后端）

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/users/me/settings` | 读取个人设置；没有设置行时返回默认值 |
| PATCH | `/api/users/me/settings` | 部分更新主题、语言、检查类别、忽略目录和规模上限 |
| GET | `/api/tasks` | 我的历史任务，支持仓库、PR 编号、状态、日期和排序 |
| GET | `/api/tasks/{taskId}` | 任务详情、PR 信息、统计、重点文件和下钻 ID |
| GET | `/api/tasks/{taskId}/progress` | 轻量进度轮询 |
| GET | `/api/tasks/{taskId}/files` | 文件清单，支持状态、变更类型、重点文件和路径搜索 |
| GET | `/api/tasks/{taskId}/files/{fileId}` | 文件 patch、GitHub 链接和关联问题 |
| GET | `/api/tasks/{taskId}/issues` | 问题组合筛选、排序、关键词搜索和任务级统计 |
| GET | `/api/tasks/{taskId}/issues/{issueId}` | 问题详情、证据和 GitHub 行号链接 |
| PATCH | `/api/tasks/{taskId}/issues/{issueId}` | 更新处理状态、理由或建议，记录最后操作人和时间 |
| POST | `/api/tasks/{taskId}/issues/{issueId}/undo` | 撤销人工处理 |
| GET | `/api/tasks/{taskId}/report` | 报告总结与覆盖统计 |
| GET | `/api/tasks/{taskId}/export` | 导出 Markdown，语言跟随任务固定的报告语言 |

说明：

- 任务子资源支持游客链接：请求头 `X-Guest-Token`，导出场景也支持 `?guestToken=...`；过期链接返回 403。
- 历史列表 `/api/tasks` 只对登录用户开放，游客任务不会出现在他人的历史记录中。
- 分页统一返回 `PageResult{total, records}`；成功码沿用当前骨架的 `200`。
- 为保持字段语义，接口会保留值为 `null` 的字段，例如报告级问题没有 `filePath` 和行号。
- 任务创建、取消、重试（F03）和 GitHub / AI 分析链路尚未实现；F09 的候选问题入库校验已提供 `ReviewIssueService.saveGeneratedIssues` 作为后续分析模块的入口。
- 数据库账号模型已统一为 `sys_user`，旧库迁移脚本：`deploy/sql/migration-20261021-unify-sys-user.sql`。

## 双 token 设计

- **access-token**：Sa-Token 签发，默认 30 分钟，放在 `Authorization: Bearer` 中。
- **refresh-token**：随机 UUID，存 Redis，默认 7 天，只用于 `/api/auth/refresh`。
- 刷新时旧 refresh-token 立即删除（旋转），并签发新的 access-token + refresh-token。
- 退出登录时注销当前 access-token，并删除该用户全部 refresh-token。

Redis key：

| Key | 内容 | TTL |
| --- | --- | --- |
| `auth:refresh:{token}` | userId | 7 天 |
| `auth:refresh:user:{userId}` | refresh-token 集合 | 7 天 |
| `auth:login:fail:{username}` | 连续失败次数 | 15 分钟 |

## 代码分层

```text
controller.auth   -> 公开认证接口
controller.admin  -> 管理员接口
controller.user   -> 用户侧业务接口（设置、任务、文件、问题、报告）
service           -> 业务逻辑、权限校验、事务、token 流程
service.impl
mapper            -> BaseMapper 单表 CRUD；XML 复杂查询
entity            -> 数据库实体
dto / vo          -> 入参 / 出参，避免暴露密码等敏感字段
config            -> MyBatis-Plus、Sa-Token、Swagger、CORS
common            -> Result、PageResult、异常、上下文、常量、GitHub 链接工具
```
