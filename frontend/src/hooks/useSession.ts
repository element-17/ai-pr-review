import { useSyncExternalStore } from 'react'
import { getSession, readSessionSnapshot, SESSION_EVENT } from '../lib/session'

function subscribe(callback: () => void) {
  window.addEventListener(SESSION_EVENT, callback)
  return () => window.removeEventListener(SESSION_EVENT, callback)
}

export function useSession() {
  useSyncExternalStore(subscribe, readSessionSnapshot)
  return getSession()
}
