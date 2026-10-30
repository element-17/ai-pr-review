import type { Session } from './types'
import { getSession, setSession } from './session'
import { navigate } from './routes'

const baseUrl = (import.meta.env.VITE_API_BASE_URL || '/api').replace(/\/$/, '')
// master 后端已实现对应接口，默认开启；仅在显式配置为 'false' 时关闭
export const analysisEnabled = import.meta.env.VITE_ENABLE_ANALYSIS !== 'false'
export const comparisonEnabled = import.meta.env.VITE_ENABLE_COMPARISON !== 'false'
export const deletionEnabled = import.meta.env.VITE_ENABLE_TASK_DELETE !== 'false'

export class ApiError extends Error {
  code: number
  data: Record<string, unknown> | null
  /** 接收错误码、消息和可选附加数据；创建 ApiError 实例。 */
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

/** 接收任意值；返回它是否为非空、非数组对象。 */
function isRecord(value: unknown): value is Record<string, unknown> {
  return !!value && typeof value === 'object' && !Array.isArray(value)
}

let refreshRequest: Promise<void> | null = null

/** 接收失效前的会话；若会话未更换则清除并跳转登录，无返回值。 */
function expireSession(previous: Session | null) {
  if (getSession()?.refreshToken !== previous?.refreshToken) return
  setSession(null)
  if (!['/login', '/register'].includes(location.pathname)) {
    const returnTo = location.pathname + location.search
    navigate(`/login?expired=1&returnTo=${encodeURIComponent(returnTo)}`)
  }
}

/** 无参数；合并并发刷新并保存新会话，返回 Promise<void>，失败时拒绝。 */
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
      expireSession(previous)
      if (error instanceof ApiError && error.code === 401) throw new ApiError(401, 'sessionExpired')
      throw error
    }).finally(() => { refreshRequest = null })
  }
  return refreshRequest
}

/** 接收接口路径、请求选项和刷新许可；返回数据 Promise<T>，请求失败时拒绝。 */
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
    if (!options.public && (!session || current?.accessToken === session.accessToken)) expireSession(session)
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
  /** 接收接口路径和可选请求配置；返回响应数据 Promise<T>。 */
  get: <T>(path: string, options?: RequestOptions) => request<T>(path, options),
  /** 接收接口路径、可选请求体及配置；返回 POST 响应数据 Promise<T>。 */
  post: <T>(path: string, body?: unknown, options?: RequestOptions) =>
    request<T>(path, { ...options, method: 'POST', body }),
  /** 接收接口路径、请求体和可选配置；返回 PATCH 响应数据 Promise<T>。 */
  patch: <T>(path: string, body: unknown, options?: RequestOptions) =>
    request<T>(path, { ...options, method: 'PATCH', body }),
  /** 接收接口路径；返回 DELETE 响应数据 Promise<T>。 */
  delete: <T>(path: string) => request<T>(path, { method: 'DELETE' }),
}

/** 接收查询参数对象；忽略 undefined 和空字符串，返回带问号的查询串或空串。 */
export function query(
  values: Record<string, string | number | boolean | undefined>,
) {
  const params = new URLSearchParams()
  Object.entries(values).forEach(([key, value]) => {
    if (value !== undefined && value !== '') params.set(key, String(value))
  })
  return params.size ? `?${params}` : ''
}

/** 接收可空 URL；返回 HTTPS GitHub 地址，无效时返回 undefined。 */
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

/** 接收 PR 链接字符串；返回去除查询串和末尾斜杠的合法 GitHub PR 地址，否则返回 null。 */
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
