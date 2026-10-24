import { useCallback, useEffect, useState } from 'react'
import { api } from '../lib/api'
import type { Progress } from '../lib/types'

/** 接收任务 ID、运行标记、游客令牌和完成回调；返回轮询进度、错误及重试方法。 */
export function useTaskProgress(taskId: number, running: boolean, guestToken: string, onComplete: () => void) {
  const [revision, setRevision] = useState(0)
  const [result, setResult] = useState<{ taskId: number; progress?: Progress; error?: unknown }>({ taskId })
  useEffect(() => {
    if (!running) return
    const controller = new AbortController()
    let timer: ReturnType<typeof setTimeout> | undefined
    /** 无参数；更新进度，运行中安排下一轮，终态调用完成回调，返回 Promise<void>。 */
    async function poll() {
      try {
        const progress = await api.get<Progress>(`/tasks/${taskId}/progress`, { guestToken, signal: controller.signal })
        if (controller.signal.aborted) return
        setResult({ taskId, progress })
        if (![1, 2].includes(progress.status)) { onComplete(); return }
        timer = setTimeout(poll, 2000)
      } catch (error) {
        if (!controller.signal.aborted) setResult(previous => ({ ...previous, taskId, error }))
      }
    }
    void poll()
    return () => { controller.abort(); clearTimeout(timer) }
  }, [taskId, running, guestToken, onComplete, revision])
  /** 无参数；清除当前结果并重新启动进度请求，无返回值。 */
  const retry = useCallback(() => {
    setResult({ taskId })
    setRevision(value => value + 1)
  }, [taskId])
  return { progress: result.taskId === taskId ? result.progress : undefined, error: result.taskId === taskId ? result.error : undefined, retry }
}
