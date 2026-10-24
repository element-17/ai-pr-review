import { classNames } from '../lib/classNames'
import styles from './Auth.module.scss'
import { useState } from 'react'
import type { FormEvent } from 'react'
import { api } from '../lib/api'
import { setSession } from '../lib/session'
import { navigate } from '../hooks'
import { safeReturnPath } from '../lib/routes'
import { usePreferences } from '../lib/preferences'
import {
  registrationFields,
  registrationLimits,
  validateRegistration,
  type RegistrationErrors,
  type RegistrationField,
} from '../lib/registration'
import type { MessageKey } from '../i18n/types'
import type { Session, Settings } from '../lib/types'
import { ErrorNotice, Icon, Link, Notice } from '../components/ui'

/** 接收注册模式标记和查询串；返回带校验、错误提示及返回路径的登录或注册页面节点。 */
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
  const [validation, setValidation] = useState<RegistrationErrors>({})
  const params = new URLSearchParams(search)
  const returnTo = safeReturnPath(params.get('returnTo'))
  /** 接收表单事件和首次显示错误标记；按需更新对应注册字段错误，无返回值。 */
  function validateField(event: FormEvent<HTMLFormElement>, reveal = false) {
    const input = event.target
    if (!register || busy || !(input instanceof HTMLInputElement)) return
    setError(null)
    const field = registrationFields.find(field => field === input.name)
    if (!field || (!reveal && !(field in validation) && !(field === 'password' && 'confirmPassword' in validation))) return
    const { errors } = validateRegistration(event.currentTarget)
    setValidation(previous => {
      const next = { ...previous }
      if (reveal || field in previous) next[field] = errors[field]
      if (field === 'password' && 'confirmPassword' in previous) next.confirmPassword = errors.confirmPassword
      return next
    })
  }
  /** 接收注册字段名；返回提示和错误的关联 ID 字符串，登录模式返回 undefined。 */
  function describedBy(field: RegistrationField) {
    return register ? `register-${field}-hint${validation[field] ? ` register-${field}-error` : ''}` : undefined
  }
  /** 接收注册字段名和提示翻译键；返回字段说明与错误节点，登录模式返回 null。 */
  function fieldFeedback(field: RegistrationField, hint: MessageKey) {
    if (!register) return null
    return (
      <>
        <span id={`register-${field}-hint`} className={styles['field-hint']}>{t(hint)}</span>
        {validation[field] && <span id={`register-${field}-error`} className={styles['field-error']} role="alert">{t(validation[field])}</span>}
      </>
    )
  }
  /** 接收表单提交事件；校验后注册或登录并导航，登录时保存会话，返回 Promise<void>。 */
  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (busy) return
    const form = event.currentTarget
    const registration = register ? validateRegistration(form) : null
    setError(null)
    if (registration) {
      setValidation(registration.errors)
      const invalid = registrationFields.find(field => registration.errors[field])
      if (invalid) {
        const input = form.elements.namedItem(invalid)
        if (input instanceof HTMLInputElement) input.focus()
        return
      }
    }
    const values = new FormData(form)
    const username = String(values.get('username')).trim()
    const password = String(values.get('password'))
    setBusy(true)
    try {
      if (registration) {
        const { username, password, nickname, email, phone } = registration.values
        await api.post(
          '/auth/register',
          {
            username,
            password,
            nickname: nickname || undefined,
            email: email || undefined,
            phone: phone || undefined,
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
        {params.has('expired') && <Notice>{t('sessionExpired')}</Notice>}
        <form noValidate={register} onSubmit={submit} onBlur={event => validateField(event, true)} onChange={event => validateField(event)}>
          <fieldset disabled={busy}>
            <label className="field">
              <span id="auth-username-label">{t('username')}</span>
              <input
                name="username"
                aria-labelledby="auth-username-label"
                autoComplete="username"
                required
                minLength={register ? registrationLimits.username.min : 1}
                maxLength={registrationLimits.username.max}
                pattern={register ? '[a-zA-Z0-9_]+' : undefined}
                aria-invalid={register ? !!validation.username : undefined}
                aria-describedby={describedBy('username')}
              />
              {fieldFeedback('username', 'registerUsernameHint')}
            </label>
            <label className="field">
              <span id="auth-password-label">{t('password')}</span>
              <input
                name="password"
                aria-labelledby="auth-password-label"
                type="password"
                autoComplete={register ? 'new-password' : 'current-password'}
                required
                minLength={6}
                maxLength={register ? registrationLimits.password.max : 64}
                aria-invalid={register ? !!validation.password : undefined}
                aria-describedby={describedBy('password')}
              />
              {fieldFeedback('password', 'registerPasswordHint')}
            </label>
            {register && (
              <>
                <label className="field">
                  <span id="auth-confirm-password-label">{t('confirmPassword')}</span>
                  <input
                    name="confirmPassword"
                    aria-labelledby="auth-confirm-password-label"
                    type="password"
                    autoComplete="new-password"
                    required
                    maxLength={registrationLimits.password.max}
                    aria-invalid={!!validation.confirmPassword}
                    aria-describedby={describedBy('confirmPassword')}
                  />
                  {fieldFeedback('confirmPassword', 'registerConfirmPasswordHint')}
                </label>
                <label className="field">
                  <span id="auth-nickname-label">{t('nickname')}</span>
                  <input
                    name="nickname"
                    aria-labelledby="auth-nickname-label"
                    autoComplete="nickname"
                    maxLength={registrationLimits.nickname.max}
                    aria-invalid={!!validation.nickname}
                    aria-describedby={describedBy('nickname')}
                  />
                  {fieldFeedback('nickname', 'nicknameLength')}
                </label>
                <label className="field">
                  <span id="auth-email-label">{t('email')}</span>
                  <input
                    name="email"
                    aria-labelledby="auth-email-label"
                    type="email"
                    autoComplete="email"
                    maxLength={registrationLimits.email.max}
                    aria-invalid={!!validation.email}
                    aria-describedby={describedBy('email')}
                  />
                  {fieldFeedback('email', 'registerEmailHint')}
                </label>
                <label className="field">
                  <span id="auth-phone-label">{t('phoneOptional')}</span>
                  <input
                    name="phone"
                    aria-labelledby="auth-phone-label"
                    type="tel"
                    autoComplete="tel-national"
                    inputMode="tel"
                    maxLength={registrationLimits.phone.max}
                    aria-invalid={!!validation.phone}
                    aria-describedby={describedBy('phone')}
                  />
                  {fieldFeedback('phone', 'registerPhoneHint')}
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
          </fieldset>
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
