import { ApiError } from '../../lib/api'
import { usePreferences } from '../../lib/preferences'
import type { MessageKey } from '../../i18n/types'
import { Notice } from './Notice'

export function ErrorNotice({
  error,
  retry,
}: {
  error: unknown
  retry?: () => void
}) {
  const { t } = usePreferences()
  const known = [
    'networkError',
    'requestFailed',
    'invalidResponse',
    'sessionExpired',
    'invalidCredentials',
  ]
  let key: MessageKey = 'requestFailed'
  if (error instanceof ApiError) {
    if (known.includes(error.message)) key = error.message as MessageKey
    else if (error.message.includes('账号已被禁用')) key = 'accountDisabled'
    else if (error.message.includes('用户名已存在')) key = 'usernameExists'
    else if (error.code === 400) key = 'invalidInput'
    else if (error.code === 403) key = 'forbidden'
    else if (error.code === 404) key = 'resourceNotFound'
    else if (error.code === 429) key = 'rateLimited'
    else if (error.code >= 500) key = 'serverError'
  }
  return (
    <Notice danger>
      <strong>{t('errorTitle')}</strong>
      <p>{t(key)}</p>
      {retry && (
        <button className="button small" onClick={retry}>
          {t('retry')}
        </button>
      )}
    </Notice>
  )
}
