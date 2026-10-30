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

   脚本会创建 `user` 表，并写入两个本地种子账号：

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
| GET | `/api/auth/github/login/authorize` | 获取 GitHub 登录授权地址 | 否 |
| GET | `/api/auth/github/callback` | GitHub OAuth 回调，302 回前端 | 否 |
| POST | `/api/auth/github/exchange` | 用一次性 ticket 兑换双 token | 否 |
| GET | `/api/auth/github/bind/authorize` | 获取当前用户绑定 GitHub 的授权地址 | 是 |
| GET | `/api/auth/github/binding` | 读取 GitHub 绑定状态 | 是 |
| DELETE | `/api/auth/github/binding` | 解绑 GitHub | 是 |
| PUT | `/api/users/me/password` | 设置/修改密码 | 是 |
| POST | `/api/auth/logout` | 退出登录并撤销 refresh-token | 是 |
| GET | `/api/auth/me` | 当前登录用户 | 是 |

登录后请求头携带：

```text
Authorization: Bearer <accessToken>
```

### GitHub 登录与绑定

- OAuth App 配置：`pr.github.oauth.client-id/client-secret/callback-url/frontend-login-callback-url/frontend-bind-result-url`；secret 只放环境变量或本地忽略配置。
- state 与一次性 ticket 存 Redis，state 一次性消费，防止过期/重放；回调只 302 到前端，不把 GitHub token 放进 URL。
- GitHub 数字 id 是唯一绑定键；login/avatar/email 只作展示，一个 GitHub 账号只能绑定一个本地账号。
- GitHub 创建的用户名为 `gh_{login}`，随机不可用密码，`password_set=0`；解绑前必须先设置密码。
- GitHub token 使用 AES-GCM 加密存入 `user_github_binding.access_token_ciphertext`，接口只返回绑定状态、scope、授权时间和 token 状态。
- GitHub 登录用户、账密登录但已绑定用户都会使用自己的 token 拉 PR。

### GitHub Token 选择

```text
已绑定用户 token → 配置文件公用 token → 无 token
```

- 已绑定用户 token 失效（401 或 token_status != 1）：预览/建任务返回 403 + `data.githubAuthRequired=true`；异步任务 `errorCode=10`，不回退公用 token。
- 公用 token 配置但无效：接口 502，任务 `errorCode=11`。
- 公用 token 未配置或为占位符：无 token 请求，受 GitHub 60 次/小时限制；接口 429，任务 `errorCode=2`。
- 预览缓存只缓存无 token 的公开请求；用户/公用 token 请求不走共享缓存。
- 任务创建只保存 `user_id`，worker 按任务归属重新解析 token，不把明文 token 写进 `analysis_task`。

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
| POST | `/api/tasks` | 创建分析任务，允许游客；支持 `Idempotency-Key` |
| GET | `/api/tasks` | 我的历史任务，支持仓库、PR 编号、状态、日期和排序 |
| GET | `/api/tasks/{taskId}` | 任务详情、PR 信息、统计、重点文件和下钻 ID |
| POST | `/api/tasks/{taskId}/cancel` | 取消排队/分析中的任务 |
| POST | `/api/tasks/{taskId}/retry` | 失败、部分完成或取消的任务重试，新建任务 |
| DELETE | `/api/tasks/{taskId}` | 仅登录本人，逻辑删除已结束任务 |
| GET | `/api/tasks/{taskId}/progress` | 轻量进度轮询 |
| GET | `/api/tasks/{taskId}/files` | 文件清单，支持状态、变更类型、重点文件和路径搜索 |
| GET | `/api/tasks/{taskId}/files/{fileId}` | 文件 patch、GitHub 链接和关联问题 |
| GET | `/api/tasks/{taskId}/files/{fileId}/diff` | 结构化 Diff 行、新旧行号和问题标记 |
| GET | `/api/tasks/{taskId}/issues` | 问题组合筛选、排序、关键词搜索和任务级统计 |
| GET | `/api/tasks/{taskId}/issues/{issueId}` | 问题详情、证据和 GitHub 行号链接 |
| PATCH | `/api/tasks/{taskId}/issues/{issueId}` | 更新处理状态、理由或建议，记录最后操作人和时间 |
| POST | `/api/tasks/{taskId}/issues/{issueId}/undo` | 撤销人工处理 |
| GET | `/api/tasks/{taskId}/report` | 报告总结与覆盖统计 |
| GET | `/api/tasks/{taskId}/export` | 导出 Markdown，语言跟随任务固定的报告语言 |
| GET | `/api/tasks/{baseTaskId}/compare/{targetTaskId}` | 同一 PR 两个分析版本的问题与总结对比 |

说明：

- 任务子资源支持游客链接：请求头 `X-Guest-Token`，导出场景也支持 `?guestToken=...`；过期链接返回 403。
- 历史列表 `/api/tasks` 只对登录用户开放，游客任务不会出现在他人的历史记录中。
- 分页统一返回 `PageResult{total, records}`；成功码沿用当前骨架的 `200`。
- 为保持字段语义，接口会保留值为 `null` 的字段，例如报告级问题没有 `filePath` 和行号。
- AI 分析支持管理员配置的 OpenAI Chat Completions 兼容和 Anthropic Messages 兼容模型；没有数据库模型配置时回退 `pr.ai.provider=deepseek/fake` 环境配置（两阶段：PR 总结/重点文件 + 逐批 3–5 个文件提取问题）。`pr.ai.deepseek.base-url`、`model` 可切换其他 OpenAI 兼容服务。
- 无模型 Key 时可设置 `pr.ai.provider=fake`，用确定性 fake client 验证 agent 工具循环和报告链路；`pr.ai.stub-pipeline=true` 只用于状态机测试。
- 游客创建任务每 IP 每小时 5 次；预览每 IP 每分钟 30 次；创建/重试支持 `Idempotency-Key`，TTL 10 分钟。
- 服务重启后，排队任务重新投递，运行中任务标记失败 `errorCode=8`。
- 数据库账号模型已统一为 `user`，旧库迁移脚本：`deploy/sql/migration-20261023-rename-sys-user-to-user.sql`。
- 任务重试字段迁移脚本：`deploy/sql/migration-20261022-retry-of-task.sql`。

## 模型配置与用户偏好

管理员接口（全部要求 `ADMIN`）：

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/admin/model-configs?enabled=` | 模型列表，只返回 Key 掩码 |
| GET | `/api/admin/model-configs/{id}` | 详情 |
| POST | `/api/admin/model-configs` | 新增；apiKey 必填 |
| PUT | `/api/admin/model-configs/{id}` | 更新；apiKey 为空保留原 Key |
| PUT | `/api/admin/model-configs/{id}/status?enabled=` | 启用/停用 |
| PUT | `/api/admin/model-configs/{id}/default` | 设为默认 |
| POST | `/api/admin/model-configs/test` | 测试未保存配置 |
| POST | `/api/admin/model-configs/{id}/test` | 测试已保存配置 |

- provider 支持 `OPENAI_CHAT` 和 `ANTHROPIC_MESSAGES`；两套客户端统一走 `ModelClientRouter`，返回统一 `ModelResponse`。
- API Key 使用 AES-GCM 加密，密钥来自 `PR_SECURITY_ENCRYPTION_KEY`（Base64 32 字节）；接口只返回 `apiKeyConfigured` 和末 4 位掩码。
- base URL 生产默认只允许 HTTPS、公网地址和 443 端口；本地联调可设置 `pr.ai.allow-private-base-url=true`。
- 普通用户通过 `GET /api/models/available` 读取 enabled 模型；`PATCH /api/users/me/settings` 的 `preferredModelId` 只能是 enabled 模型，`0` 表示清除偏好。
- 模型解析优先级：用户偏好 → 管理员默认 → 环境兜底；重试优先沿用原任务模型；停用偏好模型后保留选择但实际回退默认。
- 任务写 `model_config_id` 和 `analysis_config.model` 快照；详情/报告返回 `modelDisplayName/modelProvider`，历史任务不随配置改名/停用改变。

## AI 分析

- Agent 工具：`read_file`、`find_callers`、`read_tests`、`submit_summary`、`submit_issues`；只读、路径白名单、单文件 20KB 上限，不执行 shell 或任意网络请求。
- 上下文：GitHub Contents API；TypeScript 和 Python 轻量函数级提取，失败时回退到变更行附近窗口。
- 批处理：每批 3–5 个文件；每批最多 6 轮模型调用、8 次工具调用；`pr.ai.batch-concurrency` 控制批内并发（默认 2，范围 1–4），单批失败隔离，批次完成推进 50–89 进度，取消会中断剩余批次。
- 未调用提交工具时先提醒一次，仍失败则该批标记失败并保留其他批次结果；批次线程继承任务 traceId。
- 校验：文件必须属于当前任务；类别/严重度/置信度范围；行号必须落在 patch 新增行；证据不足时置信度降到 60 以下并保持待确认；重复问题合并。
- 部分完成：批次失败或模型输出被丢弃时 status=4、errorCode=7；已完成结果保留。
- 版本对比：仅登录本人、同一 PR、两个已完成/部分完成任务；按文件路径（含重命名前后路径）、类别、规范化标题匹配，返回 added/remaining/resolved、`lineShifted` 和总结字段变化；低置信匹配标记 `matchConfidence=low`。
- 配置示例见 `application-dev.yml.example` 的 `pr.ai`；真实 Key 只放本地 `application-dev.yml` 或环境变量，不提交仓库。

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

## 日志与可观测性

- 每个请求生成或透传 `X-Request-Id`，写入 MDC `traceId` 并回写到响应头；异步任务日志使用 `task-{taskId}` 作为 traceId。
- 访问日志只记录 `method/path/status/durationMs/ip/userId`，不记录 query、请求体、请求头或响应体，避免游客令牌和密码进入日志。
- 文件日志：`logs/ai-pr-review.log`（50MB 滚动、保留 30 天、总量 1GB）和 `logs/ai-pr-review-error.log`（仅 ERROR，20MB/30 天/512MB）；`LOG_DIR` 可覆盖目录。
- `dev` profile 同时输出控制台和文件，非 `dev` 只输出文件；SQL DEBUG 只在 `dev` 打开。
- 日志格式化后统一脱敏 `Authorization`、`api-key`、`access/refresh/guest token`、`password/secret`、`Bearer` 及常见 token 前缀；不打印 prompt、完整代码或密钥。
- 任务、模型和 GitHub 客户端记录阶段/调用耗时；模型日志只记录 token 数量、耗时和状态。

## 质量验证

- 答辩材料（后端）见 [../docs/答辩/README.md](../docs/答辩/README.md)。
- 全量 API/状态回归见 [../docs/全量业务回归与状态验证.md](../docs/全量业务回归与状态验证.md)。
- 非平凡需求方案见 [../docs/非平凡需求设计说明.md](../docs/非平凡需求设计说明.md)，交互流程见 [../docs/交互流程.md](../docs/交互流程.md)，数据状态见 [../docs/数据与状态变更.md](../docs/数据与状态变更.md)。
- 20 个测试用例、真实模型指标、误报/漏报分析见 [../docs/测试用例与指标.md](../docs/测试用例与指标.md)。
- 真实模型验证：`deepseek/deepseek-v4.1-flash` 分析 `octocat/Hello-World#11596`，任务 status=3、20.8 秒、3 次调用、4504 tokens；管理员模型配置路径已用本地 mock OpenAI/Anthropic 完成浏览器联调。
- 单元/协议测试：`.\mvnw.cmd test` 当前 26 个用例，0 失败，1 跳过（真实 GitHub live test 默认跳过）。
- 双模型对比与选型见 [../docs/模型对比与选型.md](../docs/模型对比与选型.md)：v4.1 Flash 默认，v4 Pro 作为复杂 PR 可选；对比脚本 `scripts/model-comparison.mjs`。
- 大 Diff、模型超时、启动恢复、非法输出部分完成等异常路径均已实际执行并记录。

## 代码分层

学习 `sky-take-out` 的职责分层，但保持单 Maven 模块；共享能力和数据模型先收口，业务层再按职责展开。

```text
common.constant/context    -> 状态码、错误码、用户上下文
common.crypto              -> AES-GCM 敏感数据加解密
common.exception           -> 全局异常处理器和自定义异常
common.result              -> Result、PageResult
common.util                -> PatchLineIndex、PatchDiffParser、GitHubUrlUtil、BaseUrlValidator、二进制/语言检测
common.logging             -> 日志脱敏、traceId 过滤器
common.interceptor         -> 登录用户上下文拦截器
common.properties          -> AiProperties、GitHubProperties、GitHubOAuthProperties、SecurityProperties、PrAppProperties、TaskProperties

pojo.dto / pojo.entity / pojo.vo -> 入参、数据库实体、出参

core.analysis              -> AI 分析流水线、上下文、校验和内部模型
core.agent                 -> agent 工具循环、工具定义/注册和提交结果
core.agent.ai              -> OpenAI/Anthropic 客户端、ModelClientRouter、运行时配置、fake 客户端
core.github                -> GitHub 客户端、URL 解析、错误映射和 DTO
core.github.oauth          -> GitHub OAuth 客户端、state/ticket 载荷

controller.auth/admin/user -> 认证、管理员和用户侧 HTTP 接口
service                    -> 业务接口（任务、设置、模型、导出、问题反馈等）
service.impl               -> 业务接口实现（XxxServiceImpl）
task                       -> TaskWorker、TaskDispatcher、TaskRecoveryRunner
mapper                     -> BaseMapper 单表 CRUD；XML 复杂查询
config                     -> MyBatis-Plus、Sa-Token、线程池、Swagger、CORS/MVC
```

`core.analysis` 使用 `AnalysisResult`、`AnalysisContext`、`IssueDraft`、`IssueDraftList`、`SummaryDraft`、`IssueValidationResult`；`core.agent` 使用 `AgentContext`、`AgentSubmission` 等明确的 agent 数据名，避免和 service/util 混淆。
