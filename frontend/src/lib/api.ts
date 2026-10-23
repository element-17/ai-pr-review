import type { Session } from './types'
import { getSession, setSession } from './session'

const baseUrl = (import.meta.env.VITE_API_BASE_URL || '/api').replace(/\/$/, '')
// master 后端已实现对应接口，默认开启；仅在显式配置为 'false' 时关闭
export const analysisEnabled = import.meta.env.VITE_ENABLE_ANALYSIS !== 'false'
export const comparisonEnabled = import.meta.env.VITE_ENABLE_COMPARISON !== 'false'
export const deletionEnabled = import.meta.env.VITE_ENABLE_TASK_DELETE !== 'false'

export class ApiError extends Error {
  code: number
  data: Record<string, unknown> | null
  constructor(code: number, message: string, data: Record<string, unknown> | null = null) {
    super(message)
    this.name = 'ApiError'
    this.code = code
    this.data = data
  }
}

interface RequestOptions {
  method?: string
  body?: unknown
  guestToken?: string
  signal?: AbortSignal
  public?: boolean
  raw?: boolean
  idempotencyKey?: string
}

interface Envelope {
  code: number
  message?: string
  data?: unknown
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return !!value && typeof value === 'object' && !Array.isArray(value)
}

let refreshRequest: Promise<void> | null = null

async function refreshSession() {
  if (!refreshRequest) {
    const previous = getSession()
    refreshRequest = (async () => {
      if (!previous?.refreshToken) throw new ApiError(401, 'sessionExpired')
      const session = await request<Session>('/auth/refresh', {
        method: 'POST', body: { refreshToken: previous.refreshToken }, public: true,
      }, false)
      if (getSession()?.refreshToken !== previous.refreshToken) throw new ApiError(401, 'sessionExpired')
      setSession(session)
    })().catch(error => {
      if (getSession()?.refreshToken === previous?.refreshToken) setSession(null)
      if (error instanceof ApiError && error.code === 401) throw new ApiError(401, 'sessionExpired')
      throw error
    }).finally(() => { refreshRequest = null })
  }
  return refreshRequest
}

async function request<T>(path: string, options: RequestOptions = {}, canRefresh = true): Promise<T> {
  const headers = new Headers({ Accept: options.raw ? 'text/markdown, application/json' : 'application/json' })
  const session = getSession()
  if (!options.public && session) headers.set('Authorization', `Bearer ${session.accessToken}`)
  if (options.guestToken) headers.set('X-Guest-Token', options.guestToken)
  if (options.body !== undefined) headers.set('Content-Type', 'application/json')
  if (options.idempotencyKey) headers.set('Idempotency-Key', options.idempotencyKey)
  const signal = options.signal ? AbortSignal.any([options.signal, AbortSignal.timeout(20000)]) : AbortSignal.timeout(20000)
  let response: Response
  let payload: string
  try {
    response = await fetch(`${baseUrl}${path}`, {
      method: options.method || 'GET', headers,
      body: options.body === undefined ? undefined : JSON.stringify(options.body), signal,
    })
    payload = await response.text()
  } catch (error) {
    if (options.signal?.aborted) throw error
    throw new ApiError(0, 'networkError')
  }
  const isJson = response.headers.get('content-type')?.includes('json')
  let envelope: Envelope | null = null
  if (isJson) {
    try {
      const parsed: unknown = JSON.parse(payload)
      if (isRecord(parsed) && typeof parsed.code === 'number') envelope = parsed as unknown as Envelope
    } catch {
      if (response.ok) throw new ApiError(0, 'invalidResponse')
    }
  }
  const code = envelope?.code ?? response.status
  if (code === 401 || response.status === 401) {
    const current = getSession()
    if (!options.public && session && canRefresh && current?.userInfo.id === session.userInfo.id) {
      if (current.accessToken !== session.accessToken) return request<T>(path, options, false)
      await refreshSession()
      return request<T>(path, options, false)
    }
    if (!options.public && session && current?.accessToken === session.accessToken) setSession(null)
    throw new ApiError(401, options.public ? 'invalidCredentials' : 'sessionExpired')
  }
  if (!response.ok || (envelope && code !== 200)) {
    throw new ApiError(
      response.ok ? code : response.status,
      typeof envelope?.message === 'string' ? envelope.message : 'requestFailed',
      isRecord(envelope?.data) ? envelope.data : null,
    )
  }
  if (options.raw && !isJson) return payload as T
  if (options.raw || !envelope || !('data' in envelope)) throw new ApiError(0, 'invalidResponse')
  return envelope.data as T
}

export const api = {
  get: <T>(path: string, options?: RequestOptions) => request<T>(path, options),
  post: <T>(path: string, body?: unknown, options?: RequestOptions) =>
    request<T>(path, { ...options, method: 'POST', body }),
  patch: <T>(path: string, body: unknown, options?: RequestOptions) =>
    request<T>(path, { ...options, method: 'PATCH', body }),
  delete: <T>(path: string) => request<T>(path, { method: 'DELETE' }),
}

export function query(
  values: Record<string, string | number | boolean | undefined>,
) {
  const params = new URLSearchParams()
  Object.entries(values).forEach(([key, value]) => {
    if (value !== undefined && value !== '') params.set(key, String(value))
  })
  return params.size ? `?${params}` : ''
}

export function safeGithubUrl(value: string | null | undefined) {
  try {
    const url = new URL(value || '')
    return url.protocol === 'https:' && url.hostname === 'github.com'
      ? url.href
      : undefined
  } catch {
    return undefined
  }
}

export function normalizePrUrl(value: string) {
  try {
    const url = new URL(value.trim())
    if (
      url.protocol !== 'https:' ||
      url.hostname !== 'github.com' ||
      url.port ||
      url.username ||
      url.password ||
      !/^\/[\w.-]+\/[\w.-]+\/pull\/[1-9]\d*\/?$/.test(url.pathname)
    )
      return null
    return `https://github.com${url.pathname.replace(/\/$/, '')}`
  } catch {
    return null
  }
}
