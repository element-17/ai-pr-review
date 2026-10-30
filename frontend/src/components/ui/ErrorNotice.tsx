import { usePreferences } from '../../lib/preferences'
import { errorMessageKey } from '../../lib/errors'
import { Notice } from './Notice'
import { Link } from './Link'

/** 接收错误和可选重试回调；返回本地化错误提示及适用的恢复操作节点。 */
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
