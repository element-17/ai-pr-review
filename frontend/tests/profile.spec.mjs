import { test, expect } from '@playwright/test'
import { readFileSync } from 'node:fs'

const zh = JSON.parse(readFileSync(new URL('../src/i18n/locales/zh-CN/common.json', import.meta.url)))
const en = JSON.parse(readFileSync(new URL('../src/i18n/locales/en-US/common.json', import.meta.url)))
const field = (dialog, label) => dialog.getByText(label, { exact: true }).locator('..').locator('dd')
const fulfill = (route, data, code = 200) => route.fulfill({ json: { code, message: 'test', data } })

async function openSignedInPage(page, language = 'zh-CN', theme = 1) {
  const response = await page.request.post('/api/auth/login', { data: { username: 'demo', password: 'demo123456' } })
  const { data: session } = await response.json()
  await page.addInitScript(({ session, language, theme }) => {
    sessionStorage.setItem('pr-review.session', JSON.stringify(session))
    localStorage.setItem('pr-review.preferences', JSON.stringify({ language, theme }))
  }, { session, language, theme })
  const initialized = page.waitForResponse(response => new URL(response.url()).pathname === '/api/auth/me' && response.status() === 200)
  await page.goto('/tasks?repo=octocat%2FHello-World&status=3')
  await initialized
  return session
}

for (const [width, height, language, messages, theme] of [
  [1440, 1000, 'zh-CN', zh, 1],
  [768, 1024, 'zh-CN', zh, 1],
  [390, 844, 'en-US', en, 2],
]) {
  test(`头像打开本人资料，支持键盘与关闭后恢复焦点（${width}px，${language}）`, async ({ page }, testInfo) => {
    await page.setViewportSize({ width, height })
    const session = await openSignedInPage(page, language, theme)
    const originalUrl = page.url()
    const trigger = page.getByRole('button', { name: messages.viewProfile, exact: true }).filter({ visible: true })
    await expect(trigger).toHaveCount(1)
    await trigger.focus()
    const requested = page.waitForRequest(request => new URL(request.url()).pathname === '/api/auth/me')
    await page.keyboard.press('Enter')
    expect((await requested).headers().authorization).toBe(`Bearer ${session.accessToken}`)
    const dialog = page.getByRole('dialog', { name: messages.profileTitle, exact: true })
    await expect(dialog).toBeVisible()
    await expect(field(dialog, messages.username)).toHaveText('demo')
    await expect(field(dialog, messages.profileNickname)).toHaveText('演示账号')
    await expect(field(dialog, messages.profileEmail)).toHaveText('demo@example.com')
    await expect(field(dialog, messages.phone)).toHaveText('13800000000')
    await expect(field(dialog, messages.userRole)).toHaveText(messages.roleAdmin)
    await expect(field(dialog, messages.accountStatus)).toHaveText(messages.accountActive)
    await expect(field(dialog, messages.accountCreated)).toContainText('2026')
    await expect(field(dialog, messages.lastLogin)).not.toHaveText(messages.notProvided)
    await expect(field(dialog, messages.profileUpdated)).not.toHaveText(messages.notProvided)
    const close = dialog.getByRole('button', { name: messages.close, exact: true })
    await expect(close).toBeFocused()
    await page.keyboard.press('Tab')
    await page.keyboard.press('Tab')
    await expect(close).toBeFocused()
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
    expect(await dialog.evaluate(element => element.scrollWidth <= element.clientWidth)).toBe(true)
    await page.screenshot({ path: testInfo.outputPath(`profile-${width}.png`), fullPage: true })
    await page.keyboard.press('Escape')
    await expect(dialog).toHaveCount(0)
    await expect(trigger).toBeFocused()
    await expect(page).toHaveURL(originalUrl)
    expect(await page.evaluate(() => document.body.style.overflow)).toBe('')
    await trigger.click()
    await expect(dialog).toBeVisible()
    await dialog.getByRole('button', { name: messages.close, exact: true }).click()
    await expect(dialog).toHaveCount(0)
    await expect(trigger).toBeFocused()
  })
}

test('每次打开重新读取当前账号接口，缺失资料有占位，头像加载失败回退昵称', async ({ page }) => {
  const session = await openSignedInPage(page)
  const profile = { ...session.userInfo, nickname: '最新昵称', avatar: '/missing-avatar.png', role: 'USER' }
  await page.route('**/missing-avatar.png', route => route.fulfill({ status: 404, body: '' }))
  await page.route('**/api/auth/me', route => fulfill(route, profile))
  const trigger = page.getByRole('button', { name: zh.viewProfile, exact: true }).filter({ visible: true })
  await trigger.click()
  const dialog = page.getByRole('dialog', { name: zh.profileTitle, exact: true })
  await expect(field(dialog, zh.profileNickname)).toHaveText('最新昵称')
  await expect(field(dialog, zh.profileEmail)).toHaveText(zh.notProvided)
  await expect(field(dialog, zh.phone)).toHaveText(zh.notProvided)
  await expect(field(dialog, zh.userRole)).toHaveText(zh.roleUser)
  await expect(field(dialog, zh.accountStatus)).toHaveText(zh.notProvided)
  await expect(field(dialog, zh.accountCreated)).toHaveText(zh.notProvided)
  await expect(dialog.locator('img')).toHaveCount(0)
  await expect(dialog.locator('[aria-hidden="true"]')).toHaveText('最')
  await page.mouse.click(5, 5)
  await expect(dialog).toHaveCount(0)
  profile.email = 'updated@example.com'
  profile.status = 0
  await trigger.click()
  await expect(field(dialog, zh.profileEmail)).toHaveText('updated@example.com')
  await expect(field(dialog, zh.accountStatus)).toHaveText(zh.accountInactive)
})

test('个人资料加载失败可重试，不回退展示缓存的资料', async ({ page }) => {
  const session = await openSignedInPage(page, 'en-US')
  let fail = true
  await page.route('**/api/auth/me', route => fulfill(route, fail ? null : { ...session.userInfo, email: 'recovered@example.com' }, fail ? 500 : 200))
  await page.getByRole('button', { name: en.viewProfile, exact: true }).filter({ visible: true }).click()
  const dialog = page.getByRole('dialog', { name: en.profileTitle, exact: true })
  await expect(dialog.getByText(en.serverError, { exact: true })).toBeVisible()
  await expect(dialog.locator('dl')).toHaveCount(0)
  fail = false
  await dialog.getByRole('button', { name: en.retry, exact: true }).click()
  await expect(field(dialog, en.profileEmail)).toHaveText('recovered@example.com')
  await expect(dialog.getByText(en.serverError, { exact: true })).toHaveCount(0)
})

test('资料加载中可以关闭，重新打开不会显示上次未完成的响应', async ({ page }) => {
  const session = await openSignedInPage(page)
  let release
  const pending = new Promise(resolve => { release = resolve })
  await page.route('**/api/auth/me', async route => {
    await pending
    await fulfill(route, { ...session.userInfo, email: 'stale@example.com' })
  })
  const trigger = page.getByRole('button', { name: zh.viewProfile, exact: true }).filter({ visible: true })
  const requested = page.waitForRequest(request => new URL(request.url()).pathname === '/api/auth/me')
  await trigger.click()
  await requested
  const dialog = page.getByRole('dialog', { name: zh.profileTitle, exact: true })
  await expect(dialog.getByText(zh.loading, { exact: true })).toBeVisible()
  await page.keyboard.press('Escape')
  await expect(dialog).toHaveCount(0)
  await page.route('**/api/auth/me', route => fulfill(route, { ...session.userInfo, email: 'fresh@example.com' }))
  await trigger.click()
  await expect(field(dialog, zh.profileEmail)).toHaveText('fresh@example.com')
  release()
  await expect(field(dialog, zh.profileEmail)).toHaveText('fresh@example.com')
})

test('游客不展示个人资料入口，不请求当前账号信息', async ({ page }) => {
  const requests = []
  page.on('request', request => {
    if (new URL(request.url()).pathname === '/api/auth/me') requests.push(request.url())
  })
  await page.goto('/')
  await expect(page.getByRole('button', { name: zh.viewProfile, exact: true })).toHaveCount(0)
  await expect(page.getByRole('banner').getByRole('link', { name: zh.login, exact: false })).toBeVisible()
  expect(requests).toHaveLength(0)
})

test('查看资料时会话失效会跳转登录，同账号重新登录后需要点击才打开资料', async ({ page }) => {
  await openSignedInPage(page)
  let expired = true
  await page.route('**/api/auth/me', route => expired ? fulfill(route, null, 401) : route.continue())
  await page.route('**/api/auth/refresh', route => fulfill(route, null, 401))
  await page.getByRole('button', { name: zh.viewProfile, exact: true }).filter({ visible: true }).click()
  await expect(page).toHaveURL(/\/login\?.*returnTo=/)
  await expect(page.getByRole('dialog')).toHaveCount(0)
  expect(await page.evaluate(() => document.body.style.overflow)).toBe('')
  expired = false
  await page.getByLabel(zh.username, { exact: true }).fill('demo')
  await page.getByLabel(zh.password, { exact: true }).fill('demo123456')
  await page.getByRole('button', { name: zh.login, exact: true }).click()
  await expect(page).toHaveURL(/\/tasks\?repo=/)
  await expect(page.getByRole('dialog')).toHaveCount(0)
  await page.getByRole('button', { name: zh.viewProfile, exact: true }).filter({ visible: true }).click()
  const dialog = page.getByRole('dialog', { name: zh.profileTitle, exact: true })
  await expect(field(dialog, zh.profileEmail)).toHaveText('demo@example.com')
})
