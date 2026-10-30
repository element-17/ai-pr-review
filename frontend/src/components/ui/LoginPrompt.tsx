import { usePreferences } from '../../lib/preferences'
import { Empty } from './Empty'
import { Link } from './Link'

/** 无参数；返回保留当前地址作为登录后返回路径的登录提示节点。 */
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
