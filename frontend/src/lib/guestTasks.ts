import { parseServerDate } from './format'
import type { CreatedTask } from './types'

const storageKey = 'pr-review.guest-tasks'
interface GuestAccess {
  token: string
  expireTime: string | null
}

function readTasks(): Record<string, GuestAccess> {
  try {
    const value: unknown = JSON.parse(sessionStorage.getItem(storageKey) || '{}')
    return value && typeof value === 'object' && !Array.isArray(value)
      ? value as Record<string, GuestAccess> : {}
  } catch { return {} }
}

export function getGuestAccess(taskId: number): GuestAccess | null {
  const access = readTasks()[taskId]
  if (!access || typeof access.token !== 'string' || !access.token) return null
  const expireTime = typeof access.expireTime === 'string' ? access.expireTime : null
  if (expireTime && parseServerDate(expireTime).getTime() <= Date.now()) return null
  return { token: access.token, expireTime }
}

export function rememberGuestTask(task: Pick<CreatedTask, 'taskId' | 'guestToken' | 'expireTime'>) {
  if (!task.guestToken || !Number.isSafeInteger(task.taskId) || task.taskId <= 0) return
  try {
    const tasks = readTasks()
    const previous = tasks[task.taskId]
    tasks[task.taskId] = {
      token: task.guestToken,
      expireTime: task.expireTime || (previous?.token === task.guestToken ? previous.expireTime : null),
    }
    sessionStorage.setItem(storageKey, JSON.stringify(tasks))
  } catch { /* A result link still works when browser storage is unavailable. */ }
}
