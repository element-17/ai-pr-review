import type { Session } from './types'

export const SESSION_KEY = 'pr-review.session'
export const SESSION_EVENT = 'session-change'

/** 无参数；读取当前标签页的会话 JSON，缺失或存储不可用时返回 null。 */
export function readSessionSnapshot() {
  try {
    return sessionStorage.getItem(SESSION_KEY)
  } catch {
    return null
  }
}

/** 无参数；返回当前标签页中校验后的会话，无有效会话时返回 null。 */
export function getSession(): Session | null {
  return parseSessionSnapshot(readSessionSnapshot())
}

/** 接收可空会话 JSON；校验并补全默认字段，返回 Session 或 null。 */
export function parseSessionSnapshot(snapshot: string | null): Session | null {
  try {
    const value: unknown = JSON.parse(snapshot || 'null')
    if (!value || typeof value !== 'object') return null
    const session = value as Partial<Session>
    const user = session.userInfo
    if (
      typeof session.accessToken !== 'string' || !session.accessToken ||
      typeof session.refreshToken !== 'string' || !session.refreshToken ||
      !user || typeof user !== 'object' ||
      !Number.isSafeInteger(user.id) || user.id <= 0 ||
      typeof user.username !== 'string' || !user.username
    ) return null
    return {
      accessToken: session.accessToken,
      refreshToken: session.refreshToken,
      tokenType: 'Bearer',
      expiresIn: typeof session.expiresIn === 'number' ? session.expiresIn : 0,
      userInfo: {
        id: user.id,
        username: user.username,
        nickname: typeof user.nickname === 'string' ? user.nickname : null,
        avatar: typeof user.avatar === 'string' ? user.avatar : null,
        role: typeof user.role === 'string' ? user.role : 'USER',
      },
    }
  } catch {
    return null
  }
}

/** 接收会话或 null；保存或清除会话并通知订阅者，无返回值。 */
export function setSession(session: Session | null) {
  if (session) sessionStorage.setItem(SESSION_KEY, JSON.stringify(session))
  else sessionStorage.removeItem(SESSION_KEY)
  window.dispatchEvent(new Event(SESSION_EVENT))
}
