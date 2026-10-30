import { useSyncExternalStore } from 'react'

/** 接收路由变化回调；监听 popstate 并返回取消订阅函数。 */
function subscribe(callback: () => void) {
  window.addEventListener('popstate', callback)
  return () => window.removeEventListener('popstate', callback)
}

/** 无参数；订阅路由变化并返回当前路径与查询串拼接的字符串。 */
export function useRoute() {
  return useSyncExternalStore(subscribe, () => window.location.pathname + window.location.search)
}
