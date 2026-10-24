import { test, expect } from '@playwright/test'
import { readFileSync } from 'node:fs'

const zh = JSON.parse(readFileSync(new URL('../src/i18n/locales/zh-CN/common.json', import.meta.url)))
const en = JSON.parse(readFileSync(new URL('../src/i18n/locales/en-US/common.json', import.meta.url)))
test.use({ timezoneId: 'Asia/Shanghai' })

async function initialize(page, { signedIn = false, language = 'zh-CN', theme = 1, followTime = false } = {}) {
  let session = null
  if (signedIn) {
    const response = await page.request.post('/api/auth/login', { data: { username: 'demo', password: 'demo123456' } })
    session = (await response.json()).data
  }
  await page.addInitScript(({ session, language, theme, followTime }) => {
    if (session) sessionStorage.setItem('pr-review.session', JSON.stringify(session))
    if (!localStorage.getItem('pr-review.preferences')) {
      localStorage.setItem('pr-review.preferences', JSON.stringify({ language, theme, followTime }))
    }
  }, { session, language, theme, followTime })
}

test('首页移除三步介绍，主题按钮从系统实际颜色开始直接切换并持久化', async ({ page }) => {
  await initialize(page, { theme: 3 })
  await page.emulateMedia({ colorScheme: 'dark' })
  await page.goto('/')
  await expect(page.getByText(zh.workflowLabel, { exact: true })).toHaveCount(0)
  await expect(page.getByRole('heading', { name: zh.feature1, exact: true })).toHaveCount(0)
  await expect(page.getByRole('heading', { name: zh.guestAccessTitle, exact: true })).toBeVisible()
  await expect(page.locator('html')).toHaveAttribute('data-theme', 'dark')
  await page.getByRole('button', { name: zh.switchToLight, exact: true }).click()
  await expect(page.locator('html')).toHaveAttribute('data-theme', 'light')
  await page.getByRole('button', { name: zh.switchToDark, exact: true }).click()
  await expect(page.locator('html')).toHaveAttribute('data-theme', 'dark')
  await page.getByRole('button', { name: zh.switchToLight, exact: true }).click()
  await page.reload()
  await expect(page.locator('html')).toHaveAttribute('data-theme', 'light')
  const preferences = await page.evaluate(() => JSON.parse(localStorage.getItem('pr-review.preferences')))
  expect(preferences.theme).toBe(1)
  expect(preferences.followTime).toBe(false)
})

test('按当地时间在 19:00 与 07:00 自动切换，取消自动模式恢复手动主题', async ({ page }) => {
  await page.clock.install({ time: new Date('2026-10-23T18:59:30+08:00') })
  await initialize(page, { theme: 2 })
  await page.goto('/settings')
  await expect(page.locator('html')).toHaveAttribute('data-theme', 'dark')
  await page.getByRole('checkbox', { name: zh.themeFollowTime, exact: true }).check()
  await expect(page.locator('html')).toHaveAttribute('data-theme', 'light')
  await expect(page.getByRole('combobox', { name: zh.theme, exact: true })).toBeDisabled()
  await page.clock.fastForward(30_000)
  await expect(page.locator('html')).toHaveAttribute('data-theme', 'dark')
  await page.clock.setSystemTime(new Date('2026-10-24T07:00:00+08:00'))
  await page.evaluate(() => window.dispatchEvent(new Event('focus')))
  await expect(page.locator('html')).toHaveAttribute('data-theme', 'light')
  await page.reload()
  await expect(page.getByRole('checkbox', { name: zh.themeFollowTime, exact: true })).toBeChecked()
  await expect(page.locator('html')).toHaveAttribute('data-theme', 'light')
  await page.getByRole('checkbox', { name: zh.themeFollowTime, exact: true }).uncheck()
  await expect(page.locator('html')).toHaveAttribute('data-theme', 'dark')
})

test('顶栏手动切换退出定时模式，只把后端支持的主题字段发送给设置接口', async ({ page }) => {
  await page.clock.install({ time: new Date('2026-10-23T12:00:00+08:00') })
  await initialize(page, { signedIn: true })
  const patches = []
  page.on('request', request => {
    if (new URL(request.url()).pathname === '/api/users/me/settings' && request.method() === 'PATCH') patches.push(request.postDataJSON())
  })
  await page.goto('/settings')
  await page.getByRole('checkbox', { name: zh.themeFollowTime, exact: true }).check()
  expect(patches).toHaveLength(0)
  const saved = page.waitForResponse(response => response.request().method() === 'PATCH' && response.url().endsWith('/users/me/settings'))
  await page.getByRole('button', { name: zh.switchToDark, exact: true }).click()
  await saved
  await expect(page.locator('html')).toHaveAttribute('data-theme', 'dark')
  await expect(page.getByRole('checkbox', { name: zh.themeFollowTime, exact: true })).not.toBeChecked()
  expect(patches).toEqual([{ theme: 2 }])
  await page.clock.fastForward(60_000)
  await expect(page.locator('html')).toHaveAttribute('data-theme', 'dark')
})

for (const [width, language, messages] of [[1440, 'zh-CN', zh], [768, 'zh-CN', zh], [390, 'en-US', en]]) {
  test(`设置分类导航可滚动定位，修改后跨分类保留并保存（${width}px）`, async ({ page }, testInfo) => {
    await page.setViewportSize({ width, height: 900 })
    await page.emulateMedia({ reducedMotion: 'reduce' })
    await initialize(page, { signedIn: true, language })
    await page.goto('/settings')
    const navigation = page.getByRole('navigation', { name: messages.settingsNavigation, exact: true })
    await expect(navigation.getByRole('link')).toHaveCount(5)
    await page.locator('textarea[name="ignoreDirs"]').fill('.git\nbuild')
    await navigation.getByRole('link', { name: messages.analysisLimits, exact: true }).click()
    await expect(navigation.getByRole('link', { name: messages.analysisLimits, exact: true })).toHaveAttribute('aria-current', 'location')
    await page.getByLabel(messages.maxFiles, { exact: true }).fill('87')
    const requested = page.waitForRequest(request => new URL(request.url()).pathname === '/api/users/me/settings' && request.method() === 'PATCH')
    await page.getByRole('button', { name: messages.save, exact: true }).click()
    expect((await requested).postDataJSON()).toMatchObject({ ignoreDirs: ['.git', 'build'], maxFileCount: 87 })
    await expect(page.getByText(messages.saved, { exact: true })).toBeVisible()
    await navigation.getByRole('link', { name: messages.dataNotice, exact: true }).click()
    await expect(navigation.getByRole('link', { name: messages.dataNotice, exact: true })).toHaveAttribute('aria-current', 'location')
    expect(await page.locator('#settings-data').evaluate(element => {
      const bounds = element.getBoundingClientRect()
      return bounds.top >= 0 && bounds.top < innerHeight
    })).toBe(true)
    expect(await page.evaluate(() => window.scrollY)).toBeGreaterThan(100)
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
    await page.screenshot({ path: testInfo.outputPath(`settings-${width}-scrolled.png`) })
    await page.evaluate(() => window.scrollTo({ top: 0, behavior: 'instant' }))
    await expect(navigation.getByRole('link', { name: messages.appearance, exact: true })).toHaveAttribute('aria-current', 'location')
    await page.screenshot({ path: testInfo.outputPath(`settings-${width}.png`) })
  })
}

test('设置加载失败时导航只显示可用分类，重试后补齐分析分类', async ({ page }) => {
  await initialize(page, { signedIn: true })
  let failed = true
  await page.route('**/api/users/me/settings', route => failed
    ? route.fulfill({ json: { code: 500, message: 'error', data: null } })
    : route.continue())
  await page.goto('/settings')
  const navigation = page.getByRole('navigation', { name: zh.settingsNavigation, exact: true })
  await expect(page.getByText(zh.serverError, { exact: true })).toBeVisible()
  await expect(navigation.getByRole('link')).toHaveCount(2)
  failed = false
  await page.getByRole('button', { name: zh.retry, exact: true }).click()
  await expect(navigation.getByRole('link')).toHaveCount(5)
})
