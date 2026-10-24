import { useSyncExternalStore } from 'react'
import { parseSessionSnapshot, readSessionSnapshot, SESSION_EVENT } from '../lib/session'

/** 接收会话变化回调；返回移除该监听的取消订阅函数。 */
function subscribe(callback: () => void) {
  window.addEventListener(SESSION_EVENT, callback)
  return () => window.removeEventListener(SESSION_EVENT, callback)
}

/** 无参数；订阅会话变化并返回当前 Session，无有效会话时返回 null。 */
export function useSession() {
  const snapshot = useSyncExternalStore(subscribe, readSessionSnapshot)
  return parseSessionSnapshot(snapshot)
}
