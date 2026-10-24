import { useCallback, useEffect, useState } from 'react'
import { api, query } from '../lib/api'
import type { Page } from '../lib/types'

// Load every metadata page so selectors also include older tasks and large PRs.
export function useCollection<T>(path: string | null, pageSize: number, guestToken = '') {
  const [revision, setRevision] = useState(0)
  const identity = JSON.stringify([path, pageSize, guestToken])
  const requestKey = `${identity}:${revision}`
  const [result, setResult] = useState<{
    identity: string; requestKey: string; data?: Page<T>; error?: unknown
  }>({ identity: '', requestKey: '' })
  useEffect(() => {
    if (!path) return
    const controller = new AbortController()
    const collectionPath = path
    async function load() {
      const records: T[] = []
      let total: number
      for (let page = 1; ; page++) {
        const separator = collectionPath.includes('?') ? '&' : '?'
        const batch = await api.get<Page<T>>(`${collectionPath}${separator}${query({ page, pageSize }).slice(1)}`, {
          guestToken, signal: controller.signal,
        })
        if (controller.signal.aborted) return
        total = batch.total
        records.push(...batch.records)
        if (records.length >= total || !batch.records.length) break
      }
      setResult({ identity, requestKey, data: { records, total } })
    }
    void load().catch(error => {
      if (!controller.signal.aborted) setResult(previous => ({
        identity, requestKey, error,
        data: previous.identity === identity ? previous.data : undefined,
      }))
    })
    return () => controller.abort()
  }, [path, pageSize, guestToken, identity, requestKey])
  const reload = useCallback(() => setRevision(value => value + 1), [])
  return {
    data: path && result.identity === identity ? result.data : undefined,
    error: path && result.requestKey === requestKey ? result.error : undefined,
    loading: !!path && result.requestKey !== requestKey,
    reload,
  }
}
