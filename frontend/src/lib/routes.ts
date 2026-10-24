import type { MessageKey } from '../i18n/types'
import { getGuestAccess } from './guestTasks'

export type WorkspaceTab = 'overview' | 'files' | 'issues' | 'compare'

export function positiveInteger(value: string | null | undefined) {
  if (!value || !/^[1-9]\d*$/.test(value)) return undefined
  const number = Number(value)
  return Number.isSafeInteger(number) ? number : undefined
}

// 只允许站内相对路径，拒绝协议相对地址、反斜杠以及控制字符/空白
function hasUnsafeChar(value: string) {
  for (const char of value) {
    if (char === '\\' || char.charCodeAt(0) <= 0x20) return true
  }
  return false
}

export function safeReturnPath(value: string | null, fallback = '/tasks') {
  if (!value || !value.startsWith('/') || value.startsWith('//') || hasUnsafeChar(value)) return fallback
  return value
}

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

export function navigate(path: string) {
  const target = safeReturnPath(path, '/')
  window.history.pushState(null, '', target)
  window.dispatchEvent(new PopStateEvent('popstate'))
  window.scrollTo({ top: 0 })
}
