import { useEffect, useRef, useState } from 'react'
import { api } from '../lib/api'
import { navigate, safeReturnPath } from '../lib/routes'
import { getSession, setSession } from '../lib/session'
import { usePreferences } from '../lib/preferences'
import type { MessageKey } from '../i18n/types'
import type { Session, Settings } from '../lib/types'
import { ErrorNotice, Link, Notice } from '../components/ui'

const loginErrors = ['STATE_INVALID', 'CANCELED', 'TOKEN_EXCHANGE_FAILED', 'USER_FETCH_FAILED', 'EMAIL_EXISTS', 'ACCOUNT_CONFLICT', 'ACCOUNT_DISABLED', 'FAILED'] as const

/** 接收 OAuth 回调查询串；兑换一次性 ticket 并建立平台登录会话。 */
export default function GitHubCallback({ search }: { search: string }) {
  const { t, setPreferences } = usePreferences()
  const attempted = useRef(false)
  const [error, setError] = useState<unknown>(null)
  // 保存初始回调参数：清除地址栏后，会话更新可能让路由重新读取空查询串。
  const [{ ticket, callbackError }] = useState(() => {
    const params = new URLSearchParams(search)
    return { ticket: params.get('ticket'), callbackError: params.get('githubError') }
  })
  const errorKey = callbackError
    ? loginErrors.includes(callbackError as typeof loginErrors[number])
      ? `githubLoginError${callbackError}` as MessageKey
      : 'githubLoginErrorFAILED'
    : !ticket ? 'githubLoginErrorFAILED' : null

  useEffect(() => {
    window.history.replaceState(window.history.state, '', window.location.pathname)
    if (callbackError) {
      sessionStorage.removeItem('pr-review.github-return-to')
      return
    }
    if (!ticket) {
      sessionStorage.removeItem('pr-review.github-return-to')
      return
    }
    if (attempted.current) return
    attempted.current = true
    void api.post<Session>('/auth/github/exchange', { ticket }, { public: true }).then(session => {
      setSession(session)
      void api.get<Settings>('/users/me/settings').then(settings => {
        if (getSession()?.accessToken === session.accessToken) setPreferences({ theme: settings.theme, language: settings.language })
      }).catch(() => undefined)
      const returnTo = safeReturnPath(sessionStorage.getItem('pr-review.github-return-to'), '/')
      sessionStorage.removeItem('pr-review.github-return-to')
      navigate(returnTo)
    }).catch(caught => {
      sessionStorage.removeItem('pr-review.github-return-to')
      setError(caught)
    })
  }, [callbackError, ticket, setPreferences])

  return (
    <section className="card" aria-live="polite">
      {errorKey ? <>
        <Notice danger>{t(errorKey)}</Notice>
        <Link className="button primary" to="/login">{t('backToLogin')}</Link>
      </> : error ? <>
        <ErrorNotice error={error} />
        <Link className="button primary" to="/login">{t('backToLogin')}</Link>
      </> : <p>{t('githubLoginProcessing')}</p>}
    </section>
  )
}
