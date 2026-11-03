#!/usr/bin/env node
// 本地联调用的 mock 后端：实现 Apifox 项目 8918366（ai-pr-review）中的接口契约。
// 零依赖，Node 18+ 直接运行。默认端口 8080，与 vite.config.ts 的 API_PROXY_TARGET 对齐。
//
//   npm run mock                      # http://localhost:8080
//   MOCK_PORT=9090 npm run mock       # 换端口，同时改 .env.local 的 API_PROXY_TARGET
//   MOCK_DELAY=400 npm run mock       # 给每个响应加延迟，方便看 loading 态
//
// 演示账号：demo / demo123456（见 fixtures.mjs 的 DEMO_ACCOUNT）

import { createServer } from 'node:http'
import {
  DEMO_ACCOUNT,
  buildCompare,
  createTask,
  exportMarkdown,
  findTask,
  issueDetail,
  issueBrief,
  issueStats,
  issuesOf,
  filesOf,
  listTasks,
  materialize,
  session,
  settings,
  taskDetail,
  taskFileBrief,
  tasks,
  users,
} from './fixtures.mjs'

const PORT = Number(process.env.MOCK_PORT || 8080)
const DELAY = Number(process.env.MOCK_DELAY || 0)

const ok = (data = null, message = '操作成功') => ({ code: 200, message, data })
const fail = (code, message, data = null) => ({ code, message, data })

class HttpError extends Error {
  constructor(status, body) {
    super(body.message || 'error')
    this.status = status
    this.body = body
  }
}

function requireUser(ctx) {
  if (!ctx.session) throw new HttpError(401, fail(401, '未登录或令牌已失效'))
  return ctx.session
}

// 任务级接口：登录用户直接放行；游客任务必须带匹配的 X-Guest-Token
function authorizeTask(ctx, task) {
  const guestToken = ctx.headers['x-guest-token']
  if (task.isGuest) {
    if (guestToken !== task.guestToken || (task.expireTime && new Date(task.expireTime).getTime() <= Date.now()))
      throw new HttpError(403, fail(403, '游客令牌无效'))
    return { guestToken }
  }
  const current = requireUser(ctx)
  return { guestToken: null, user: current.userInfo }
}

function pageOf(records, { page = 1, pageSize = 20 }) {
  const number = Math.max(1, Number(page) || 1)
  const size = Math.max(1, Number(pageSize) || 20)
  const start = (number - 1) * size
  return { total: records.length, records: records.slice(start, start + size) }
}

function splitNumbers(value) {
  return String(value || '')
    .split(',')
    .map((item) => Number(item))
    .filter((item) => Number.isFinite(item) && item > 0)
}

function sortIssues(records, sort) {
  const copy = [...records]
  const byTime = (a, b) => a.updateTime.localeCompare(b.updateTime)
  switch (sort) {
    case 'severity_asc':
      return copy.sort((a, b) => a.severity - b.severity)
    case 'createTime_asc':
      return copy.sort(byTime)
    case 'createTime_desc':
      return copy.sort((a, b) => byTime(b, a))
    default:
      return copy.sort((a, b) => b.severity - a.severity)
  }
}

function sortFiles(records, sort) {
  const copy = [...records]
  if (sort === 'addLines_desc') return copy.sort((a, b) => b.addLines - a.addLines)
  if (sort === 'deleteLines_desc') return copy.sort((a, b) => b.deleteLines - a.deleteLines)
  return copy.sort((a, b) => a.filePath.localeCompare(b.filePath))
}

function findIssue(task, issueId) {
  const issue = issuesOf(task).find((item) => item.issueId === issueId)
  if (!issue) throw new HttpError(404, fail(404, '问题不存在'))
  return issue
}

function route(method, pathname, ctx) {
  const path = pathname.replace(/\/+$/, '') || '/'
  const parts = path.split('/').filter(Boolean) // ['api', ...]
  if (parts[0] !== 'api') throw new HttpError(404, fail(404, '接口不存在'))
  const [, head, ...rest] = parts
  const q = ctx.query

  // ---- 认证 ----
  if (head === 'auth') {
    const action = rest[0]
    if (method === 'POST' && action === 'register') {
      const { username, password } = ctx.body || {}
      if (!username || !/^[a-zA-Z0-9_]{3,50}$/.test(username))
        throw new HttpError(400, fail(400, '用户名需为 3-50 位字母、数字或下划线'))
      if (!password || String(password).length < 6)
        throw new HttpError(400, fail(400, '密码至少 6 位'))
      if (users.some((user) => user.username === username))
        throw new HttpError(409, fail(409, '用户名已被占用'))
      const now = new Date().toISOString()
      users.push({
        id: Math.max(...users.map((user) => user.id)) + 1,
        username,
        mockPassword: password,
        nickname: ctx.body.nickname || null,
        email: ctx.body.email || null,
        phone: ctx.body.phone || null,
        avatar: null,
        status: 1,
        role: 'USER',
        lastLoginTime: null,
        createTime: now,
        updateTime: now,
      })
      return ok(null, '注册成功')
    }
    if (method === 'POST' && action === 'login') {
      const { username, password } = ctx.body || {}
      const user = users.find((item) => item.username === username)
      const expected =
        user && (user.mockPassword ?? (user.username === DEMO_ACCOUNT.username ? DEMO_ACCOUNT.password : null))
      if (!user || !expected || password !== expected)
        throw new HttpError(401, fail(401, '用户名或密码错误'))
      if (user.status === 0) throw new HttpError(403, fail(403, '账号已被禁用'))
      user.lastLoginTime = new Date().toISOString()
      return ok(session(user.username), '登录成功')
    }
    if (method === 'POST' && action === 'refresh') {
      const { refreshToken } = ctx.body || {}
      if (!refreshToken) throw new HttpError(400, fail(400, 'refreshToken 不能为空'))
      const id = /^mock-refresh-(\d+)$/.exec(String(refreshToken))?.[1]
      const user = users.find((item) => item.id === Number(id))
      if (!user) throw new HttpError(401, fail(401, 'refreshToken 已失效'))
      return ok(session(user.username), '刷新成功')
    }
    if (method === 'POST' && action === 'logout') {
      requireUser(ctx)
      return ok(null, '已退出登录')
    }
    if (method === 'GET' && action === 'me') {
      const current = requireUser(ctx)
      const user = users.find((item) => item.id === current.userInfo.id)
      return ok({
        ...current.userInfo,
        email: user.email,
        phone: user.phone,
        status: user.status,
        lastLoginTime: user.lastLoginTime,
        createTime: user.createTime,
        updateTime: user.updateTime,
      })
    }
  }

  // ---- 用户 ----
  if (head === 'users') {
    if (rest[0] === 'me' && rest[1] === 'profile') {
      const current = requireUser(ctx)
      const user = users.find(item => item.id === current.userInfo.id)
      if (method === 'PATCH') {
        for (const field of ['nickname', 'email', 'phone', 'avatar']) {
          if (field in (ctx.body || {})) user[field] = ctx.body[field]
        }
        user.updateTime = new Date().toISOString()
      }
      if (method === 'GET' || method === 'PATCH') {
        const { mockPassword, ...profile } = user
        void mockPassword
        return ok(profile)
      }
    }
    if (rest[0] === 'me' && rest[1] === 'settings') {
      requireUser(ctx)
      if (method === 'GET') return ok(settings)
      if (method === 'PATCH') {
        Object.assign(settings, ctx.body || {}, { updateTime: new Date().toISOString() })
        return ok(settings, '设置已保存')
      }
    }
    if (rest[0] === 'page' && method === 'GET') {
      requireUser(ctx)
      let records = users
      if (q.keyword)
        records = records.filter((user) =>
          [user.username, user.nickname, user.email].some((value) =>
            String(value || '').includes(q.keyword),
          ),
        )
      if (q.status !== undefined) records = records.filter((user) => user.status === Number(q.status))
      if (q.role) records = records.filter((user) => user.role === q.role)
      return ok(pageOf(records, q))
    }
    if (rest[0] === 'search' && method === 'GET') {
      requireUser(ctx)
      return ok(pageOf(users, q))
    }
    if (rest[0] === 'stats' && rest[1] === 'roles' && method === 'GET') {
      requireUser(ctx)
      const counts = new Map()
      users.forEach((user) => counts.set(user.role, (counts.get(user.role) || 0) + 1))
      return ok([...counts].map(([role, total]) => ({ role, total })))
    }
    if (rest.length === 0) {
      requireUser(ctx)
      if (method === 'POST') {
        const { username, password } = ctx.body || {}
        if (!username || !password) throw new HttpError(400, fail(400, '用户名与密码必填'))
        const id = Math.max(...users.map((user) => user.id)) + 1
        users.push({
          id,
          username,
          mockPassword: password,
          nickname: ctx.body.nickname || null,
          email: ctx.body.email || null,
          phone: ctx.body.phone || null,
          avatar: null,
          status: 1,
          role: ctx.body.role || 'USER',
          lastLoginTime: null,
          createTime: new Date().toISOString(),
          updateTime: new Date().toISOString(),
        })
        return ok(id, '新增成功')
      }
    }
    if (rest.length === 1) {
      requireUser(ctx)
      const user = users.find((item) => item.id === Number(rest[0]))
      if (!user) throw new HttpError(404, fail(404, '用户不存在'))
      if (method === 'GET') return ok(user)
      if (method === 'PUT') {
        Object.assign(user, ctx.body || {}, { updateTime: new Date().toISOString() })
        return ok(null, '更新成功')
      }
      if (method === 'DELETE') {
        users.splice(users.indexOf(user), 1)
        return ok(null, '删除成功')
      }
    }
    if (rest.length === 2 && rest[1] === 'status' && method === 'PUT') {
      requireUser(ctx)
      const user = users.find((item) => item.id === Number(rest[0]))
      if (!user) throw new HttpError(404, fail(404, '用户不存在'))
      user.status = Number(q.status)
      return ok(null, '状态已更新')
    }
  }

  // ---- PR 预览 ----
  if (head === 'prs' && rest[0] === 'preview' && method === 'POST') {
    const { url } = ctx.body || {}
    if (!/^https:\/\/github\.com\/[\w.-]+\/[\w.-]+\/pull\/[1-9]\d*\/?$/.test(String(url || '')))
      throw new HttpError(400, fail(400, 'PR 地址不合法，应为 https://github.com/{owner}/{repo}/pull/{number}'))
    const match = /github\.com\/([\w.-]+)\/([\w.-]+)\/pull\/(\d+)/.exec(url)
    const reusable = ctx.session && tasks.find(
      (task) =>
        !task.isGuest &&
        task.status === 3 &&
        task.pr.repoFullName === `${match[1]}/${match[2]}` &&
        task.pr.prNumber === Number(match[3]),
    )
    const template = tasks.find((task) => !task.isGuest) || tasks[0]
    return ok({
      pr: {
        ...(taskDetail(template).pr),
        repoFullName: `${match[1]}/${match[2]}`,
        prNumber: Number(match[3]),
      },
      reusableTask: reusable
        ? {
            taskId: reusable.taskId,
            status: reusable.status,
            reportLanguage: reusable.reportLanguage,
            createTime: reusable.createTime,
            finishTime: reusable.finishTime,
          }
        : null,
    })
  }

  // ---- 任务 ----
  if (head === 'tasks') {
    if (rest.length === 0) {
      if (method === 'GET') { requireUser(ctx); return ok(listTasks(q)) }
      if (method === 'POST') {
        const { url, reportLanguage } = ctx.body || {}
        if (!/^https:\/\/github\.com\/[\w.-]+\/[\w.-]+\/pull\/[1-9]\d*\/?$/.test(String(url || '')))
          throw new HttpError(400, fail(400, 'PR 地址不合法'))
        const task = createTask({ url, reportLanguage: reportLanguage || 'zh-CN', guest: !ctx.session })
        return ok({
          taskId: task.taskId,
          status: task.status,
          stage: task.stage,
          progress: task.progress,
          reportLanguage: task.reportLanguage,
          isGuest: task.isGuest,
          guestToken: task.guestToken,
          expireTime: task.expireTime,
          shareUrl: null,
          createTime: task.createTime,
          retryOfTaskId: null,
        }, '任务已创建')
      }
    }
    // 游客入口允许不带令牌访问，但必须带匹配的 X-Guest-Token
    const taskId = Number(rest[0])
    const task = findTask(taskId)
    if (!task) throw new HttpError(404, fail(404, '任务不存在'))
    authorizeTask(ctx, task)

    if (rest.length === 1 && method === 'GET') return ok(taskDetail(task))
    if (rest.length === 1 && method === 'DELETE') {
      requireUser(ctx)
      if (task.isGuest) throw new HttpError(403, fail(403, '游客任务不可删除'))
      tasks.splice(tasks.indexOf(task), 1)
      return ok({ taskId, deleted: true }, '删除成功')
    }
    if (rest[1] === 'progress' && method === 'GET') {
      const current = materialize(task)
      return ok({
        taskId: current.taskId,
        status: current.status,
        stage: current.stage,
        progress: current.progress,
        errorCode: current.errorCode,
        errorMessage: current.errorMessage,
        startTime: current.startTime,
        finishTime: current.finishTime,
        updateTime: new Date().toISOString(),
      })
    }
    if (rest[1] === 'cancel' && method === 'POST') {
      const current = materialize(task)
      if (![1, 2].includes(current.status))
        throw new HttpError(409, fail(409, '当前状态不可取消'))
      current.cancelled = true
      current.dynamic = false
      current.status = 6
      current.stage = Math.max(current.stage, 1)
      current.finishTime = new Date().toISOString()
      current.canCancel = false
      return ok({ taskId, status: current.status, finishTime: current.finishTime }, '任务已取消')
    }
    if (rest[1] === 'retry' && method === 'POST') {
      const current = materialize(task)
      const retried = createTask({
        url: `https://github.com/${current.pr.repoFullName}/pull/${current.pr.prNumber}`,
        reportLanguage: current.reportLanguage,
        guest: current.isGuest,
      })
      return ok({
        taskId: retried.taskId,
        status: retried.status,
        stage: retried.stage,
        progress: retried.progress,
        reportLanguage: retried.reportLanguage,
        isGuest: retried.isGuest,
        guestToken: retried.guestToken,
        expireTime: retried.expireTime,
        shareUrl: null,
        createTime: retried.createTime,
        retryOfTaskId: taskId,
      }, '已重新提交')
    }
    if (rest[1] === 'report' && method === 'GET') {
      const current = materialize(task)
      if (!current.report) throw new HttpError(404, fail(404, '报告尚未生成'))
      return ok(current.report)
    }
    if (rest[1] === 'export' && method === 'GET') {
      const markdown = exportMarkdown(task)
      if (!markdown) throw new HttpError(404, fail(404, '报告尚未生成'))
      ctx.raw = { type: 'text/markdown; charset=utf-8', body: markdown }
      return undefined
    }
    if (rest[1] === 'compare' && rest[2] && method === 'GET') {
      const target = findTask(Number(rest[2]))
      if (!target) throw new HttpError(404, fail(404, '目标任务不存在'))
      if (task.isGuest || target.isGuest)
        throw new HttpError(403, fail(403, '游客任务不支持版本对比'))
      return ok(buildCompare(task, target))
    }
    if (rest[1] === 'files') {
      const files = filesOf(task)
      if (rest.length === 2 && method === 'GET') {
        let records = files
        if (q.keyword) records = records.filter((file) => file.filePath.includes(q.keyword))
        if (q.status) records = records.filter((file) => file.status === Number(q.status))
        if (q.changeType) records = records.filter((file) => file.changeType === Number(q.changeType))
        if (q.priorityOnly === 'true' || q.priorityOnly === '1')
          records = records.filter((file) => !!file.priorityReason)
        const page = pageOf(sortFiles(records, q.sort), q)
        return ok({ ...page, records: page.records.map(taskFileBrief) })
      }
      if (rest.length === 3 || (rest.length === 4 && rest[3] === 'diff')) {
        const file = files.find((item) => item.fileId === Number(rest[2]))
        if (!file) throw new HttpError(404, fail(404, '文件不存在'))
        if (rest[3] === 'diff' && method === 'GET') return ok(file)
        if (method === 'GET') {
          const { rows, patchAvailable, addedLines, hunkLines, ...detail } = file
          return ok(detail)
        }
      }
    }
    if (rest[1] === 'issues') {
      const issues = issuesOf(task)
      if (rest.length === 2 && method === 'GET') {
        let records = issues
        const severities = splitNumbers(q.severity)
        const categories = splitNumbers(q.category)
        const statuses = splitNumbers(q.feedbackStatus)
        if (severities.length) records = records.filter((issue) => severities.includes(issue.severity))
        if (categories.length) records = records.filter((issue) => categories.includes(issue.category))
        if (statuses.length) records = records.filter((issue) => statuses.includes(issue.feedbackStatus))
        if (q.fileId) records = records.filter((issue) => issue.fileId === Number(q.fileId))
        if (q.keyword)
          records = records.filter((issue) =>
            [issue.title, issue.triggerCondition, issue.impact, issue.evidence].some((value) =>
              String(value || '').includes(q.keyword),
            ),
          )
        const stats = issueStats(issues)
        const page = pageOf(sortIssues(records, q.sort), q)
        return ok({ ...page, records: page.records.map(issueBrief), stats })
      }
      if (rest.length === 3) {
        const issue = findIssue(task, Number(rest[2]))
        if (method === 'GET') return ok(issueDetail(issue))
        if (method === 'PATCH') {
          const { feedbackStatus, feedbackRemark, suggestion } = ctx.body || {}
          if (feedbackStatus !== undefined) issue.feedbackStatus = Number(feedbackStatus)
          if (feedbackRemark !== undefined) issue.feedbackRemark = feedbackRemark || null
          if (suggestion !== undefined) {
            if (!String(suggestion).trim())
              throw new HttpError(400, fail(400, 'Review 建议不能为空'))
            issue.suggestion = suggestion
          }
          if (issue.feedbackStatus === 4 && !String(issue.feedbackRemark || '').trim())
            throw new HttpError(400, fail(400, '标记误报时必须填写理由'))
          // 游客也可以反馈，操作人记为空
          if (ctx.session) {
            issue.feedbackUserId = ctx.session.userInfo.id
          } else {
            issue.feedbackUserId = null
          }
          issue.feedbackTime = new Date().toISOString()
          issue.updateTime = issue.feedbackTime
          return ok(issueDetail(issue), '已保存')
        }
      }
      if (rest.length === 4 && rest[3] === 'undo' && method === 'POST') {
        const issue = findIssue(task, Number(rest[2]))
        issue.feedbackStatus = 1
        issue.feedbackRemark = null
        issue.feedbackUserId = null
        issue.feedbackTime = null
        issue.suggestion = issue.aiSuggestion
        issue.updateTime = new Date().toISOString()
        return ok(issueDetail(issue), '已撤销')
      }
    }
  }

  throw new HttpError(404, fail(404, `未实现的接口：${method} ${path}`))
}

const server = createServer(async (req, res) => {
  const url = new URL(req.url, `http://${req.headers.host || 'localhost'}`)
  const chunks = []
  for await (const chunk of req) chunks.push(chunk)
  const text = Buffer.concat(chunks).toString('utf8')
  let body = null
  if (text) {
    try {
      body = JSON.parse(text)
    } catch {
      body = null
    }
  }
  const authorization = req.headers.authorization || ''
  const token = authorization.startsWith('Bearer ') ? authorization.slice(7) : ''
  const userId = Number(/^mock-access-(\d+)-/.exec(token)?.[1])
  const currentUser = users.find((user) => user.id === userId)
  const ctx = {
    headers: req.headers,
    query: Object.fromEntries(url.searchParams),
    body,
    session: currentUser
      ? {
          userInfo: {
            id: currentUser.id,
            username: currentUser.username,
            nickname: currentUser.nickname,
            avatar: currentUser.avatar,
            role: currentUser.role,
          },
        }
      : null,
    raw: null,
  }

  if (DELAY) await new Promise((resolve) => setTimeout(resolve, DELAY))

  let status = 200
  let payload
  try {
    const result = route(req.method, url.pathname, ctx)
    if (ctx.raw) {
      status = 200
      res.writeHead(status, {
        'Content-Type': ctx.raw.type,
        'Cache-Control': 'no-store',
        'Access-Control-Allow-Origin': '*',
      })
      res.end(ctx.raw.body)
      log(req, url, status)
      return
    }
    payload = result
  } catch (error) {
    if (error instanceof HttpError) {
      status = error.status
      payload = error.body
    } else {
      status = 500
      payload = fail(500, error?.message || '服务器内部错误')
      console.error(error)
    }
  }
  log(req, url, status)
  res.writeHead(status, {
    'Content-Type': 'application/json; charset=utf-8',
    'Cache-Control': 'no-store',
    'Access-Control-Allow-Origin': '*',
    'Access-Control-Allow-Headers': '*',
    'Access-Control-Allow-Methods': '*',
  })
  res.end(JSON.stringify(payload))
})

function log(req, url, status) {
  const mark = status >= 400 ? '✗' : '✓'
  const route = url.pathname + (url.search || '')
  console.log(`${mark} ${String(status).padEnd(3)} ${req.method.padEnd(6)} ${route}`)
}

server.listen(PORT, () => {
  console.log(`mock 后端已启动：http://localhost:${PORT}`)
  console.log(`演示账号：${DEMO_ACCOUNT.username} / ${DEMO_ACCOUNT.password}`)
  console.log('前端 npm run dev 后，API_PROXY_TARGET 指向该端口即可。')
  console.log(`可用任务：${tasks.filter((task) => !task.isGuest).map((task) => task.taskId).join(', ')}`)
})
