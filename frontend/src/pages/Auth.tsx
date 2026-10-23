import { classNames } from '../lib/classNames'
import styles from './Auth.module.css'
import { useState } from 'react'
import type { FormEvent } from 'react'
import { api } from '../lib/api'
import { setSession } from '../lib/session'
import { navigate } from '../hooks'
import { safeReturnPath } from '../lib/routes'
import { usePreferences } from '../lib/preferences'
import type { Session, Settings } from '../lib/types'
import { ErrorNotice, Icon, Link, Notice } from '../components/ui'

export default function Auth({
  register,
  search,
}: {
  register: boolean
  search: string
}) {
  const { t, setPreferences } = usePreferences()
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<unknown>(null)
  const params = new URLSearchParams(search)
  const returnTo = safeReturnPath(params.get('returnTo'))
  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const values = new FormData(event.currentTarget)
    const username = String(values.get('username')).trim()
    const password = String(values.get('password'))
    setBusy(true)
    setError(null)
    try {
      if (register) {
        await api.post(
          '/auth/register',
          {
            username,
            password,
            nickname: values.get('nickname') || undefined,
            email: values.get('email') || undefined,
          },
          { public: true },
        )
        navigate(`/login?registered=1&returnTo=${encodeURIComponent(returnTo)}`)
      } else {
        const session = await api.post<Session>(
          '/auth/login',
          { username, password },
          { public: true },
        )
        setSession(session)
        const settings = await api
          .get<Settings>('/users/me/settings')
          .catch(() => null)
        if (settings)
          setPreferences({ theme: settings.theme, language: settings.language })
        navigate(returnTo)
      }
    } catch (caught) {
      setError(caught)
    } finally {
      setBusy(false)
    }
  }
  return (
    <div className={styles['auth-page']}>
      <div className={classNames('card', styles['auth-card'])}>
        <div className="brand-mark">
          <Icon name="branch" size={28} />
        </div>
        <h1>{t(register ? 'registerTitle' : 'loginTitle')}</h1>
        <p>{t(register ? 'registerDescription' : 'loginDescription')}</p>
        {params.has('registered') && <Notice>{t('registerSuccess')}</Notice>}
        <form onSubmit={submit}>
          <label className="field">
            <span>{t('username')}</span>
            <input
              name="username"
              autoComplete="username"
              required
              minLength={register ? 3 : 1}
              maxLength={50}
              pattern={register ? '[a-zA-Z0-9_]+' : undefined}
            />
          </label>
          <label className="field">
            <span>{t('password')}</span>
            <input
              name="password"
              type="password"
              autoComplete={register ? 'new-password' : 'current-password'}
              required
              minLength={6}
              maxLength={register ? 32 : 64}
            />
          </label>
          {register && (
            <>
              <label className="field">
                <span>{t('nickname')}</span>
                <input name="nickname" maxLength={50} />
              </label>
              <label className="field">
                <span>{t('email')}</span>
                <input name="email" type="email" maxLength={100} />
              </label>
            </>
          )}
          {!!error && <ErrorNotice error={error} />}
          <button
            className={classNames('button', 'primary', 'full-width')}
            disabled={busy}
          >
            {busy ? t('loading') : t(register ? 'register' : 'login')}
            <Icon name="arrow" size={17} />
          </button>
        </form>
        <p className={styles['auth-switch']}>
          {t(register ? 'haveAccount' : 'noAccount')}{' '}
          <Link
            to={`${register ? '/login' : '/register'}?returnTo=${encodeURIComponent(returnTo)}`}
          >
            {t(register ? 'login' : 'register')}
          </Link>
        </p>
      </div>
    </div>
  )
}
