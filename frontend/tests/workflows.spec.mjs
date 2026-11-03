import { test, expect } from './fixtures.mjs'
import { readFileSync } from 'node:fs'

const zh = JSON.parse(readFileSync(new URL('../src/i18n/locales/zh-CN/common.json', import.meta.url)))
const en = JSON.parse(readFileSync(new URL('../src/i18n/locales/en-US/common.json', import.meta.url)))
const prUrl = 'https://github.com/octocat/Hello-World/pull/1347'
const fulfill = (route, data, code = 200, message = 'success') => route.fulfill({ json: { code, message, data } })

async function signIn(page, language = 'zh-CN') {
  const response = await page.request.post('/api/auth/login', { data: { username: 'demo', password: 'demo123456' } })
  const { data: session } = await response.json()
  await page.addInitScript(({ session, language }) => {
    sessionStorage.setItem('pr-review.session', JSON.stringify(session))
    localStorage.setItem('pr-review.preferences', JSON.stringify({ language, theme: 3 }))
  }, { session, language })
  return session
}

for (const [language, messages] of [['zh-CN', zh], ['en-US', en]]) {
  test(`退出登录先确认，取消保留会话，确认后跳转登录页（${language}）`, async ({ page }) => {
    const session = await signIn(page, language)
    let logoutRequests = 0
    page.on('request', request => {
      if (new URL(request.url()).pathname === '/api/auth/logout') logoutRequests++
    })
    await page.goto('/tasks')
    page.once('dialog', async dialog => {
      expect(dialog.type()).toBe('confirm')
      expect(dialog.message()).toBe(messages.logoutConfirm)
      await dialog.dismiss()
    })
    await page.getByRole('button', { name: messages.logout, exact: true }).click()
    await expect(page).toHaveURL('/tasks')
    expect(await page.evaluate(() => JSON.parse(sessionStorage.getItem('pr-review.session')).accessToken)).toBe(session.accessToken)
    expect(logoutRequests).toBe(0)
    page.once('dialog', dialog => dialog.accept())
    await page.getByRole('button', { name: messages.logout, exact: true }).click()
    await expect(page).toHaveURL('/login')
    await expect(page.getByRole('heading', { name: messages.loginTitle, exact: true })).toBeVisible()
    expect(await page.evaluate(() => sessionStorage.getItem('pr-review.session'))).toBeNull()
    expect(logoutRequests).toBe(1)
  })
}

test('退出登录接口失败时仍清理本地会话并跳转登录页', async ({ page }) => {
  await signIn(page)
  await page.route('**/api/auth/logout', route => fulfill(route, null, 500, 'service unavailable'))
  await page.goto('/tasks')
  page.once('dialog', dialog => dialog.accept())
  await page.getByRole('button', { name: zh.logout, exact: true }).click()
  await expect(page).toHaveURL('/login')
  expect(await page.evaluate(() => sessionStorage.getItem('pr-review.session'))).toBeNull()
})

test('游客可预览并创建任务，显示有效期，凭当前标签页的记录恢复访问', async ({ page }) => {
  await page.goto('/')
  await expect(page.getByRole('link', { name: zh.tasks, exact: true })).toHaveCount(0)
  await expect(page.getByRole('link', { name: zh.settings, exact: true })).toHaveCount(0)
  await page.getByLabel(zh.prUrl).fill(prUrl)
  await page.getByRole('button', { name: zh.preview, exact: true }).click()
  await expect(page.getByText(zh.prState1, { exact: true })).toBeVisible()
  await expect(page.getByRole('button', { name: zh.start, exact: true })).toBeDisabled()
  await page.getByRole('checkbox').check()
  await page.getByRole('button', { name: zh.start, exact: true }).click()
  await expect(page).toHaveURL(/\/tasks\/\d+\?guestToken=/)
  await expect(page.getByText(zh.guestExpires, { exact: false })).toBeVisible()
  const taskPath = new URL(page.url()).pathname
  const token = new URL(page.url()).searchParams.get('guestToken')
  const restored = page.waitForRequest(request => new URL(request.url()).pathname === `/api${taskPath}` && request.headers()['x-guest-token'] === token)
  await page.goto(taskPath)
  await restored
  await expect(page.getByText(zh.guestExpires, { exact: false })).toBeVisible()
  await expect(page.getByRole('link', { name: zh.compare, exact: true })).toHaveCount(0)
  await page.getByRole('button', { name: zh.cancel, exact: true }).click()
  await expect(page.getByText(zh.status6, { exact: true })).toBeVisible()
  await page.getByRole('button', { name: zh.retry, exact: true }).click()
  await expect.poll(() => new URL(page.url()).pathname).not.toBe(taskPath)
  await expect(page.getByText(zh.guestExpires, { exact: false })).toBeVisible()
  const retriedPath = new URL(page.url()).pathname
  await page.goto(retriedPath)
  await expect(page.getByText(zh.guestExpires, { exact: false })).toBeVisible()
})

test('过期报告禁止修改与撤销，重新分析保留原 PR 和报告语言', async ({ page }) => {
  const session = await signIn(page)
  const response = await page.request.get('/api/tasks/1001/issues', { headers: { Authorization: `Bearer ${session.accessToken}` } })
  const { data } = await response.json()
  await page.goto(`/tasks/1001/issues?issueId=${data.records[0].issueId}`)
  await expect(page.getByRole('button', { name: zh.save, exact: true })).toBeDisabled()
  await expect(page.getByRole('button', { name: zh.undo, exact: true })).toBeDisabled()
  await expect(page.getByRole('button', { name: zh.nextPending, exact: true })).toBeEnabled()
  const task = await (await page.request.get('/api/tasks/1001', { headers: { Authorization: `Bearer ${session.accessToken}` } })).json()
  await page.getByRole('link', { name: zh.reanalyze, exact: true }).click()
  await expect(page.getByLabel(zh.prUrl)).toHaveValue(`https://github.com/${task.data.pr.repoFullName}/pull/${task.data.pr.prNumber}`)
  await expect(page.getByLabel(zh.reportLanguage)).toHaveValue(task.data.reportLanguage)
  await expect(page.getByRole('button', { name: zh.preview, exact: true })).toBeEnabled()
})

test('当前报告仍可保存反馈和撤销，配置快照显示检查类别', async ({ page }) => {
  const session = await signIn(page)
  const headers = { Authorization: `Bearer ${session.accessToken}` }
  await page.goto('/tasks/1003')
  await expect(page.getByText(`${zh.checks}:`, { exact: false })).toContainText(zh.category1)
  const { data } = await (await page.request.get('/api/tasks/1003/issues', { headers })).json()
  const issueId = data.records[0].issueId
  await page.goto(`/tasks/1003/issues?issueId=${issueId}`)
  await expect(page.getByRole('button', { name: zh.save, exact: true })).toBeEnabled()
  const editor = page.locator('fieldset')
  await editor.getByLabel(zh.feedback, { exact: true }).selectOption('4')
  await page.getByLabel(zh.feedbackRemark, { exact: false }).fill('回归测试：已经人工核对边界条件。')
  const saved = page.waitForResponse(response => response.request().method() === 'PATCH' && response.url().endsWith(`/issues/${issueId}`))
  await page.getByRole('button', { name: zh.save, exact: true }).click()
  expect((await (await saved).json()).code).toBe(200)
  await expect(editor.getByLabel(zh.feedback, { exact: true })).toHaveValue('4')
  page.once('dialog', dialog => dialog.accept())
  const undone = page.waitForResponse(response => response.url().endsWith(`/issues/${issueId}/undo`))
  await page.getByRole('button', { name: zh.undo, exact: true }).click()
  expect((await (await undone).json()).code).toBe(200)
  await expect(editor.getByLabel(zh.feedback, { exact: true })).toHaveValue('1')
})

test('文件筛选加载第 201 个文件，并可恢复第二页加载失败', async ({ page }) => {
  await signIn(page)
  let failSecondPage = true
  const requestedPages = []
  await page.route('**/api/tasks/1003/files?*', route => {
    const params = new URL(route.request().url()).searchParams
    const pageNumber = Number(params.get('page'))
    requestedPages.push(pageNumber)
    if (pageNumber === 2 && failSecondPage) return fulfill(route, null, 500, 'internal error')
    const records = Array.from({ length: pageNumber === 1 ? 200 : 1 }, (_, index) => {
      const fileId = (pageNumber - 1) * 200 + index + 1
      return { fileId, filePath: `src/file-${fileId}.ts` }
    })
    return fulfill(route, { total: 201, records })
  })
  await page.goto('/tasks/1003/issues')
  await expect(page.getByText(zh.serverError, { exact: true })).toBeVisible()
  failSecondPage = false
  await page.getByRole('button', { name: zh.retry, exact: true }).click()
  await expect(page.locator('select[name="fileId"] option')).toHaveCount(202)
  await page.getByLabel(zh.file, { exact: true }).selectOption('201')
  const filtered = page.waitForRequest(request => request.url().includes('/api/tasks/1003/issues?') && new URL(request.url()).searchParams.get('fileId') === '201')
  await page.getByRole('button', { name: zh.search, exact: true }).click()
  await filtered
  expect(requestedPages.filter(pageNumber => pageNumber === 2)).toHaveLength(2)
})

test('第 101 条历史版本可选择，对比显示统计、总结变化与低置信匹配', async ({ page }) => {
  const session = await signIn(page, 'en-US')
  const { data } = await (await page.request.get('/api/tasks?pageSize=100', { headers: { Authorization: `Bearer ${session.accessToken}` } })).json()
  const template = data.records.find(task => task.taskId === 1003)
  const records = Array.from({ length: 101 }, (_, index) => ({ ...template, taskId: index === 100 ? 1001 : index === 0 ? 1003 : 2000 + index }))
  await page.route('**/api/tasks?*', route => {
    const params = new URL(route.request().url()).searchParams
    const start = (Number(params.get('page')) - 1) * 100
    return fulfill(route, { total: records.length, records: records.slice(start, start + 100) })
  })
  await page.route('**/api/tasks/1001/compare/1003', async route => {
    const payload = await (await route.fetch()).json()
    payload.data.issues.remaining[0].lineShifted = false
    payload.data.issues.remaining[0].matchConfidence = 'low'
    payload.data.summary.purpose.changed = false
    payload.data.matching.note = 'Verify the evidence across both commits.'
    await route.fulfill({ json: payload })
  })
  await page.goto('/tasks/1003/compare?base=1001&target=1003')
  await expect(page.locator('select[name="base"] option')).toHaveCount(102)
  await page.getByLabel(en.baseVersion).selectOption('1001')
  await page.getByLabel(en.targetVersion).selectOption('1003')
  await page.getByRole('button', { name: en.compareAction, exact: true }).click()
  await expect(page.getByText(en.lowMatchConfidence, { exact: true }).first()).toBeVisible()
  await expect(page.getByText(en.summaryUnchanged, { exact: true })).toBeVisible()
  await expect(page.getByText(en.summaryChanged, { exact: true }).first()).toBeVisible()
  await expect(page.getByText('Verify the evidence across both commits.', { exact: true })).toBeVisible()
  await expect(page.locator('dl')).toHaveCount(2)
  await expect(page.locator('dl').first()).toContainText(en.feedback1)
  const optionText = await page.locator('select[name="base"] option[value="1003"]').textContent()
  expect(optionText).toContain(new Intl.DateTimeFormat('en-US', { dateStyle: 'medium', timeStyle: 'short', timeZone: 'Asia/Shanghai' }).format(new Date(template.createTime)))
  for (const width of [1440, 768, 390]) {
    await page.setViewportSize({ width, height: 1000 })
    await expect(page.getByRole('button', { name: en.compareAction })).toBeVisible()
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
  }
  await page.screenshot({ path: test.info().outputPath('comparison-mobile.png'), fullPage: true })
})

for (const [reason, code, expected] of [
  ['没有访问该仓库的权限', 403, 'taskError3'],
  ['GitHub 请求达到限流上限', 429, 'taskError2'],
  ['GitHub 请求过于频繁，请稍后重试', 429, 'taskError2'],
  ['无权访问该仓库', 403, 'taskError3'],
  ['模型调用超时', 500, 'taskError6'],
]) {
  test(`预览失败展示具体原因：${reason}`, async ({ page }) => {
    await page.route('**/api/prs/preview', route => fulfill(route, null, code, reason))
    await page.goto('/')
    await page.getByLabel(zh.prUrl).fill(prUrl)
    await page.getByRole('button', { name: zh.preview, exact: true }).click()
    await expect(page.getByText(zh[expected], { exact: true })).toBeVisible()
  })
}

test('刷新令牌失败仅刷新一次，清空会话并保留登录返回地址', async ({ page }) => {
  await signIn(page)
  let refreshes = 0
  await page.route('**/api/**', async route => {
    const path = new URL(route.request().url()).pathname
    if (path === '/api/auth/refresh') {
      refreshes++
      await new Promise(resolve => setTimeout(resolve, 100))
      return fulfill(route, null, 401, 'refresh token expired')
    }
    return fulfill(route, null, 401, 'unauthorized')
  })
  const returnTo = '/tasks/1003/issues?severity=3&keyword=token'
  await page.goto(returnTo)
  await expect(page).toHaveURL(/\/login\?expired=1&returnTo=/)
  expect(new URL(page.url()).searchParams.get('returnTo')).toBe(returnTo)
  await expect(page.getByText(zh.sessionExpired, { exact: true })).toBeVisible()
  expect(await page.evaluate(() => sessionStorage.getItem('pr-review.session'))).toBeNull()
  expect(refreshes).toBe(1)
})

test('有效刷新合并并发请求并重放，不跳转登录', async ({ page }) => {
  const session = await signIn(page)
  let refreshes = 0
  let replays = 0
  await page.route('**/api/**', async route => {
    const request = route.request()
    if (new URL(request.url()).pathname === '/api/auth/refresh') {
      refreshes++
      await new Promise(resolve => setTimeout(resolve, 100))
      return fulfill(route, { ...session, accessToken: 'rotated-access', refreshToken: 'rotated-refresh' })
    }
    if (request.headers().authorization === 'Bearer rotated-access') {
      replays++
      return route.fulfill({ response: await route.fetch({ headers: { ...request.headers(), authorization: `Bearer ${session.accessToken}` } }) })
    }
    return fulfill(route, null, 401, 'unauthorized')
  })
  await page.goto('/tasks/1003')
  await expect(page.getByRole('heading', { name: zh.overview, exact: true })).toBeVisible()
  await expect(page).toHaveURL('/tasks/1003')
  expect(refreshes).toBe(1)
  expect(replays).toBeGreaterThanOrEqual(2)
})

test('游客链接失效给出恢复入口，过期的本地凭证不会自动重用', async ({ page }) => {
  await page.goto('/tasks/1007?guestToken=invalid')
  await expect(page.getByText(zh.guestLinkExpired, { exact: true })).toBeVisible()
  await expect(page.getByRole('link', { name: zh.openTask, exact: true })).toBeVisible()
  await page.evaluate(() => sessionStorage.setItem('pr-review.guest-tasks', JSON.stringify({
    1007: { token: 'guest-demo-token-1007', expireTime: '2000-01-01 00:00:00' },
  })))
  const request = page.waitForRequest(request => new URL(request.url()).pathname === '/api/tasks/1007')
  await page.goto('/tasks/1007')
  expect((await request).headers()['x-guest-token']).toBeUndefined()
})
