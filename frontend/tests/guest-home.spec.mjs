import { test, expect } from '@playwright/test'
import { readFileSync } from 'node:fs'

const zh = JSON.parse(readFileSync(new URL('../src/i18n/locales/zh-CN/common.json', import.meta.url)))
const en = JSON.parse(readFileSync(new URL('../src/i18n/locales/en-US/common.json', import.meta.url)))
const prUrl = 'https://github.com/octocat/Hello-World/pull/1347'

async function preferences(page, language = 'zh-CN', theme = 1) {
  await page.addInitScript(({ language, theme }) => {
    if (!localStorage.getItem('pr-review.preferences')) {
      localStorage.setItem('pr-review.preferences', JSON.stringify({ language, theme }))
    }
  }, { language, theme })
}

for (const [width, language, messages, theme] of [[1440, 'zh-CN', zh, 1], [768, 'zh-CN', zh, 1], [390, 'en-US', en, 2]]) {
  test(`未登录展示专用游客首页，公开预览与权限入口正确（${width}px）`, async ({ page }, testInfo) => {
    await page.setViewportSize({ width, height: 1000 })
    await preferences(page, language, theme)
    const protectedRequests = []
    page.on('request', request => {
      const path = new URL(request.url()).pathname
      if (['/api/auth/me', '/api/users/me/settings', '/api/tasks'].includes(path)) protectedRequests.push(request)
    })
    await page.goto('/')
    await expect(page).toHaveTitle(`${messages.guestHome} · AI PR Review`)
    await expect(page.getByRole('heading', { level: 1 })).toContainText(messages.guestTitle)
    await expect(page.getByRole('link', { name: messages.guestHome, exact: true })).toHaveAttribute('aria-current', 'page')
    await expect(page.getByText(messages.home, { exact: true })).toHaveCount(0)
    await expect(page.getByRole('link', { name: messages.tasks, exact: true })).toHaveCount(0)
    await expect(page.getByRole('link', { name: messages.settings, exact: true })).toHaveCount(0)
    await expect(page.getByRole('heading', { name: messages.guestAnalyzeTitle, exact: true })).toBeVisible()
    await expect(page.getByRole('heading', { name: messages.guestAccessTitle, exact: true })).toBeVisible()
    await expect(page.getByText(messages.guestAccountDescription, { exact: true })).toBeVisible()
    expect(protectedRequests).toHaveLength(0)
    await page.screenshot({ path: testInfo.outputPath(`guest-home-${width}.png`), fullPage: true })
    await page.getByLabel(messages.prUrl, { exact: true }).fill(prUrl)
    const previewed = page.waitForRequest(request => new URL(request.url()).pathname === '/api/prs/preview')
    await page.getByRole('button', { name: messages.preview, exact: true }).click()
    expect((await previewed).headers().authorization).toBeUndefined()
    await expect(page.getByText(messages.prState1, { exact: true })).toBeVisible()
    await expect(page.getByRole('button', { name: messages.start, exact: true })).toBeDisabled()
    await expect(page.getByRole('link', { name: messages.viewResult, exact: true })).toHaveCount(0)
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
    await page.screenshot({ path: testInfo.outputPath(`guest-preview-${width}.png`), fullPage: true })
  })
}

test('游客结果入口缺少凭证时先提示，正确令牌与当前标签页恢复均可访问', async ({ page }) => {
  await preferences(page)
  await page.goto('/')
  await page.getByLabel(zh.taskId, { exact: true }).fill('1007')
  await page.getByRole('button', { name: zh.open, exact: true }).click()
  await expect(page).toHaveURL('/')
  await expect(page.getByText(zh.guestAccessRequired, { exact: true })).toBeVisible()
  await page.getByLabel(zh.guestTokenLabel, { exact: true }).fill('guest-demo-token-1007')
  const opened = page.waitForRequest(request => new URL(request.url()).pathname === '/api/tasks/1007')
  await page.getByRole('button', { name: zh.open, exact: true }).click()
  expect((await opened).headers()['x-guest-token']).toBe('guest-demo-token-1007')
  await expect(page).toHaveURL('/tasks/1007?guestToken=guest-demo-token-1007')
  await expect(page.getByRole('heading', { name: zh.overview, exact: true })).toBeVisible()
  await expect(page.getByText(zh.home, { exact: true })).toHaveCount(0)
  await expect.poll(() => page.evaluate(() => !!JSON.parse(sessionStorage.getItem('pr-review.guest-tasks') || '{}')['1007'])).toBe(true)
  await page.getByRole('navigation').getByRole('link', { name: zh.guestHome, exact: true }).click()
  await page.getByLabel(zh.taskId, { exact: true }).fill('1007')
  await expect(page.getByLabel(zh.guestTokenLabel, { exact: true })).toHaveValue('')
  await page.getByRole('button', { name: zh.open, exact: true }).click()
  await expect(page).toHaveURL('/tasks/1007?guestToken=guest-demo-token-1007')
  await expect(page.getByRole('heading', { name: zh.overview, exact: true })).toBeVisible()
})

test('已知过期凭证不会从游客首页自动复用', async ({ page }) => {
  await preferences(page)
  await page.goto('/')
  await page.evaluate(() => sessionStorage.setItem('pr-review.guest-tasks', JSON.stringify({
    1007: { token: 'expired-token', expireTime: '2000-01-01 00:00:00' },
  })))
  const requests = []
  page.on('request', request => { if (new URL(request.url()).pathname === '/api/tasks/1007') requests.push(request) })
  await page.getByLabel(zh.taskId, { exact: true }).fill('1007')
  await page.getByRole('button', { name: zh.open, exact: true }).click()
  await expect(page.getByText(zh.guestAccessRequired, { exact: true })).toBeVisible()
  expect(requests).toHaveLength(0)
  await expect(page).toHaveURL('/')
})

test('游客登录后回到用户首页，预填与语言保留，并可创建本人评审；退出后恢复游客入口', async ({ page }) => {
  await preferences(page)
  const homePath = `/?url=${encodeURIComponent(prUrl)}&reportLanguage=en-US`
  await page.goto(homePath)
  await expect(page.getByLabel(zh.prUrl, { exact: true })).toHaveValue(prUrl)
  await page.getByRole('link', { name: zh.guestLoginAction, exact: true }).click()
  expect(new URL(page.url()).searchParams.get('returnTo')).toBe(homePath)
  await page.getByLabel(zh.username, { exact: true }).fill('demo')
  await page.getByLabel(zh.password, { exact: true }).fill('demo123456')
  await page.getByRole('button', { name: zh.login, exact: true }).click()
  await expect(page).toHaveURL(homePath)
  await expect(page).toHaveTitle(`${zh.home} · AI PR Review`)
  await expect(page.getByRole('heading', { name: zh.home, exact: true })).toBeVisible()
  await expect(page.getByRole('link', { name: zh.guestHome, exact: true })).toHaveCount(0)
  await expect(page.getByRole('link', { name: zh.tasks, exact: true })).toBeVisible()
  await expect(page.getByLabel(zh.prUrl, { exact: true })).toHaveValue(prUrl)
  await expect(page.getByLabel(zh.reportLanguage, { exact: true })).toHaveValue('en-US')
  await page.getByRole('button', { name: zh.preview, exact: true }).click()
  await expect(page.getByText(zh.prState1, { exact: true })).toBeVisible()
  await page.getByRole('checkbox').check()
  const created = page.waitForRequest(request => request.method() === 'POST' && new URL(request.url()).pathname === '/api/tasks')
  await page.getByRole('button', { name: zh.start, exact: true }).click()
  expect((await created).headers().authorization).toMatch(/^Bearer /)
  await expect(page).toHaveURL(/\/tasks\/\d+$/)
  await expect(page.getByText(zh.guestExpires, { exact: false })).toHaveCount(0)
  page.once('dialog', dialog => dialog.accept())
  await page.getByRole('button', { name: zh.logout, exact: true }).click()
  await expect(page).toHaveURL('/login')
  await page.getByRole('link', { name: zh.guestHome, exact: true }).click()
  await expect(page).toHaveTitle(`${zh.guestHome} · AI PR Review`)
  await expect(page.getByText(zh.home, { exact: true })).toHaveCount(0)
})
