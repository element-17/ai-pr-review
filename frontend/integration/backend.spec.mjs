import { test, expect } from '@playwright/test'
import { readFileSync } from 'node:fs'

const zh = JSON.parse(readFileSync(new URL('../src/i18n/locales/zh-CN/common.json', import.meta.url)))
const prUrl = process.env.INTEGRATION_PR_URL || 'https://github.com/octocat/Hello-World/pull/11596'
const expectedModel = process.env.INTEGRATION_MODEL_NAME || 'fake'
const [, owner, repo, , prNumber] = new URL(prUrl).pathname.split('/')
const repoFullName = `${owner}/${repo}`
const username = `integration_${Date.now()}`
const password = 'Integration123!'
let session
let taskId
let secondTaskId
let issue
let file
let pageErrors

async function data(response) {
  expect(response.ok()).toBe(true)
  const result = await response.json()
  expect(result.code, result.message).toBe(200)
  return result.data
}

function headers() {
  return { Authorization: `Bearer ${session.accessToken}` }
}

async function finished(request, id, access = headers()) {
  await expect.poll(async () => {
    const progress = await data(await request.get(`/api/tasks/${id}/progress`, { headers: access }))
    return progress.status
  }, { timeout: 90_000, intervals: [500, 1000, 2000] }).toBe(3)
  return data(await request.get(`/api/tasks/${id}`, { headers: access }))
}

async function analyze(page) {
  await page.goto('/')
  await page.getByLabel(zh.prUrl, { exact: true }).fill(prUrl)
  const previewResponse = page.waitForResponse(response => response.url().endsWith('/api/prs/preview'))
  await page.getByRole('button', { name: zh.preview, exact: true }).click()
  const preview = await data(await previewResponse)
  await expect(page.getByText(preview.pr.title, { exact: true })).toBeVisible()
  await page.getByRole('checkbox').check()
  const createdResponse = page.waitForResponse(response => response.request().method() === 'POST' && new URL(response.url()).pathname === '/api/tasks')
  await page.getByRole('button', { name: zh.start, exact: true }).click()
  const created = await data(await createdResponse)
  await expect(page).toHaveURL(new RegExp(`/tasks/${created.taskId}(?:\\?|$)`))
  return created
}

test.describe.serial('真实后端联调（不拦截或模拟 API）', () => {
  test.beforeEach(async ({ page }, testInfo) => {
    pageErrors = []
    page.on('pageerror', error => pageErrors.push(error.message))
    await page.addInitScript(({ session, guest }) => {
      localStorage.setItem('pr-review.preferences', JSON.stringify({ theme: 1, language: 'zh-CN' }))
      if (session && !guest && !sessionStorage.getItem('pr-review.session')) sessionStorage.setItem('pr-review.session', JSON.stringify(session))
    }, { session, guest: testInfo.title.startsWith('游客') })
  })

  test.afterEach(() => {
    expect(pageErrors).toEqual([])
  })

  test('注册并登录真实账号，保存双令牌', async ({ page }) => {
    await page.goto('/register?returnTo=%2F')
    await page.getByLabel(zh.username, { exact: true }).fill(username)
    await page.getByLabel(zh.password, { exact: true }).fill(password)
    await page.getByLabel(zh.confirmPassword, { exact: true }).fill(password)
    await page.getByRole('button', { name: zh.register, exact: true }).click()
    await expect(page).toHaveURL(/\/login\?registered=1/)
    await page.getByLabel(zh.username, { exact: true }).fill(username)
    await page.getByLabel(zh.password, { exact: true }).fill(password)
    await page.getByRole('button', { name: zh.login, exact: true }).click()
    await expect(page).toHaveURL('/')
    session = await page.evaluate(() => JSON.parse(sessionStorage.getItem('pr-review.session')))
    expect(session.accessToken).toBeTruthy()
    expect(session.refreshToken).toBeTruthy()
    expect(session.userInfo.username).toBe(username)
  })

  test('读取个人资料并持久化分析设置', async ({ page, request }) => {
    await page.goto('/settings')
    await page.getByRole('button', { name: zh.viewProfile, exact: true }).filter({ visible: true }).click()
    const dialog = page.getByRole('dialog', { name: zh.profileTitle })
    await expect(dialog).toContainText(username)
    await expect(dialog).toContainText(zh.roleUser)
    await page.keyboard.press('Escape')
    await page.locator('input[name="maxFileCount"]').fill('150')
    await page.getByRole('button', { name: zh.save, exact: true }).click()
    await expect(page.getByText(zh.saved, { exact: true })).toBeVisible()
    const settings = await data(await request.get('/api/users/me/settings', { headers: headers() }))
    expect(settings.maxFileCount).toBe(150)
    await page.reload()
    await expect(page.locator('input[name="maxFileCount"]')).toHaveValue('150')
  })

  test('GitHub PR 预览、创建、轮询完成并显示报告', async ({ page, request }, testInfo) => {
    const created = await analyze(page)
    taskId = created.taskId
    expect(created.isGuest).toBe(false)
    const task = await finished(request, taskId)
    expect(task.analysisConfig.maxFileCount).toBe(150)
    expect(task.modelName).toBe(expectedModel)
    expect(task.stats.files.analyzed).toBeGreaterThan(0)
    expect(task.hasReport).toBe(true)
    await expect(page.getByText(zh.status3, { exact: true })).toBeVisible({ timeout: 15_000 })
    const report = await data(await request.get(`/api/tasks/${taskId}/report`, { headers: headers() }))
    expect(report.modelName).toBe(expectedModel)
    await expect(page.getByText(report.purpose, { exact: true })).toBeVisible()
    await page.screenshot({ path: testInfo.outputPath('real-backend-overview.png'), fullPage: true })
    const files = await data(await request.get(`/api/tasks/${taskId}/files`, { headers: headers() }))
    file = files.records.find(item => !item.isBinary && item.status === 3) || files.records[0]
    const issues = await data(await request.get(`/api/tasks/${taskId}/issues`, { headers: headers() }))
    issue = issues.records.find(item => item.fileId) || issues.records[0]
    expect(issue).toBeTruthy()
  })

  test('结构化 Diff、反馈保存与撤销、Markdown 下载', async ({ page, request }, testInfo) => {
    await page.goto(`/tasks/${taskId}/files?fileId=${file.fileId}`)
    const diff = await data(await request.get(`/api/tasks/${taskId}/files/${file.fileId}/diff`, { headers: headers() }))
    expect(diff.patchAvailable).toBe(true)
    expect(diff.rows.some(row => row.type === 'add')).toBe(true)
    await expect(page.getByRole('heading', { name: file.filePath, exact: true })).toBeVisible()
    await page.getByRole('link', { name: zh.split, exact: true }).click()
    await expect(page).toHaveURL(/view=split/)
    await page.screenshot({ path: testInfo.outputPath('real-backend-diff.png'), fullPage: true })
    await page.goto(`/tasks/${taskId}/issues?issueId=${issue.issueId}`)
    await expect(page.getByRole('heading', { level: 2, name: issue.title, exact: true })).toBeVisible()
    const editor = page.locator('fieldset')
    await editor.getByLabel(zh.feedback, { exact: true }).selectOption('4')
    await page.getByLabel(zh.feedbackRemark, { exact: false }).fill('真实后端联调：人工确认误报并验证撤销。')
    const saved = page.waitForResponse(response => response.request().method() === 'PATCH' && response.url().endsWith(`/issues/${issue.issueId}`))
    await page.getByRole('button', { name: zh.save, exact: true }).click()
    expect((await data(await saved)).feedbackStatus).toBe(4)
    await page.reload()
    await expect(editor.getByLabel(zh.feedback, { exact: true })).toHaveValue('4')
    page.once('dialog', dialog => dialog.accept())
    const undone = page.waitForResponse(response => response.url().endsWith(`/issues/${issue.issueId}/undo`))
    await page.getByRole('button', { name: zh.undo, exact: true }).click()
    expect((await data(await undone)).feedbackStatus).toBe(1)
    const downloadPromise = page.waitForEvent('download')
    await page.getByRole('button', { name: zh.export, exact: true }).click()
    const download = await downloadPromise
    expect(download.suggestedFilename()).toBe(`pr-review-${taskId}.md`)
    const path = testInfo.outputPath(download.suggestedFilename())
    await download.saveAs(path)
    const markdown = readFileSync(path, 'utf8')
    expect(markdown).toContain(repoFullName)
    expect(markdown).toContain(issue.title)
  })

  test('历史筛选与两次真实分析结果对比', async ({ page, request }) => {
    const created = await analyze(page)
    secondTaskId = created.taskId
    await finished(request, secondTaskId)
    await page.goto(`/tasks/${taskId}/compare`)
    await page.getByLabel(zh.baseVersion, { exact: true }).selectOption(String(taskId))
    await page.getByLabel(zh.targetVersion, { exact: true }).selectOption(String(secondTaskId))
    const compared = page.waitForResponse(response => response.url().endsWith(`/compare/${secondTaskId}`))
    await page.getByRole('button', { name: zh.compareAction, exact: true }).click()
    const comparison = await data(await compared)
    expect(comparison.issues.remaining.length).toBeGreaterThan(0)
    await expect(page.getByText(comparison.matching.note, { exact: true })).toBeVisible()
    await page.goto(`/tasks?${new URLSearchParams({ repo: repoFullName, prNumber, status: '3' })}`)
    await expect(page.locator('tbody tr')).toHaveCount(2)
  })

  test('游客真实任务、凭证恢复与访问权限边界', async ({ page, request }, testInfo) => {
    const created = await analyze(page)
    expect(created.isGuest).toBe(true)
    expect(created.guestToken).toBeTruthy()
    const access = { 'X-Guest-Token': created.guestToken }
    await finished(request, created.taskId, access)
    await page.goto(`/tasks/${created.taskId}`)
    await expect(page.getByRole('heading', { name: zh.overview, exact: true })).toBeVisible()
    await expect(page.getByText(zh.status3, { exact: true })).toBeVisible()
    await expect(page.getByRole('link', { name: zh.compare, exact: true })).toHaveCount(0)
    const noToken = await (await request.get(`/api/tasks/${created.taskId}`)).json()
    expect(noToken.code).toBe(401)
    const wrongToken = await (await request.get(`/api/tasks/${created.taskId}`, { headers: { 'X-Guest-Token': 'invalid' } })).json()
    expect(wrongToken.code).toBe(403)
    const history = await (await request.get('/api/tasks')).json()
    expect(history.code).toBe(401)
    await page.setViewportSize({ width: 390, height: 844 })
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
    await page.screenshot({ path: testInfo.outputPath('real-backend-guest-mobile.png'), fullPage: true })
  })

  test('取消任务、界面重试与删除', async ({ page, request }) => {
    const created = await data(await request.post('/api/tasks', { headers: headers(), data: { url: prUrl } }))
    const cancelled = await data(await request.post(`/api/tasks/${created.taskId}/cancel`, { headers: headers() }))
    expect(cancelled.status).toBe(6)
    await page.goto(`/tasks/${created.taskId}`)
    await expect(page.getByText(zh.status6, { exact: true })).toBeVisible()
    const retried = page.waitForResponse(response => response.url().endsWith(`/tasks/${created.taskId}/retry`))
    await page.getByRole('button', { name: zh.retry, exact: true }).click()
    const retry = await data(await retried)
    expect(retry.taskId).not.toBe(created.taskId)
    const retriedTask = await finished(request, retry.taskId)
    expect(retriedTask.modelName).toBe(expectedModel)
    await expect(page.getByRole('button', { name: zh.delete, exact: true })).toBeVisible()
    page.once('dialog', dialog => dialog.accept())
    await page.getByRole('button', { name: zh.delete, exact: true }).click()
    await expect(page).toHaveURL('/tasks')
    const deleted = await (await request.get(`/api/tasks/${retry.taskId}`, { headers: headers() })).json()
    expect(deleted.code).toBe(404)
  })

  test('真实刷新令牌旋转、重放请求与退出撤销', async ({ page, request }) => {
    await page.goto('/')
    await page.evaluate(() => {
      const current = JSON.parse(sessionStorage.getItem('pr-review.session'))
      current.accessToken = 'expired-integration-access-token'
      sessionStorage.setItem('pr-review.session', JSON.stringify(current))
    })
    const refreshed = page.waitForResponse(response => response.url().endsWith('/api/auth/refresh'))
    await page.getByRole('link', { name: zh.tasks, exact: true }).click()
    const rotated = await data(await refreshed)
    expect(rotated.refreshToken).not.toBe(session.refreshToken)
    await expect(page.locator('tbody tr')).not.toHaveCount(0)
    session = rotated
    page.once('dialog', dialog => dialog.accept())
    await page.getByRole('button', { name: zh.logout, exact: true }).click()
    await expect(page).toHaveURL('/login')
    expect(await page.evaluate(() => sessionStorage.getItem('pr-review.session'))).toBeNull()
    const me = await (await request.get('/api/auth/me', { headers: headers() })).json()
    expect(me.code).toBe(401)
  })
})
