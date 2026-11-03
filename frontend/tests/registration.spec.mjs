import { test, expect } from './fixtures.mjs'
import { readFileSync } from 'node:fs'

const zh = JSON.parse(readFileSync(new URL('../src/i18n/locales/zh-CN/common.json', import.meta.url)))
const en = JSON.parse(readFileSync(new URL('../src/i18n/locales/en-US/common.json', import.meta.url)))
const input = (page, field) => page.locator(`input[name="${field}"]`)
const isRegister = request => new URL(request.url()).pathname === '/api/auth/register'

async function openRegistration(page, language = 'zh-CN') {
  await page.addInitScript(language => {
    localStorage.setItem('pr-review.preferences', JSON.stringify({ language, theme: 1 }))
  }, language)
  await page.goto('/register?returnTo=%2Ftasks%3Fstatus%3D3')
}

for (const [language, messages, width] of [['zh-CN', zh, 1440], ['en-US', en, 390]]) {
  test(`注册按字段提示、失焦校验和修正反馈，非法资料不请求接口（${language}）`, async ({ page }, testInfo) => {
    await page.setViewportSize({ width, height: 1000 })
    const requests = []
    page.on('request', request => { if (isRegister(request)) requests.push(request) })
    await openRegistration(page, language)
    await page.getByRole('button', { name: messages.register, exact: true }).click()
    await expect(page.getByRole('alert').getByText(messages.usernameRequired, { exact: true })).toBeVisible()
    await expect(page.getByRole('alert').getByText(messages.passwordRequired, { exact: true })).toBeVisible()
    await expect(page.getByRole('alert').getByText(messages.confirmPasswordRequired, { exact: true })).toBeVisible()
    await expect(input(page, 'username')).toBeFocused()
    await input(page, 'username').fill('wrong-name')
    await expect(input(page, 'username')).toHaveAttribute('aria-invalid', 'true')
    await input(page, 'username').fill('valid_name')
    await expect(input(page, 'username')).toHaveAttribute('aria-invalid', 'false')
    await input(page, 'password').fill('abcdef')
    await page.getByRole('button', { name: messages.register, exact: true }).click()
    await expect(input(page, 'confirmPassword')).toBeFocused()
    await input(page, 'confirmPassword').fill('abcdef ')
    await input(page, 'confirmPassword').blur()
    await expect(page.getByRole('alert').getByText(messages.passwordMismatch, { exact: true })).toBeVisible()
    await page.getByRole('button', { name: messages.register, exact: true }).click()
    await expect(input(page, 'confirmPassword')).toBeFocused()
    expect(requests).toHaveLength(0)
    await input(page, 'confirmPassword').fill('abcdef')
    await expect(input(page, 'confirmPassword')).toHaveAttribute('aria-invalid', 'false')
    await input(page, 'password').fill('ABCDEF')
    await expect(page.getByRole('alert').getByText(messages.passwordMismatch, { exact: true })).toBeVisible()
    await input(page, 'password').fill('abcdef')
    await expect(input(page, 'confirmPassword')).toHaveAttribute('aria-invalid', 'false')
    await input(page, 'password').fill('      ')
    await input(page, 'email').fill('invalid-email')
    await input(page, 'phone').fill('12345678901')
    await input(page, 'phone').blur()
    await expect(input(page, 'email')).toHaveAttribute('aria-invalid', 'true')
    await expect(page.getByRole('alert').getByText(messages.emailInvalid, { exact: true })).toBeVisible()
    await expect(page.getByRole('alert').getByText(messages.phoneInvalid, { exact: true })).toBeVisible()
    await page.getByRole('button', { name: messages.register, exact: true }).click()
    await expect(input(page, 'password')).toBeFocused()
    await expect(page.getByRole('button', { name: messages.register, exact: true })).toBeEnabled()
    expect(requests).toHaveLength(0)
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
    await page.screenshot({ path: testInfo.outputPath(`registration-${width}.png`), fullPage: true })
  })
}

test('提交前重新检查用户名、密码边界，不依赖原生长度属性拦截', async ({ page }) => {
  const requests = []
  page.on('request', request => { if (isRegister(request)) requests.push(request) })
  await openRegistration(page)
  for (const [field, value, message] of [
    ['username', '   ', zh.usernameRequired],
    ['username', 'ab', zh.usernameLength],
    ['username', 'a'.repeat(51), zh.usernameLength],
    ['username', 'bad name', zh.usernameCharacters],
    ['username', '测试用户', zh.usernameCharacters],
    ['username', 'bad-name', zh.usernameCharacters],
    ['password', '     ', zh.passwordRequired],
    ['password', 'abcde', zh.registerPasswordLength],
    ['password', 'a'.repeat(33), zh.registerPasswordLength],
  ]) {
    await input(page, 'username').evaluate(element => { element.value = 'valid_user' })
    await input(page, 'password').evaluate(element => { element.value = 'abcdef' })
    await input(page, 'confirmPassword').evaluate(element => { element.value = 'abcdef' })
    await input(page, field).evaluate((element, value) => { element.value = value }, value)
    await page.getByRole('button', { name: zh.register, exact: true }).click()
    await expect(page.getByRole('alert').getByText(message, { exact: true })).toBeVisible()
    await expect(input(page, field)).toBeFocused()
    expect(requests).toHaveLength(0)
  }
})

test('可选昵称、邮箱、手机号填写后校验长度和格式，清空可撤销错误', async ({ page }) => {
  const requests = []
  page.on('request', request => { if (isRegister(request)) requests.push(request) })
  await openRegistration(page)
  await input(page, 'username').fill('valid_user')
  await input(page, 'password').fill('abcdef')
  await input(page, 'confirmPassword').fill('abcdef')
  for (const [field, value, message] of [
    ['nickname', '名'.repeat(51), zh.nicknameLength],
    ['email', `${'a'.repeat(90)}@example.com`, zh.emailLength],
    ['email', 'user@@example.com', zh.emailInvalid],
    ['phone', '12912345678', zh.phoneInvalid],
    ['phone', '1380013800', zh.phoneInvalid],
    ['phone', '138001380000', zh.phoneInvalid],
  ]) {
    await input(page, field).evaluate((element, value) => { element.value = value }, value)
    await page.getByRole('button', { name: zh.register, exact: true }).click()
    await expect(page.getByRole('alert').getByText(message, { exact: true })).toBeVisible()
    await expect(input(page, field)).toHaveAttribute('aria-invalid', 'true')
    expect(requests).toHaveLength(0)
    await input(page, field).fill('')
    await input(page, field).blur()
    await expect(input(page, field)).toHaveAttribute('aria-invalid', 'false')
  }
})

test('最小合法用户名和密码可注册，选填项留空不发送，注册后可登录并保留返回页', async ({ page }) => {
  await openRegistration(page)
  await input(page, 'username').fill('  r_1  ')
  await input(page, 'password').fill('abcdef')
  await input(page, 'confirmPassword').fill('abcdef')
  await input(page, 'nickname').fill('   ')
  const requested = page.waitForRequest(isRegister)
  await page.getByRole('button', { name: zh.register, exact: true }).click()
  expect((await requested).postDataJSON()).toEqual({ username: 'r_1', password: 'abcdef' })
  await expect(page).toHaveURL(/\/login\?registered=1&returnTo=/)
  await expect(page.getByText(zh.registerSuccess, { exact: true })).toBeVisible()
  await expect(input(page, 'confirmPassword')).toHaveCount(0)
  expect(await page.evaluate(() => sessionStorage.getItem('pr-review.session'))).toBeNull()
  await input(page, 'username').fill('r_1')
  await input(page, 'password').fill('abcdef')
  await page.getByRole('button', { name: zh.login, exact: true }).click()
  await expect(page).toHaveURL('/tasks?status=3')
})

test('最大合法长度和手机号允许提交，密码首尾空格保持原值', async ({ page }) => {
  await openRegistration(page)
  const values = {
    username: `upper_${'u'.repeat(44)}`,
    password: ` ${'A'.repeat(30)} `,
    nickname: '昵称'.repeat(25),
    email: `${'e'.repeat(64)}@${'d'.repeat(31)}.com`,
    phone: '19999999999',
  }
  for (const [field, value] of Object.entries(values)) await input(page, field).fill(value)
  await input(page, 'confirmPassword').fill(values.password)
  const requested = page.waitForRequest(isRegister)
  await page.getByRole('button', { name: zh.register, exact: true }).click()
  expect((await requested).postDataJSON()).toEqual(values)
  await expect(page).toHaveURL(/\/login\?registered=1&returnTo=/)
  await input(page, 'username').fill(values.username)
  await input(page, 'password').fill(values.password)
  await page.getByRole('button', { name: zh.login, exact: true }).click()
  await expect(page).toHaveURL('/tasks?status=3')
})

test('合法资料仍由服务端判重，400/409 用户名已存在均显示可操作提示', async ({ page }) => {
  await openRegistration(page)
  let code = 400
  await page.route('**/api/auth/register', route => route.fulfill({ json: { code, message: '用户名已存在', data: null } }))
  await input(page, 'username').fill('demo')
  await input(page, 'password').fill('abcdef')
  await input(page, 'confirmPassword').fill('abcdef')
  for (const value of [400, 409]) {
    code = value
    await page.getByRole('button', { name: zh.register, exact: true }).click()
    await expect(page.getByRole('alert').getByText(zh.usernameExists, { exact: true })).toBeVisible()
    await expect(page.getByRole('button', { name: zh.register, exact: true })).toBeEnabled()
  }
  await input(page, 'username').fill('another_user')
  await expect(page.getByRole('alert')).toHaveCount(0)
})

test('提交中禁止重复请求，接口失败后保留填写内容并可重试', async ({ page }) => {
  await openRegistration(page)
  let release
  const pending = new Promise(resolve => { release = resolve })
  await page.route('**/api/auth/register', async route => {
    await pending
    await route.fulfill({ json: { code: 500, message: 'internal stack trace', data: null } })
  })
  await input(page, 'username').fill('retry_user')
  await input(page, 'password').fill('abcdef')
  await input(page, 'confirmPassword').fill('abcdef')
  const requests = []
  page.on('request', request => { if (isRegister(request)) requests.push(request) })
  const requested = page.waitForRequest(isRegister)
  await page.getByRole('button', { name: zh.register, exact: true }).click()
  await requested
  await expect(input(page, 'username')).toBeDisabled()
  await expect(input(page, 'confirmPassword')).toBeDisabled()
  await page.locator('form').evaluate(form => { form.requestSubmit(); form.requestSubmit() })
  expect(requests).toHaveLength(1)
  release()
  await expect(page.getByRole('alert').getByText(zh.serverError, { exact: true })).toBeVisible()
  await expect(input(page, 'username')).toHaveValue('retry_user')
  await expect(input(page, 'password')).toHaveValue('abcdef')
  await expect(input(page, 'confirmPassword')).toHaveValue('abcdef')
  await expect(page.getByText('internal stack trace', { exact: true })).toHaveCount(0)
  await expect(page.getByRole('button', { name: zh.register, exact: true })).toBeEnabled()
  await page.unroute('**/api/auth/register')
  await page.getByRole('button', { name: zh.register, exact: true }).click()
  await expect(page).toHaveURL(/\/login\?registered=1&returnTo=/)
})
