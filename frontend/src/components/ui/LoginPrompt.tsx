import { usePreferences } from '../../lib/preferences'
import { Empty } from './Empty'
import { Link } from './Link'

export function LoginPrompt() {
  const { t } = usePreferences()
  return (
    <Empty title="loginRequired" description="loginHint">
      <Link
        className="button primary"
        to={`/login?returnTo=${encodeURIComponent(location.pathname + location.search)}`}
      >
        {t('login')}
      </Link>
    </Empty>
  )
}
