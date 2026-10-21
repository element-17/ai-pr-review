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
controller  -> 接收请求、参数校验、返回 Result
service     -> 业务逻辑、事务、token 流程
service.impl
mapper      -> BaseMapper 单表 CRUD；XML 复杂查询
entity      -> 数据库实体，继承 BaseEntity
dto / vo    -> 入参 / 出参，避免暴露密码等敏感字段
config      -> MyBatis-Plus、Sa-Token、Swagger、CORS
common      -> Result、PageResult、异常、上下文、常量
```
