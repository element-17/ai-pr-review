# AI PR Review 前端

## 组件组织

- `src/components/ui/`：图标、链接、提示、分页等基础组件，一个职责一个文件，通过 `index.ts` 统一导出；组件内部直接引用同目录文件。
- `src/components/common/`：加载状态、语言切换等通用组件。
- `src/components/diff/`：Diff 渲染与代码高亮；滚动位置缓存跟随 DiffViewer 模块保存。
- `src/components/issues/`：问题编辑与反馈表单。
- `src/components/settings/`：外观偏好和分析设置表单。
- `src/components/account/`：本人头像与个人资料弹窗，点击时请求当前账号信息。
- `src/components/review/`：游客页与用户首页共用的 PR 预览/分析表单和结果访问表单。
- `src/components/workspace/`：工作台外壳、进度轮询、任务操作及共享 props 类型。
- `src/pages/`：页面数据请求、页面级状态与业务组件组合；总览单独放在 `Overview.tsx`。

公共组件从 `components/ui` 导入，业务组件按模块直接导入。业务子组件保留自己的表单或渲染状态；共享类型放在独立类型文件，组件不反向依赖页面。

样式统一使用 SCSS：组件和页面使用 `*.module.scss`，全局样式位于 `src/index.scss` 与 `src/styles/shared.scss`。Vite 通过开发依赖 `sass` 自动编译，无需额外配置；新增样式也使用 `.scss`。子元素和状态选择器使用嵌套及 `&` 组织，主题颜色保留 CSS 自定义属性以支持运行时切换。

React 19 + TypeScript + Vite，实现 PR 接入、历史任务、评审总览、文件 Diff、问题反馈、版本对比和偏好设置。

## 本地运行

```powershell
npm ci
Copy-Item .env.example .env.local
npm run dev
```

开发服务默认使用 `/api`，由 Vite 代理至 `http://localhost:8080`。可在 `.env.local` 修改 `API_PROXY_TARGET`。生产部署需代理 `/api` 至后端，并将页面路由回退到 `index.html`。跨域部署时可配置 `VITE_API_BASE_URL`，同时在后端配置允许的来源。

### 无后端联调（本地 mock）

`mock/` 下是一个零依赖的 Node 服务，按 Apifox 项目 `8918366` 的接口契约返回假数据，结构对齐 `components/schemas` 里的 VO。

```powershell
npm run mock                 # 启动在 8080，正好是默认的 API_PROXY_TARGET
npm run dev                  # 另开一个终端
```

演示账号 `demo` / `demo123456`。可用任务：`1003` 已完成（对比目标版本）、`1002` 部分完成且已过期、`1001` 已完成（对比基线版本）、`1004` 失败可重试、`1005` 已取消、`1006` 分析中（进度轮询）、`1007` 游客任务（令牌 `guest-demo-token-1007`）。`/prs/preview` 认 `octocat/Hello-World#1347`。新建任务 20 秒左右走完排队到完成，用来验证进度轮询与终态刷新。

- `MOCK_PORT=9090 npm run mock` 换端口，同时改 `.env.local` 的 `API_PROXY_TARGET`。
- `MOCK_DELAY=400 npm run mock` 给响应加延迟，便于观察 loading 态。
- 数据在进程内存里，改了反馈状态等重启即还原。
- 支持游客预览、创建、取消和重试；新建游客任务的演示有效期为 7 天。正式有效期仍由真实后端决定。

接口与 Apifox 文档的对应关系：`mock/server.mjs` 按 `/api` 前缀路由，`mock/fixtures.mjs` 存种子数据；导出 `/api/tasks/{id}/export` 返回 `text/markdown` 原文，`{code,message,data}` 包装与前端 `lib/api.ts` 的解析一致。

```powershell
npm run build
npm run lint
npx playwright install chromium  # 首次执行浏览器测试时安装
npm test
```

## 界面语言

所有界面翻译集中在 `src/i18n/locales/zh-CN/common.json` 和 `src/i18n/locales/en-US/common.json`，使用相同的平铺 key。新增文案时同时更新两份 JSON，装饰英文保留在页面中。

`src/i18n/index.ts` 统一注册语言资源并提供 `translate`；`PreferencesProvider` 管理当前语言，组件可用 `useTranslation()` 获取 `{ t, language }`，然后调用 `t('homeTitle')`。已有页面的 `usePreferences().t` 也使用同一个翻译函数，不维护另一套字典。

`MessageKey` 从中文 JSON 推导，TypeScript 检查英文 key 的缺失或多余，以及调用处 key 的拼写。两份小型语言包随前端一起打包，切换时不需要额外请求；缺失或空文案回退至中文。界面语言统一使用 `zh-CN` / `en-US`，继续沿用本地持久化和登录用户的设置同步。报告正文和导出语言独立于界面切换。

## 接口基准与功能边界

对照 `origin/master` 的接口文档及后端实际 Controller、DTO、VO（2026-10-22，`cca8ccb`），优先采用当前实现：`/api`、成功码 `200`、`{ total, records }` 分页、用户名登录、注册后返回登录页、Sa-Token 双令牌。

- 登录成功后令牌存于当前标签页的 `sessionStorage`；401 时合并并发刷新请求、旋转双令牌，并重放一次请求；刷新失败清除会话并跳转登录，保留原页面与筛选条件。点击退出先弹出确认，取消保留当前会话；确认后请求退出接口，并清除本地会话、跳转登录页。
- 注册按当前 `RegisterDTO` 校验：用户名 3–50 位英文字母、数字或下划线；密码 6–32 位且非空白；昵称最多 50 字符，邮箱可选且最多 100 字符、须格式有效，手机号可选且须为以 13–19 开头的 11 位号码。失焦及提交前校验，错误与字段关联，非法资料不请求接口；提交中阻止重复请求。密码保持原值，可选空资料省略；用户名是否重复仍由后端判断。当前规则与旧邮箱注册示例的区别见 [接口文档 3.1](../docs/接口文档.md#31-注册)。
- 个人资料：登录后点击侧栏头像，手机端点击顶部头像，弹窗通过 `GET /api/auth/me` 重新读取本人资料，展示用户名、昵称、邮箱、手机号、角色、状态与时间。接口未提供的字段显示“未提供”，请求失败可重试；支持关闭按钮、Esc 和遮罩关闭，保留当前页面与筛选条件。
- 游客入口：未登录访问 `/` 显示独立 `GuestHome`，侧栏与顶栏显示“游客首页”，提供公开 PR 体验、游客结果访问、结果链接保存说明及登录/注册入口；登录后 `/` 显示原用户首页。游客结果入口缺少令牌时先提示，可复用当前标签页内未过期的凭证。`/tasks/123?guestToken=...` 子页面保留令牌，所有查询、反馈与导出携带 `X-Guest-Token`。创建、重试和成功打开链接后，当前标签页记住任务对应的令牌与已知有效期，之后可直接打开任务编号；已知过期的凭证不自动复用。仅创建响应或详情提供了有效期时显示具体时间，否则说明期限由服务端决定。关闭标签页后需使用保存的链接。游客导航隐藏历史、设置和对比，主题语言仍可通过顶栏切换。令牌属于访问凭证，请只分享给可信人员。
- 历史：仓库、PR、状态、日期、排序、分页；筛选保留在 URL。只有登录用户请求历史。
- 总览：固定 head SHA、过期提醒、阶段进度、风险分布、覆盖范围、重点文件、完整配置快照、报告与 Markdown 下载。过期报告在前端禁止修改建议和反馈；“重新分析”带入原 PR 和报告语言，重新预览并确认数据发送后创建新任务。运行中每两秒轮询，终态停止并重读详情；离开页面取消请求。
- 文件：文件列表与筛选、统一/并排 Diff、补丁行号、基础词法高亮、风险联动、二进制/重命名/跳过说明；Diff 每批渲染 250 行，定位可自动展开目标行，切换问题详情保留滚动位置。
- 问题：筛选、排序、分页、详情与证据、编辑建议、四种处理状态、误报理由、撤销、下一条待确认、跳转 Diff/GitHub。文件选择按每页 200 条遍历完整文件列表，加载失败可重试。报告级提示保留空定位。
- 版本对比：遍历同一 PR 的历史任务分页，选择已完成/部分完成版本；展示双方问题统计和严重度、总结变化标识、三组问题、行号偏移和低置信匹配，保留服务端匹配说明。日期、数量和百分比跟随界面语言。
- 错误：识别 GitHub 限流、仓库权限、PR 无效、模型超时/格式、Diff 上限、游客链接失效和操作冲突，使用中英文文案；未知服务端错误不直接显示原始堆栈。
- 偏好：顶栏主题按钮直接切换浅色/深色，设置页保留浅色/深色/跟随系统，并可启用按设备当地时间自动切换（07:00–19:00 浅色，其余深色）。手动点击顶栏按钮退出定时模式。时间策略保存在当前浏览器，服务端仍只接收现有主题和语言字段。设置分为外观与语言、检查类别、忽略目录、分析规模、数据说明，右侧分类导航点击滚动定位；手机端使用顶部横向导航。游客本地保存主题语言，登录用户同步支持的设置；报告正文和导出保留原生成语言。

当前 master 已实现 PR 预览、创建/取消/重试、删除任务、版本对比和结构化 Diff（`/files/{fileId}/diff`）接口，对应功能默认开启。Diff 行号与风险标记以后端结构化结果为准，前端不再自行解析 patch。联调缺少这些接口的旧后端时，可显式关闭：

| 环境变量 | 关闭的功能 |
| --- | --- |
| `VITE_ENABLE_ANALYSIS=false` | `/prs/preview`、`POST /tasks`、`/cancel`、`/retry` |
| `VITE_ENABLE_COMPARISON=false` | `/tasks/{base}/compare/{target}` |
| `VITE_ENABLE_TASK_DELETE=false` | `DELETE /tasks/{id}` |

启用前应确认新版接口仍返回成功码 `200`。项目文档早期目标契约中的 `/api/v1`、JWT、成功码 `0`、邮箱登录与 `list` 分页不适用于当前后端。版本选择每页请求 100 条，加载全部历史分页后展示同一 PR 的已完成/部分完成任务。

## 主要交互与验证

首页 → 预览/分析（服务启用后）→ `/tasks/:id` 总览 → 文件或风险 → 问题详情 → 编辑反馈 → 返回定位 Diff → 导出。游客可在首页输入任务编号和令牌直接进入。版本对比入口位于工作台标签栏。

联调前启动后端及其 MySQL/Redis。真实后端种子账号为 `demo / demo123`、`admin / admin123`，与 mock 的演示密码不同；真实模型分析需在服务端配置模型 Key，无 Key 时可用 `pr.ai.provider=fake` 验证链路。

提交前需通过 `npm run build` 与 `npm run lint`。此前记录过按当前接口契约模拟的 24 项浏览器检查，覆盖游客请求头、历史权限、报告下载、Diff 双视图、反馈保存/撤销、主题跟随系统、语言持久化、并发 token 刷新、终态停止轮询和过期游客链接。检查包含 1440px 桌面、768px 平板及 390px 手机宽度；未连接真实 MySQL/Redis 后端验证。

可复现的 Playwright 回归位于 `tests/workflows.spec.mjs`、`tests/profile.spec.mjs`、`tests/settings.spec.mjs`、`tests/registration.spec.mjs` 和 `tests/guest-home.spec.mjs`，运行 `npm test` 会自动启动独立 mock（18080）和 Vite（15173），覆盖游客专用首页与身份切换、过期报告、反馈、完整分页、异常、token 刷新、退出、个人资料、主题定时切换、设置分类导航与注册校验/登录。测试产物放在被忽略的 `test-results/`。

## 真实后端联调

2026-10-23 已将 `origin/backend-dev@c11090a` 合并到 `frontend-dev`，并连接真实 Spring Boot、MySQL、Redis 与公开 GitHub PR 完成 8/8 浏览器联调。后端业务接口与当前前端兼容；同时移除了后端分支遗留的根目录 `frontend/` 忽略规则，保留正式前端及其未提交改动。

先按 `backend/README.md` 启动依赖、初始化数据库和配置后端。在独立开发库中执行测试，会创建 `integration_时间戳` 用户、评审任务及反馈数据，不使用 mock，不拦截浏览器 API：

```powershell
npm run test:integration
# 后端端口不为 8080 时：
$env:INTEGRATION_API_TARGET = 'http://127.0.0.1:18081'
npm run test:integration
```

测试自动启动 Vite（15174），后端与 MySQL/Redis 需预先运行。默认分析公开 PR `octocat/Hello-World#11596`（可通过 `INTEGRATION_PR_URL` 修改），后端需要能够访问 GitHub；无 Token 时受 GitHub 未认证限额限制，游客建任务也受后端每 IP 每小时 5 次限制。默认验证模型名称为 `fake`，请设置 `pr.ai.provider=fake`；若使用真实模型，需将 `INTEGRATION_MODEL_NAME` 设为服务端实际模型名称，测试会实际调用并消耗额度。

覆盖注册/登录、双令牌、本人资料、设置持久化与任务快照、PR 预览/创建/轮询、报告、结构化 Diff、反馈保存/撤销、Markdown 下载、历史筛选、两次分析结果对比、游客凭证恢复/权限、取消/重试/删除、真实刷新旋转/重放/退出撤销。代码为 `integration/backend.spec.mjs`、配置为 `playwright.integration.config.mjs`；产物位于 `test-results/integration/`。

本机联调使用独立的 MySQL 3307（数据目录 `backend/target/integration/mysql-data`）、Redis 6379 的 DB1、后端 8080 和前端 5173。后端本地配置 `application-dev.yml` 已忽略，模型使用 fake provider。Redis 使用 [Redis Windows 移植版](https://github.com/redis-windows/redis-windows/releases/tag/8.10.2)，下载后已校验发布页 SHA256；仅用于本机开发。该结果验证真实业务链路，不代表真实模型的检出质量、不同 head SHA 的对比与过期判定或 Docker/Nginx 部署已验收，详见 [前端回归与验收](../docs/前端回归与验收.md)。
