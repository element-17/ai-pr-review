import { parseServerDate } from './format'
import type { CreatedTask } from './types'

const storageKey = 'pr-review.guest-tasks'
interface GuestAccess {
  token: string
  expireTime: string | null
}

/** 无参数；返回当前标签页保存的游客凭证映射，读取失败时返回空对象。 */
function readTasks(): Record<string, GuestAccess> {
  try {
    const value: unknown = JSON.parse(sessionStorage.getItem(storageKey) || '{}')
    return value && typeof value === 'object' && !Array.isArray(value)
      ? value as Record<string, GuestAccess> : {}
  } catch { return {} }
}

/** 接收任务 ID；返回有效的已保存游客凭证，缺失、格式错误或已知过期时返回 null。 */
export function getGuestAccess(taskId: number): GuestAccess | null {
  const access = readTasks()[taskId]
  if (!access || typeof access.token !== 'string' || !access.token) return null
  const expireTime = typeof access.expireTime === 'string' ? access.expireTime : null
  if (expireTime && parseServerDate(expireTime).getTime() <= Date.now()) return null
  return { token: access.token, expireTime }
}

/** 接收任务 ID、游客令牌和可选到期时间；在当前标签页保存有效凭证，无返回值。 */
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
