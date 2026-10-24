import { useSyncExternalStore } from 'react'
import { parseSessionSnapshot, readSessionSnapshot, SESSION_EVENT } from '../lib/session'

function subscribe(callback: () => void) {
  window.addEventListener(SESSION_EVENT, callback)
  return () => window.removeEventListener(SESSION_EVENT, callback)
}

export function useSession() {
  const snapshot = useSyncExternalStore(subscribe, readSessionSnapshot)
  return parseSessionSnapshot(snapshot)
}
