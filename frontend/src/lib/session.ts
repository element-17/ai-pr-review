import type { Session } from './types'

export const SESSION_KEY = 'pr-review.session'
export const SESSION_EVENT = 'session-change'

export function readSessionSnapshot() {
  try {
    return sessionStorage.getItem(SESSION_KEY)
  } catch {
    return null
  }
}

export function getSession(): Session | null {
  try {
    const value: unknown = JSON.parse(readSessionSnapshot() || 'null')
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

export function setSession(session: Session | null) {
  if (session) sessionStorage.setItem(SESSION_KEY, JSON.stringify(session))
  else sessionStorage.removeItem(SESSION_KEY)
  window.dispatchEvent(new Event(SESSION_EVENT))
}
