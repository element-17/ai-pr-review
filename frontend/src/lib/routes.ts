import type { MessageKey } from '../i18n/types'
import { getGuestAccess } from './guestTasks'

export type WorkspaceTab = 'overview' | 'files' | 'issues' | 'compare'

/** 接收可空数字字符串；返回安全正整数，格式或范围无效时返回 undefined。 */
export function positiveInteger(value: string | null | undefined) {
  if (!value || !/^[1-9]\d*$/.test(value)) return undefined
  const number = Number(value)
  return Number.isSafeInteger(number) ? number : undefined
}

/** 接收路径字符串；返回是否含反斜杠、ASCII 控制字符或空格。 */
function hasUnsafeChar(value: string) {
  for (const char of value) {
    if (char === '\\' || char.charCodeAt(0) <= 0x20) return true
  }
  return false
}

/** 接收可空返回路径和默认路径；返回合法站内路径，无效时返回默认路径。 */
export function safeReturnPath(value: string | null, fallback = '/tasks') {
  if (!value || !value.startsWith('/') || value.startsWith('//') || hasUnsafeChar(value)) return fallback
  return value
}

/** 接收含查询串的站内地址；返回页面、任务、子项、游客凭证和标题等路由信息。 */
export function parseRoute(value: string) {
  const separator = value.indexOf('?')
  const path = separator < 0 ? value : value.slice(0, separator)
  const pathname = path === '/' ? '/' : path.replace(/\/$/, '')
  const search = separator < 0 ? '' : value.slice(separator + 1)
  const match = /^\/tasks\/([1-9]\d*)(?:\/(files|issues|compare)(?:\/([1-9]\d*))?)?$/.exec(pathname)
  let taskId = positiveInteger(match?.[1])
  const tab = (match?.[2] || 'overview') as WorkspaceTab
  const childId = positiveInteger(match?.[3])
  if (match?.[3] && (!childId || tab === 'compare')) taskId = undefined
  const titles: Record<string, MessageKey> = {
    '/': 'home', '/tasks': 'tasks', '/settings': 'settings', '/login': 'login', '/register': 'register',
  }
  return {
    pathname, search, taskId, tab,
    fileId: tab === 'files' ? childId : undefined,
    issueId: tab === 'issues' ? childId : undefined,
    guestToken: new URLSearchParams(search).get('guestToken') || (taskId ? getGuestAccess(taskId)?.token : '') || '',
    pageTitle: taskId ? tab : titles[pathname] || 'notFound',
  }
}

/** 接收站内目标路径；更新浏览器历史、触发路由刷新并滚动到顶部，无返回值。 */
export function navigate(path: string) {
  const target = safeReturnPath(path, '/')
  window.history.pushState(null, '', target)
  window.dispatchEvent(new PopStateEvent('popstate'))
  window.scrollTo({ top: 0 })
}
