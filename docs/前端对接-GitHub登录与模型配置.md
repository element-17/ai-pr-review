# 前端对接：GitHub 登录、用户 Token 与模型配置

> 对应后端基线：`backend-dev` 上的 GitHub 登录/绑定、用户级 GitHub Token、管理员模型配置、用户偏好模型增量。
> 本文只描述后端契约；不要求前端修改本仓库 `frontend/`。所有示例中的域名、Client ID、Key、Token 均为占位符。

## 1. 能力概览

| 能力 | 前端入口 | 后端行为 |
| --- | --- | --- |
| GitHub 登录 | 登录/注册页“使用 GitHub 登录” | 后端走 OAuth 授权码流程，创建/匹配本地用户，签发原有 Sa-Token 双 token |
| 账密用户绑定 GitHub | 设置/账号页“绑定 GitHub” | 当前登录用户与 GitHub 数字 id 一对一绑定，一个 GitHub 账号只能绑定一个本地账号 |
| 用户级 GitHub Token | 后端自动使用，前端不感知 | 已绑定用户拉 PR 时使用自己的 token；未绑定使用公用 token；都没有则无 token |
| 管理员模型配置 | 管理端“模型配置”页 | 新增/编辑/启停/设默认/测试 OpenAI Chat 或 Anthropic Messages；API Key 只写不读 |
| 用户偏好模型 | 设置页“模型偏好” | 只能从管理员 enabled 的模型中选择；停用后自动回退默认模型 |
| 任务实际模型 | 任务详情/报告 | 返回 `modelName`、`modelDisplayName`、`modelProvider` 和 `analysisConfig.model` 快照 |

认证仍然是 Sa-Token access-token + Redis refresh-token；GitHub 登录成功后返回的 `LoginVO` 与账密登录完全一致，前端沿用现有 session 逻辑即可。

## 2. 前端必须新增的两个路由

| 路由 | 用途 |
| --- | --- |
| `/auth/github/callback` | 接收后端回调产生的 `ticket`，调用兑换接口，保存 `Session`，跳回 `returnTo` 或首页 |
| 设置页/账号页的绑定结果处理 | 后端绑定成功后跳转 `frontendBindResultUrl`，页面读取 `githubBind` / `githubBindError` 展示结果 |

`frontendLoginCallbackUrl` 和 `frontendBindResultUrl` 由后端配置决定，默认本地为：

```text
http://localhost:5173/auth/github/callback
http://localhost:5173/settings
```

前端不要自行拼接 GitHub 授权地址，也不要把 GitHub access token 放进 URL、localStorage 或日志。

## 3. GitHub 登录

### 3.1 获取授权地址

```http
GET /api/auth/github/login/authorize
```

公开接口，成功返回：

```json
{
  "code": 200,
  "message": "操作成功",
  "data": {
    "authorizeUrl": "https://github.com/login/oauth/authorize?client_id=...&state=...",
    "state": "..."
  }
}
```

前端拿到 `authorizeUrl` 后直接 `window.location.href = authorizeUrl`。不要用 `fetch` 跟随这个地址，它是 GitHub 授权页。

### 3.2 回调与 ticket 兑换

GitHub 完成授权后，浏览器会回到后端：

```text
GET /api/auth/github/callback?code=...&state=...
```

后端校验 state、交换 token、读取 GitHub 用户、创建/匹配本地用户，然后 **302** 到：

```text
{frontendLoginCallbackUrl}?ticket=<一次性 ticket>
```

前端回调页读取 `ticket`，调用：

```http
POST /api/auth/github/exchange
Content-Type: application/json

{ "ticket": "<一次性 ticket>" }
```

成功返回与账密登录相同的 `LoginVO`：

```json
{
  "code": 200,
  "message": "操作成功",
  "data": {
    "accessToken": "...",
    "refreshToken": "...",
    "tokenType": "Bearer",
    "expiresIn": 1800,
    "userInfo": {
      "id": 3,
      "username": "gh_octo_demo",
      "nickname": "Octo Demo",
      "avatar": "https://...",
      "role": "USER",
      "passwordSet": false
    }
  }
}
```

前端保存 session 后跳转首页/`returnTo`。ticket 一次性、短有效期；失败时后端会 302 到 `frontendLoginCallbackUrl?githubError=...`，前端按下表展示：

| `githubError` | 含义 |
| --- | --- |
| `STATE_INVALID` | state 过期、重放或被篡改 |
| `CANCELED` | 用户在 GitHub 取消授权 |
| `TOKEN_EXCHANGE_FAILED` | 授权码交换失败 |
| `USER_FETCH_FAILED` | 读取 GitHub 用户失败 |
| `EMAIL_EXISTS` | GitHub 主邮箱已被本地账号使用，需先登录该账号后绑定 |
| `ACCOUNT_CONFLICT` | GitHub 账号已绑定其他本地账号 |
| `ACCOUNT_DISABLED` | 本地账号被禁用 |
| `FAILED` | 其他失败 |

### 3.3 GitHub 登录用户的本地账号规则

- 用户名自动生成：`gh_{github_login}`，特殊字符替换为 `_`，冲突时追加 `_2`、`_3`。
- 密码是随机不可用值，`passwordSet=false`；不能直接用密码登录。
- 邮箱与已有本地账号相同时 **不会自动合并**，返回 `EMAIL_EXISTS`；用户应先用原账号密码登录，再走“绑定 GitHub”。
- 一个 GitHub 数字 id 只能绑定一个本地账号。

## 4. 账密用户绑定 GitHub

### 4.1 发起绑定

```http
GET /api/auth/github/bind/authorize
Authorization: Bearer <accessToken>
```

返回结构同登录授权地址。state 由后端绑定当前登录用户，回调时不会信任前端传入的 userId。

### 4.2 绑定结果

后端回调完成后 302 到：

```text
{frontendBindResultUrl}?githubBind=SUCCESS
{frontendBindResultUrl}?githubBindError=ACCOUNT_CONFLICT
```

| `githubBindError` | 含义 |
| --- | --- |
| `ACCOUNT_CONFLICT` | GitHub 已绑定其他账号，或当前账号已绑定其他 GitHub |
| `STATE_INVALID` | state 过期/重放 |
| `CANCELED` | 用户取消 |
| `TOKEN_EXCHANGE_FAILED` / `USER_FETCH_FAILED` | GitHub 调用失败 |
| `FAILED` | 其他失败 |

### 4.3 读取绑定状态

```http
GET /api/auth/github/binding
Authorization: Bearer <accessToken>
```

```json
{
  "code": 200,
  "data": {
    "bound": true,
    "githubLogin": "octo-demo",
    "avatarUrl": "https://...",
    "email": "octo-demo@example.com",
    "scope": "read:user,user:email",
    "tokenStatus": 1,
    "authorizedAt": "2026-10-30 18:00:00",
    "lastValidatedAt": "2026-10-30 18:10:00",
    "passwordSet": true
  }
}
```

`tokenStatus`：`1` 有效，`2` 失效需重新授权，`3` 已撤销。接口永远不返回 token 或密文。

### 4.4 设置密码与解绑

GitHub 创建且 `passwordSet=false` 的账号必须先设置密码，才能解绑，否则会失去唯一登录方式。

```http
PUT /api/users/me/password
Authorization: Bearer <accessToken>
Content-Type: application/json

{ "newPassword": "new-password", "confirmPassword": "new-password" }
```

已有密码的账号修改密码时必须同时传 `currentPassword`。

解绑：

```http
DELETE /api/auth/github/binding
Authorization: Bearer <accessToken>
```

`passwordSet=false` 时返回业务码 409，`data.passwordRequired=true`，前端应引导先设置密码。

## 5. 用户设置与模型偏好

### 5.1 可选模型

```http
GET /api/models/available
Authorization: Bearer <accessToken>
```

只返回管理员已启用模型的安全字段：

```json
{
  "code": 200,
  "data": [
    { "id": 1, "displayName": "Mock OpenAI", "provider": "OPENAI_CHAT", "isDefault": true },
    { "id": 2, "displayName": "Mock Anthropic", "provider": "ANTHROPIC_MESSAGES", "isDefault": false }
  ]
}
```

普通用户和游客都不能访问管理员模型配置接口；普通用户访问 `/api/admin/model-configs` 返回业务码 403。

### 5.2 读取设置

```http
GET /api/users/me/settings
Authorization: Bearer <accessToken>
```

在原有 theme/language/checkCategories/ignoreDirs/maxFileCount/maxDiffLines 基础上新增：

| 字段 | 说明 |
| --- | --- |
| `preferredModelId` | 用户保存的偏好模型 ID；为空表示使用管理员默认 |
| `preferredModelEnabled` | 偏好模型当前是否仍启用 |
| `effectiveModel` | 当前实际生效模型的安全快照 |

`effectiveModel` 示例：

```json
{
  "configId": 1,
  "displayName": "Mock OpenAI",
  "provider": "OPENAI_CHAT",
  "modelId": "mock-openai-model",
  "source": "ADMIN_DEFAULT"
}
```

`source`：`USER_PREFERENCE` / `ADMIN_DEFAULT` / `ENV_FALLBACK` / `ORIGINAL_TASK`。

### 5.3 修改偏好

```http
PATCH /api/users/me/settings
Content-Type: application/json

{ "preferredModelId": 2 }
```

- 只能传管理员 `enabled=true` 的模型 ID；不存在或停用返回业务码 400。
- `preferredModelId=0` 表示清除偏好，回退管理员默认。
- 管理员停用用户偏好模型后，后端保留 `preferredModelId`，但 `preferredModelEnabled=false`，`effectiveModel.source` 回退为 `ADMIN_DEFAULT` 或 `ENV_FALLBACK`。
- 设置页选择器只展示 `/api/models/available` 返回的模型；不要在前端自行拼接 provider/baseUrl/modelId。

## 6. 管理员模型配置

> 所有接口要求 `ADMIN` 角色；普通用户返回 403。API Key 只写不读，列表/详情只返回 `apiKeyConfigured` 和 `apiKeyHint`（末 4 位）。

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/admin/model-configs?enabled=` | 列表，包含停用模型；不返回 Key |
| GET | `/api/admin/model-configs/{id}` | 详情 |
| POST | `/api/admin/model-configs` | 新增；`apiKey` 必填 |
| PUT | `/api/admin/model-configs/{id}` | 更新；`apiKey` 为空表示保留原 Key |
| PUT | `/api/admin/model-configs/{id}/status?enabled=true|false` | 启用/停用；停用默认模型会自动提升另一个 enabled 模型 |
| PUT | `/api/admin/model-configs/{id}/default` | 设为默认；必须 enabled |
| POST | `/api/admin/model-configs/test` | 测试未保存配置 |
| POST | `/api/admin/model-configs/{id}/test` | 测试已保存配置 |

新增/测试请求体：

```json
{
  "displayName": "Mock Anthropic",
  "provider": "ANTHROPIC_MESSAGES",
  "baseUrl": "https://your-anthropic-compatible-endpoint/v1",
  "modelId": "your-model-id",
  "apiKey": "replace-with-key",
  "maxOutputTokens": 8192,
  "temperature": 0.1,
  "enabled": true,
  "isDefault": false
}
```

字段约定：

- `provider`：`OPENAI_CHAT` 或 `ANTHROPIC_MESSAGES`。
- `baseUrl`：API 根地址，包含版本前缀（如 `/v1`）；不要带 `/chat/completions` 或 `/messages`。
- `apiKey`：只写不读；更新时留空表示不修改。
- 生产环境后端会拒绝 HTTP、localhost、私网/链路本地/云元数据地址和重定向路径；本地联调由后端配置 `pr.ai.allow-private-base-url=true` 显式放开。
- 测试连接返回 `success`、`latencyMs`、`message`，不会返回上游原始响应或 Key。

模型被停用后历史任务仍可查看；任务报告使用创建时的模型快照，不会因为当前配置改名/停用而改变。

## 7. 任务与错误处理

### 7.1 任务模型展示

`GET /api/tasks/{taskId}`、`GET /api/tasks/{taskId}/report` 新增：

```json
{
  "modelName": "mock-openai-model",
  "modelDisplayName": "Mock OpenAI",
  "modelProvider": "OPENAI_CHAT",
  "analysisConfig": {
    "checkCategories": [1, 2, 3, 4, 5, 6],
    "ignoreDirs": ["node_modules", "dist", "target", ".git"],
    "maxFileCount": 200,
    "maxDiffLines": 20000,
    "model": {
      "configId": 1,
      "displayName": "Mock OpenAI",
      "provider": "OPENAI_CHAT",
      "modelId": "mock-openai-model",
      "source": "ADMIN_DEFAULT"
    }
  }
}
```

`modelName` 保持模型 ID；界面优先展示 `modelDisplayName`，并可用 `modelProvider` 显示协议。

### 7.2 GitHub Token 失效

已绑定用户的 token 失效时，后端不会静默回退公用 token：

- 预览/建任务接口返回业务码 403，`data.githubAuthRequired=true`，`data.action="REAUTHORIZE"`。
- 异步任务失败，`errorCode=10`，`errorMessage` 提示重新授权。
- 前端应展示“重新授权 GitHub”入口，不要把它当成平台登录失效（不要清空平台 session/触发 refresh）。

公用 token 配置无效时返回 502，异步任务 `errorCode=11`；未认证限流返回 429。任务错误码新增：

| errorCode | 含义 |
| --- | --- |
| 10 | 用户 GitHub 授权失效，需要重新授权 |
| 11 | 服务端公用 GitHub Token 无效 |

### 7.3 游客与未绑定用户

- 已绑定用户：用户 token → 不使用公用 token。
- 未绑定登录用户/游客：公用 token（若配置）→ 无 token。
- 无 token 时受 GitHub 60 次/小时限制，后端返回明确限流提示。
- 预览缓存只缓存无 token 的公开请求；用户/公用 token 预览不进入共享缓存。

## 8. 建议的前端交互

1. 登录/注册页新增“使用 GitHub 登录”按钮，跳转 `authorizeUrl`。
2. 新增 `/auth/github/callback` 页面：兑换 ticket → 保存 Session → 跳转 `returnTo`。
3. 设置页新增“GitHub 绑定”卡片：读取 `/auth/github/binding`；未绑定显示绑定按钮；已绑定显示 GitHub 用户名/头像/授权时间/Token 状态/解绑按钮。
4. `passwordSet=false` 时先显示“设置密码”表单，设置成功后再允许解绑。
5. 设置页新增“模型偏好”：从 `/models/available` 加载 enabled 模型；显示 `effectiveModel` 和来源；偏好被停用时提示“原偏好已停用，当前使用默认模型”。
6. 管理端新增模型配置页：列表、新增、编辑、启停、设默认、测试连接；Key 输入框留空表示保留。
7. 任务详情/报告展示 `modelDisplayName`；问题/报告/导出不需要前端重算模型。
8. 新增 i18n：GitHub 登录错误、绑定错误、`errorCode=10/11`、模型停用回退、模型测试失败分类。
9. 不把 GitHub token 存入浏览器；OAuth 回调只传一次性 ticket。
10. 正式前端走同源 `/api` 代理即可；后端已处理 CORS 预检，跨域调试页也可用。

## 9. 联调与验收边界

- 本地没有真实 GitHub OAuth App/Anthropic Key 时，后端使用本地 mock 服务完成登录、绑定、OpenAI/Anthropic 请求/响应和工具调用验证；真实 OAuth App、真实 Anthropic Key 需凭据到位后复验。
- 真实 DeepSeek/OpenAI 兼容路径可用 `DEEPSEEK_API_KEY` 验证；Anthropic 真实链路未验证前不要标记为已完成。
- Docker/Nginx 仍需目标机复验；前端页面、主题、双语和设置页视觉由前端同学负责。

## 10. 前端自查清单

- [ ] GitHub 登录成功后保存的是后端 `LoginVO`，不是 GitHub access token。
- [ ] OAuth 回调 URL 中没有 access/refresh/GitHub token，只有一次性 ticket。
- [ ] 绑定回调校验 `githubBind` / `githubBindError`，不展示 GitHub 原始错误。
- [ ] 普通用户看不到管理端模型配置入口；直接请求返回 403。
- [ ] 模型选择器只展示 `/api/models/available`。
- [ ] 偏好模型停用后展示 `effectiveModel` 和回退提示。
- [ ] `errorCode=10` 引导重新授权，不触发平台 session 失效。
- [ ] 任务详情/报告展示模型显示名和 provider。
- [ ] 解绑前检查 `passwordSet`。
- [ ] 不在前端日志、URL、localStorage 中记录 GitHub token 或模型 API Key。
