import { test, expect } from './fixtures.mjs'

test('GitHub 登录成功时不闪现失败提示', async ({ page }) => {
  const response = await page.request.post('/api/auth/login', { data: { username: 'demo', password: 'demo123456' } })
  const { data: session } = await response.json()
  let exchanges = 0
  await page.route('**/api/auth/github/exchange', async route => {
    exchanges += 1
    await new Promise(resolve => setTimeout(resolve, 200))
    await route.fulfill({ json: { code: 200, message: 'ok', data: session } })
  })
  await page.addInitScript(() => {
    window.__githubCallbackErrors = []
    new MutationObserver(() => {
      if (document.body?.innerText.includes('GitHub 登录失败，请重试。')) window.__githubCallbackErrors.push('error shown')
    }).observe(document, { subtree: true, childList: true, characterData: true })
  })

  await page.goto('/auth/github/callback?ticket=test-ticket')
  await expect(page).toHaveURL(/\/$/)
  await expect(page.getByText('新建评审').first()).toBeVisible()
  expect(exchanges).toBe(1)
  expect(await page.evaluate(() => window.__githubCallbackErrors)).toEqual([])
})

test('GitHub 回调没有凭据时显示错误', async ({ page }) => {
  await page.goto('/auth/github/callback')
  await expect(page.getByText('GitHub 登录失败，请重试。')).toBeVisible()
})
