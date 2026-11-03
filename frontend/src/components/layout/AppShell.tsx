import { classNames } from '../../lib/classNames'
import styles from './AppShell.module.scss'
import { useState, type ReactNode } from 'react'
import type { MessageKey } from '../../i18n/types'
import type { User } from '../../lib/types'
import { usePreferences, type PreferencesUpdate } from '../../lib/preferences'
import ChangeLanguage from '../common/ChangeLanguage'
import UserAvatar from '../account/UserAvatar'
import UserProfileDialog from '../account/UserProfileDialog'
import OnboardingTour from '../onboarding/OnboardingTour'
import { ErrorNotice, Icon, Link } from '../ui'
import { navigate } from '../../hooks'

interface AppShellProps {
  pageTitle: MessageKey
  taskPage: boolean
  user: User | null
  returnTo: string
  error: unknown
  logoutBusy: boolean
  appearanceBusy: boolean
  onDismissError: () => void
  onLogout: () => Promise<void>
  onAppearanceChange: (values: PreferencesUpdate) => Promise<void>
  children: ReactNode
}

/** 接收页面、会话、操作状态及回调和子节点（AppShellProps）；返回导航、顶栏与内容外壳。 */
export default function AppShell({
  pageTitle,
  taskPage,
  user,
  returnTo,
  error,
  logoutBusy,
  appearanceBusy,
  onDismissError,
  onLogout,
  onAppearanceChange,
  children,
}: AppShellProps) {
  const { t, resolvedTheme } = usePreferences()
  const tourPage = taskPage ? 'review' : pageTitle === 'tasks' ? 'tasks' : pageTitle === 'settings' ? 'settings' : 'home'
  const tourAudience = user ? `user:${user.id}` : 'guest'
  const guestReview = !!new URLSearchParams(returnTo.split('?')[1]).get('guestToken')
  const [profileUserId, setProfileUserId] = useState<number | null>(null)
  if (profileUserId !== null && profileUserId !== user?.id) setProfileUserId(null)
  const profileOpen = !!user && profileUserId === user.id
  function goBack() {
    const state = window.history.state as { __app?: boolean; index?: number } | null
    if (state?.__app && typeof state.index === 'number' && state.index > 0) {
      window.history.back()
      return
    }
    navigate('/')
  }
  const userSummary = (
    <>
      <UserAvatar user={user} className={styles['user-avatar']} />
      <span className={styles['user-info']}>
        <strong>{user?.nickname || user?.username || t('guest')}</strong>
        <span>{user ? user.role : 'PR Review'}</span>
      </span>
    </>
  )
  const nav = user ? [
    { to: '/', title: 'home', icon: 'plus' },
    { to: '/tasks', title: 'tasks', icon: 'history' },
    { to: '/analytics', title: 'analytics', icon: 'compare' },
    { to: '/settings', title: 'settings', icon: 'settings' },
    ...(user.role === 'ADMIN' ? [{ to: '/admin/models', title: 'modelAdminTitle', icon: 'settings' } as const] : []),
    ...(user.role === 'ADMIN' ? [{ to: '/admin/users', title: 'userAdminTitle', icon: 'settings' } as const] : []),
  ] as const : [{ to: '/', title: 'guestHome', icon: 'globe' }] as const
  return (
    <div className={styles['app-shell']}>
      <a className={styles['skip-link']} href="#main-content">
        {t('workspace')}
      </a>
      <aside className={styles['sidebar']}>
        <Link to="/" className={styles['brand']}>
          <span className="brand-mark">
            <Icon name="branch" size={24} />
          </span>
          <span>
            PR Review
            <span className={styles['brand-caption']}>{t('platform')}</span>
          </span>
        </Link>
        <div className={styles['sidebar-label']}>{t('workspace')}</div>
        <nav className={styles['main-nav']}>
          {nav.map((item) => (
            <Link
              key={item.to}
              to={item.to}
              data-tour={`nav-${item.title}`}
              aria-label={t(item.title)}
              title={t(item.title)}
              aria-current={
                item.title === pageTitle || (taskPage && item.title === 'tasks')
                  ? 'page'
                  : undefined
              }
            >
              <Icon name={item.icon} size={19} />
              {t(item.title)}
              {item.title === 'home' && (
                <span className={styles['nav-plus']}>+</span>
              )}
            </Link>
          ))}
        </nav>
        <div className={styles['sidebar-bottom']}>
          <div className={styles['sidebar-imprint']} aria-hidden="true">
            <span>LESS NOISE.</span>
            <span>
              BETTER CODE<span className={styles['imprint-period']}>.</span>
            </span>
          </div>
          <div className={styles['sidebar-note']}>
            <Icon name="shield" size={21} />
            <p>{t('connection')}</p>
          </div>
          {user ? (
            <button
              type="button"
              className={classNames(styles['user-summary'], styles['profile-trigger'])}
              aria-label={t('viewProfile')}
              title={t('viewProfile')}
              aria-haspopup="dialog"
              aria-controls="user-profile-dialog"
              aria-expanded={profileOpen}
              onClick={() => setProfileUserId(user.id)}
            >
              {userSummary}
            </button>
          ) : <div className={styles['user-summary']}>{userSummary}</div>}
        </div>
        {user && (
          <button
            type="button"
            className={styles['mobile-profile']}
            aria-label={t('viewProfile')}
            title={t('viewProfile')}
            aria-haspopup="dialog"
            aria-controls="user-profile-dialog"
            aria-expanded={profileOpen}
            onClick={() => setProfileUserId(user.id)}
          >
            <UserAvatar user={user} className={styles['user-avatar']} />
          </button>
        )}
      </aside>
      <div className={styles['main-shell']}>
        <header className={styles['topbar']}>
          <div className={styles['topbar-heading']}>
            <button
              type="button"
              className="icon-button"
              aria-label={t('back')}
              title={t('back')}
              onClick={goBack}
            >
              <Icon name="back" size={18} />
            </button>
            <span className={styles['topbar-title']}>{t(pageTitle)}</span>
          </div>
          <div className={styles['topbar-actions']}>
            <OnboardingTour
              audience={tourAudience}
              page={tourPage}
              guest={!user || (taskPage && guestReview)}
            />
            <ChangeLanguage
              disabled={appearanceBusy}
              changeLanguage={(value) =>
                void onAppearanceChange({
                  language: value,
                })
              }
            />
            <button
              type="button"
              className="icon-button"
              disabled={appearanceBusy}
              aria-label={t(resolvedTheme === 'dark' ? 'switchToLight' : 'switchToDark')}
              title={t(resolvedTheme === 'dark' ? 'switchToLight' : 'switchToDark')}
              onClick={() => void onAppearanceChange({ theme: resolvedTheme === 'dark' ? 1 : 2, followTime: false })}
            >
              <Icon name={resolvedTheme === 'dark' ? 'sun' : 'moon'} size={18} />
            </button>
            <span className={styles['topbar-divider']} />
            {user ? (
              <button
                className={classNames('button', 'small')}
                disabled={logoutBusy}
                onClick={() => void onLogout()}
              >
                {t('logout')}
              </button>
            ) : (
              <Link
                className={classNames('button', 'small')}
                to={`/login?returnTo=${encodeURIComponent(returnTo)}`}
              >
                {t('login')}
                <Icon name="arrow" size={15} />
              </Link>
            )}
          </div>
        </header>
        <main
          id="main-content"
          tabIndex={-1}
          className={styles['main-content']}
          key={user?.id || 'guest'}
        >
          {!!error && (
            <div className={styles['global-error']}>
              <ErrorNotice error={error} />
              <button
                className={classNames('button', 'small')}
                onClick={onDismissError}
              >
                {t('cancel')}
              </button>
            </div>
          )}
          {children}
        </main>
        <footer className={styles['app-footer']}>
          <span>AI PR Review</span>
          <span>{t('tagline')}</span>
          <span>© 2026</span>
        </footer>
      </div>
      {profileOpen && <UserProfileDialog key={user.id} onClose={() => setProfileUserId(null)} />}
    </div>
  )
}
