import { test, expect } from './fixtures.mjs'

async function signIn(page) {
  const response = await page.request.post('/api/auth/login', { data: { username: 'demo', password: 'demo123456' } })
  const { data: session } = await response.json()
  await page.addInitScript(session => sessionStorage.setItem('pr-review.session', JSON.stringify(session)), session)
  return session
}

test('用户可读取并修改自己的资料', async ({ page }) => {
  const session = await signIn(page)
  let profile = { ...session.userInfo, email: 'old@example.com', phone: '', status: 1 }
  let patch
  await page.route('**/api/users/me/profile', async route => {
    if (route.request().method() === 'PATCH') {
      patch = route.request().postDataJSON()
      profile = { ...profile, ...patch }
    }
    await route.fulfill({ json: { code: 200, data: profile } })
  })
  await page.goto('/settings')
  const section = page.locator('#settings-profile')
  await section.locator('[name=nickname]').fill('新昵称')
  await section.locator('[name=email]').fill('new@example.com')
  await section.getByRole('button', { name: '保存更改', exact: true }).click()
  await expect(section.getByText('更改已保存')).toBeVisible()
  expect(patch).toMatchObject({ nickname: '新昵称', email: 'new@example.com' })
  expect(patch).not.toHaveProperty('role')
  expect(await page.evaluate(() => JSON.parse(sessionStorage.getItem('pr-review.session')).userInfo.nickname)).toBe('新昵称')
})

test('管理员可以搜索、编辑和更改账号状态', async ({ page }) => {
  await signIn(page)
  const user = { id: 2, username: 'person', nickname: 'User', email: 'person@example.com', phone: '', avatar: '', role: 'USER', status: 1 }
  const requests = []
  page.on('dialog', dialog => dialog.accept())
  await page.route('**/api/users/**', async route => {
    const request = route.request(), url = new URL(request.url())
    requests.push([request.method(), url.pathname, url.search])
    let data = null
    if (url.pathname.endsWith('/stats/roles')) data = [{ role: 'USER', total: 1 }]
    else if (url.pathname.endsWith('/page') || url.pathname.endsWith('/search')) data = { total: 1, records: [user] }
    else if (request.method() === 'GET') data = user
    await route.fulfill({ json: { code: 200, data } })
  })
  await page.goto('/admin/users')
  await expect(page.getByRole('heading', { name: '用户管理' })).toBeVisible()
  await page.getByLabel('搜索用户名、昵称或邮箱').fill('person')
  await page.getByRole('button', { name: '搜索', exact: true }).click()
  await expect.poll(() => requests.some(([, path]) => path.endsWith('/users/search'))).toBe(true)
  await page.getByRole('button', { name: '编辑用户', exact: true }).first().click()
  await expect(page.locator('#user-editor').getByLabel('昵称')).toHaveValue('User')
  await page.locator('#user-editor').getByLabel('昵称').fill('Updated')
  await page.locator('#user-editor').getByRole('button', { name: '保存更改', exact: true }).click()
  await expect.poll(() => requests.some(([method, path]) => method === 'PUT' && path.endsWith('/users/2'))).toBe(true)
  await page.getByRole('button', { name: '禁用', exact: true }).first().click()
  await expect.poll(() => requests.some(([method, path, search]) => method === 'PUT' && path.endsWith('/users/2/status') && search === '?status=0')).toBe(true)
})
