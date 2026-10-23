# AI PR Review 前端

## 组件组织

- `src/components/ui/`：图标、链接、提示、分页等基础组件，一个职责一个文件，通过 `index.ts` 统一导出；组件内部直接引用同目录文件。
- `src/components/common/`：加载状态、语言切换等通用组件。
- `src/components/diff/`：Diff 渲染与代码高亮；滚动位置缓存跟随 DiffViewer 模块保存。
- `src/components/issues/`：问题编辑与反馈表单。
- `src/components/settings/`：外观偏好和分析设置表单。
- `src/components/workspace/`：工作台外壳、进度轮询、任务操作及共享 props 类型。
- `src/pages/`：页面数据请求、页面级状态与业务组件组合；总览单独放在 `Overview.tsx`。

公共组件从 `components/ui` 导入，业务组件按模块直接导入。业务子组件保留自己的表单或渲染状态；共享类型放在独立类型文件，组件不反向依赖页面。样式继续使用现有的样式文件。

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

接口与 Apifox 文档的对应关系：`mock/server.mjs` 按 `/api` 前缀路由，`mock/fixtures.mjs` 存种子数据；导出 `/api/tasks/{id}/export` 返回 `text/markdown` 原文，`{code,message,data}` 包装与前端 `lib/api.ts` 的解析一致。

```powershell
npm run build
npm run lint
```

## 界面语言

所有界面翻译集中在 `src/i18n/locales/zh-CN/common.json` 和 `src/i18n/locales/en-US/common.json`，使用相同的平铺 key。新增文案时同时更新两份 JSON，装饰英文保留在页面中。

`src/i18n/index.ts` 统一注册语言资源并提供 `translate`；`PreferencesProvider` 管理当前语言，组件可用 `useTranslation()` 获取 `{ t, language }`，然后调用 `t('homeTitle')`。已有页面的 `usePreferences().t` 也使用同一个翻译函数，不维护另一套字典。

`MessageKey` 从中文 JSON 推导，TypeScript 检查英文 key 的缺失或多余，以及调用处 key 的拼写。两份小型语言包随前端一起打包，切换时不需要额外请求；缺失或空文案回退至中文。界面语言统一使用 `zh-CN` / `en-US`，继续沿用本地持久化和登录用户的设置同步。报告正文和导出语言独立于界面切换。

## 接口基准与功能边界

对照 `origin/master` 的接口文档及后端实际 Controller、DTO、VO（2026-10-22，`cca8ccb`），优先采用当前实现：`/api`、成功码 `200`、`{ total, records }` 分页、用户名登录、注册后返回登录页、Sa-Token 双令牌。

- 登录成功后令牌存于当前标签页的 `sessionStorage`；401 时合并并发刷新请求、旋转双令牌，并重放一次请求；刷新失败清除会话。退出成功后清除会话。
- 游客入口：`/tasks/123?guestToken=...`，子页面保留令牌，所有查询、反馈与导出携带 `X-Guest-Token`。令牌属于访问凭证，请只分享给可信人员。
- 历史：仓库、PR、状态、日期、排序、分页；筛选保留在 URL。只有登录用户请求历史。
- 总览：固定 head SHA、过期提醒、阶段进度、风险分布、覆盖范围、重点文件、配置快照、报告与 Markdown 下载。运行中每两秒轮询，终态停止并重读详情；离开页面取消请求。
- 文件：文件列表与筛选、统一/并排 Diff、补丁行号、基础词法高亮、风险联动、二进制/重命名/跳过说明；Diff 每批渲染 250 行，定位可自动展开目标行，切换问题详情保留滚动位置。
- 问题：筛选、排序、分页、详情与证据、编辑建议、四种处理状态、误报理由、撤销、下一条待确认、跳转 Diff/GitHub。报告级提示保留空定位。
- 偏好：浅色/深色/跟随系统，中文/英文；游客本地保存主题语言，登录用户同步设置；报告正文和导出保留原生成语言。

当前 master 已实现 PR 预览、创建/取消/重试、删除任务、版本对比和结构化 Diff（`/files/{fileId}/diff`）接口，对应功能默认开启。Diff 行号与风险标记以后端结构化结果为准，前端不再自行解析 patch。联调缺少这些接口的旧后端时，可显式关闭：

| 环境变量 | 关闭的功能 |
| --- | --- |
| `VITE_ENABLE_ANALYSIS=false` | `/prs/preview`、`POST /tasks`、`/cancel`、`/retry` |
| `VITE_ENABLE_COMPARISON=false` | `/tasks/{base}/compare/{target}` |
| `VITE_ENABLE_TASK_DELETE=false` | `DELETE /tasks/{id}` |

启用前应确认新版接口仍返回成功码 `200`。项目文档早期目标契约中的 `/api/v1`、JWT、成功码 `0`、邮箱登录与 `list` 分页不适用于当前后端。版本选择展示同一 PR 最近 100 条任务中的已完成/部分完成任务。

## 主要交互与验证

首页 → 预览/分析（服务启用后）→ `/tasks/:id` 总览 → 文件或风险 → 问题详情 → 编辑反馈 → 返回定位 Diff → 导出。游客可在首页输入任务编号和令牌直接进入。版本对比入口位于工作台标签栏。

联调前启动 master 后端及其 MySQL/Redis。可使用后端 README 中的本地种子账号登录；真实端到端分析需在服务端配置模型 Key，无 Key 时可用 `pr.ai.provider=fake` 验证链路。

提交前需通过 `npm run build` 与 `npm run lint`。此前记录过按当前接口契约模拟的 24 项浏览器检查，覆盖游客请求头、历史权限、报告下载、Diff 双视图、反馈保存/撤销、主题跟随系统、语言持久化、并发 token 刷新、终态停止轮询和过期游客链接。检查包含 1440px 桌面、768px 平板及 390px 手机宽度；未连接真实 MySQL/Redis 后端验证。
