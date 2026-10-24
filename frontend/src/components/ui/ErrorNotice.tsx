import { usePreferences } from '../../lib/preferences'
import { errorMessageKey } from '../../lib/errors'
import { Notice } from './Notice'
import { Link } from './Link'

export function ErrorNotice({
  error,
  retry,
}: {
  error: unknown
  retry?: () => void
}) {
  const { t } = usePreferences()
  const key = errorMessageKey(error)
  return (
    <Notice danger>
      <strong>{t('errorTitle')}</strong>
      <p>{t(key)}</p>
      {key === 'sessionExpired' && location.pathname !== '/login' && (
        <Link className="button small" to={`/login?returnTo=${encodeURIComponent(location.pathname + location.search)}`}>{t('login')}</Link>
      )}
      {key === 'guestLinkExpired' && <Link className="button small" to="/">{t('openTask')}</Link>}
      {retry && (
        <button className="button small" onClick={retry}>
          {t('retry')}
        </button>
      )}
    </Notice>
  )
}
