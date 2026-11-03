import { useState } from 'react'
import { api } from '../../lib/api'
import { useResource } from '../../hooks'
import { usePreferences } from '../../lib/preferences'
import type { MessageKey } from '../../i18n/types'
import type { GitHubBindingStatus, OAuthAuthorize } from '../../lib/types'
import { DateText, ErrorNotice, Icon, Notice, ResourceState } from '../ui'
import styles from './GitHubAccountSettings.module.scss'

const bindErrors = ['ACCOUNT_CONFLICT', 'STATE_INVALID', 'CANCELED', 'TOKEN_EXCHANGE_FAILED', 'USER_FETCH_FAILED', 'FAILED'] as const

/** 接收当前设置页查询串；展示 GitHub 绑定状态并提供授权、密码和解绑操作。 */
export default function GitHubAccountSettings({ search }: { search: string }) {
  const { t } = usePreferences()
  const binding = useResource<GitHubBindingStatus>('/auth/github/binding')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<unknown>(null)
  const [passwordMismatch, setPasswordMismatch] = useState(false)
  const [message, setMessage] = useState<'passwordSaved' | 'bindingRemoved' | null>(null)
  const params = new URLSearchParams(search)
  const bindResult = params.get('githubBind') === 'SUCCESS' ? 'githubBindSuccess' : null
  const bindError = params.get('githubBindError')
  const bindErrorKey = bindError && bindErrors.includes(bindError as typeof bindErrors[number])
    ? `githubBindError${bindError}` as MessageKey
    : bindError ? 'githubBindErrorFAILED' : null
  const status = binding.data

  async function startBinding() {
    setBusy(true)
    setError(null)
    try {
      const result = await api.get<OAuthAuthorize>('/auth/github/bind/authorize')
      window.location.assign(result.authorizeUrl)
    } catch (caught) {
      setError(caught)
      setBusy(false)
    }
  }

  async function savePassword(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const form = event.currentTarget
    const data = new FormData(form)
    const newPassword = String(data.get('newPassword') || '')
    const confirmPassword = String(data.get('confirmPassword') || '')
    if (newPassword !== confirmPassword) {
      setPasswordMismatch(true)
      setError(null)
      return
    }
    setPasswordMismatch(false)
    setBusy(true)
    setError(null)
    setMessage(null)
    try {
      await api.put('/users/me/password', {
        currentPassword: status?.passwordSet ? String(data.get('currentPassword') || '') : undefined,
        newPassword,
        confirmPassword,
      })
      form.reset()
      setMessage('passwordSaved')
      binding.reload()
    } catch (caught) {
      setError(caught)
    } finally {
      setBusy(false)
    }
  }

  async function unbind() {
    if (busy || !status?.bound || !status.passwordSet || !window.confirm(t('githubUnbindConfirm'))) return
    setBusy(true)
    setError(null)
    setMessage(null)
    try {
      await api.delete('/auth/github/binding')
      setMessage('bindingRemoved')
      binding.reload()
    } catch (caught) {
      setError(caught)
    } finally {
      setBusy(false)
    }
  }

  return (
    <section className="card" id="settings-github" tabIndex={-1} aria-labelledby="github-settings-heading">
      <header className={styles.heading}>
        <div>
          <h2 id="github-settings-heading">{t('githubBinding')}</h2>
          <p>{t('githubBindingDescription')}</p>
        </div>
        {status?.bound && <span className={status.tokenStatus === 1 ? styles.valid : styles.invalid}>
          {t(status.tokenStatus === 1 ? 'githubTokenValid' : status.tokenStatus === 2 ? 'githubTokenInvalid' : 'githubTokenRevoked')}
        </span>}
      </header>
      {bindResult && <Notice>{t(bindResult)}</Notice>}
      {bindErrorKey && <Notice danger>{t(bindErrorKey)}</Notice>}
      <ResourceState loading={binding.loading} error={binding.error} retry={binding.reload} />
      {status && (
        <div className={styles.content}>
          {status.bound ? (
            <div className={styles.identity}>
              {status.avatarUrl && <img src={status.avatarUrl} alt="" width="44" height="44" />}
              <div>
                <strong>@{status.githubLogin}</strong>
                {status.email && <span>{status.email}</span>}
                {status.authorizedAt && <span>{t('githubAuthorizedAt')}: <DateText value={status.authorizedAt} /></span>}
              </div>
              <button className="button small" type="button" disabled={busy || !status.passwordSet} onClick={() => void unbind()}>
                {t('githubUnbind')}
              </button>
            </div>
          ) : (
            <div className={styles.unbound}>
              <p>{t('githubNotBound')}</p>
              <button className="button primary" type="button" disabled={busy} onClick={() => void startBinding()}>
                <Icon name="branch" size={16} />{busy ? t('loading') : t('githubBind')}
              </button>
            </div>
          )}
          {status.bound && !status.passwordSet && <Notice>{t('githubPasswordRequired')}</Notice>}
          <form id="settings-password" tabIndex={-1} className={styles.passwordForm} onSubmit={savePassword}>
            <h3>{t(status.passwordSet ? 'changePassword' : 'setPassword')}</h3>
            {status.passwordSet && <label className="field">
              <span>{t('currentPassword')}</span>
              <input name="currentPassword" type="password" autoComplete="current-password" required maxLength={64} />
            </label>}
            <div className={styles.passwordFields}>
              <label className="field">
                <span>{t('newPassword')}</span>
                <input name="newPassword" type="password" autoComplete="new-password" required minLength={6} maxLength={32} />
              </label>
              <label className="field">
                <span>{t('confirmPassword')}</span>
                <input name="confirmPassword" type="password" autoComplete="new-password" required minLength={6} maxLength={32} />
              </label>
            </div>
            <button className="button" type="submit" disabled={busy}>{busy ? t('saving') : t('savePassword')}</button>
          </form>
          {message && <Notice>{t(message)}</Notice>}
          {passwordMismatch && <Notice danger>{t('passwordMismatch')}</Notice>}
          {!!error && <ErrorNotice error={error} />}
        </div>
      )}
    </section>
  )
}
