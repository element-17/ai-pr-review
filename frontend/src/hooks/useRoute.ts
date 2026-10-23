import { useSyncExternalStore } from 'react'

function subscribe(callback: () => void) {
  window.addEventListener('popstate', callback)
  return () => window.removeEventListener('popstate', callback)
}

export function useRoute() {
  return useSyncExternalStore(subscribe, () => window.location.pathname + window.location.search)
}
