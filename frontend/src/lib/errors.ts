import { ApiError } from './api'
import type { MessageKey } from '../i18n/types'

/** 接收任意错误；返回可展示的提示翻译键，无法识别时返回 requestFailed，避免暴露原始错误。 */
export function errorMessageKey(error: unknown): MessageKey {
  if (!(error instanceof ApiError)) return 'requestFailed'
  const message = error.message
  const known = ['networkError', 'requestFailed', 'invalidResponse', 'sessionExpired', 'invalidCredentials'] as const
  const key = known.find(key => key === message)
  if (key) return key
  const taskCode = error.data?.errorCode
  if (typeof taskCode === 'number' && Number.isInteger(taskCode) && taskCode >= 1 && taskCode <= 8) {
    return `taskError${taskCode}` as MessageKey
  }
  if (/账号.*禁用|account.*disabled/i.test(message)) return 'accountDisabled'
  if (/用户名.*(?:存在|占用)|username.*(?:taken|exist)/i.test(message)) return 'usernameExists'
  if (/游客.*(?:无效|过期|失效)|guest.*(?:invalid|expired)/i.test(message)) return 'guestLinkExpired'
  if (/(?:GitHub.*(?:限流|请求过于频繁|rate.?limit)|rate.?limit.*GitHub)/i.test(message)) return 'taskError2'
  if (/(?:仓库.*(?:无权限|权限不足|无权)|没有.*仓库.*权限|无权访问.*仓库|repository.*(?:denied|permission))/i.test(message)) return 'taskError3'
  if (/(?:PR|pull request).*(?:无效|不存在|invalid|not found)|无效.*(?:PR|链接)/i.test(message)) return 'taskError1'
  if (/(?:模型|model).*(?:超时|timeout|timed out)/i.test(message)) return 'taskError6'
  if (/(?:模型|model).*(?:格式|format|invalid response)/i.test(message)) return 'taskError7'
  if (/(?:Diff).*(?:过大|超过|too large|limit)/i.test(message)) return 'taskError5'
  if (/(?:GitHub).*(?:失败|超时|timeout|failed|timed out)/i.test(message)) return 'taskError4'
  if (error.code === 401) return 'sessionExpired'
  if (error.code === 400) return 'invalidInput'
  if (error.code === 403) return 'forbidden'
  if (error.code === 404) return 'resourceNotFound'
  if (error.code === 409) {
    if (typeof error.data?.existingTaskId === 'number') return 'taskInProgress'
    if (/幂等|idempoten/i.test(message)) return 'idempotencyConflict'
    if (/对比|比较|compare|comparison/i.test(message)) return 'comparisonInvalid'
    return 'stateConflict'
  }
  if (error.code === 429) return 'rateLimited'
  if (error.code >= 500) return 'serverError'
  return 'requestFailed'
}
