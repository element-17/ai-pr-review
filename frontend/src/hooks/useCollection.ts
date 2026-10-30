import { useCallback, useEffect, useState } from 'react'
import { api, query } from '../lib/api'
import type { Page } from '../lib/types'

/** 接收可空接口路径、每页条数和游客令牌；加载全部分页，返回合集、错误、加载状态及重载方法。 */
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
    /** 无参数；依次读取分页并更新合集状态，返回 Promise<void>，取消时提前结束。 */
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
  /** 无参数；触发完整集合重新加载，无返回值。 */
  const reload = useCallback(() => setRevision(value => value + 1), [])
  return {
    data: path && result.identity === identity ? result.data : undefined,
    error: path && result.requestKey === requestKey ? result.error : undefined,
    loading: !!path && result.requestKey !== requestKey,
    reload,
  }
}
