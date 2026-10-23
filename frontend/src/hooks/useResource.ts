import { useCallback, useEffect, useState } from 'react'
import { api } from '../lib/api'

export function useResource<T>(path: string | null, guestToken = '') {
  const [revision, setRevision] = useState(0)
  const identity = JSON.stringify([path, guestToken])
  const requestKey = `${identity}:${revision}`
  const [result, setResult] = useState<{
    identity: string; requestKey: string; data?: T; error?: unknown
  }>({ identity: '', requestKey: '' })
  useEffect(() => {
    if (!path) return
    const controller = new AbortController()
    api.get<T>(path, { guestToken, signal: controller.signal }).then(
      data => {
        if (!controller.signal.aborted) setResult({ identity, requestKey, data })
      },
      error => {
        if (!controller.signal.aborted) setResult(previous => ({
          identity, requestKey, error,
          data: previous.identity === identity ? previous.data : undefined,
        }))
      },
    )
    return () => controller.abort()
  }, [path, guestToken, identity, requestKey])
  const reload = useCallback(() => setRevision(value => value + 1), [])
  return {
    data: path && result.identity === identity ? result.data : undefined,
    error: path && result.requestKey === requestKey ? result.error : undefined,
    loading: !!path && result.requestKey !== requestKey,
    reload,
  }
}
