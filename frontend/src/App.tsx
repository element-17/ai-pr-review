import { lazy, Suspense, useEffect, useState } from 'react'
import { api, ApiError } from './lib/api'
import { setSession } from './lib/session'
import { navigate, useRoute, useSession } from './hooks'
import { PreferencesProvider } from './lib/PreferencesProvider'
import { usePreferences, type PreferencesUpdate } from './lib/preferences'
import type { Settings as UserSettings, User } from './lib/types'
import { Empty, Link } from './components/ui'
import { initializeNavigation, parseRoute } from './lib/routes'
import AppShell from './components/layout/AppShell'
import Home from './pages/Home'
import GuestHome from './pages/GuestHome'
import Auth from './pages/Auth'
import Tasks from './pages/Tasks'
import TaskWorkspace from './components/workspace/TaskWorkspace'
import Overview from './pages/Overview'
import Issues from './pages/Issues'
import Files from './pages/Files'
import Settings from './pages/Settings'
import Compare from './pages/Compare'
import GitHubCallback from './pages/GitHubCallback'
import AdminModels from './pages/AdminModels'
import AdminUsers from './pages/AdminUsers'
const Analytics = lazy(() => import('./pages/Analytics'))

/** 无参数；返回包裹偏好上下文的应用根节点。 */
function App() {
  return (
    <PreferencesProvider>
      <Application />
    </PreferencesProvider>
  )
}

export default App

/** 无参数；根据当前路由和会话返回应用外壳及对应页面节点。 */
function Application() {
  const route = useRoute()
  const session = useSession()
  const { setPreferences, t } = usePreferences()
  const [error, setError] = useState<unknown>(null)
  const [logoutBusy, setLogoutBusy] = useState(false)
  const [appearanceBusy, setAppearanceBusy] = useState(false)
  const { pathname, search, taskId, tab, guestToken, pageTitle: routeTitle, fileId, issueId } = parseRoute(route)
  const pageTitle = !session && pathname === '/' ? 'guestHome' : routeTitle
  const userId = session?.userInfo.id
  useEffect(() => {
    initializeNavigation()
  }, [])
  useEffect(() => {
    if (!userId) return
    const controller = new AbortController()
    api.get<User>('/auth/me', { signal: controller.signal }).catch((caught) => {
      if (!controller.signal.aborted && !(caught instanceof ApiError && caught.code === 401)) setError(caught)
    })
    return () => controller.abort()
  }, [userId])
  useEffect(() => {
    document.title = `${t(pageTitle)} · AI PR Review`
  }, [pageTitle, t])
  /** 接收待更新的偏好字段；更新本地偏好并同步登录用户设置，返回 Promise<void>。 */
  async function changeAppearance(values: PreferencesUpdate) {
    setAppearanceBusy(true)
    setPreferences(values)
    const { theme, language } = values
    if (!session || (theme === undefined && language === undefined)) { setAppearanceBusy(false); return }
    try {
      await api.patch<UserSettings>('/users/me/settings', { theme, language })
    } catch (caught) {
      setError(caught)
    } finally { setAppearanceBusy(false) }
  }
  /** 无参数；确认后退出登录、清除会话并跳转，返回 Promise<void>。 */
  async function logout() {
    if (logoutBusy || !window.confirm(t('logoutConfirm'))) return
    setError(null)
    setLogoutBusy(true)
    try {
      await api.post('/auth/logout')
    } catch (caught) {
      setError(caught)
    } finally {
      setSession(null)
      navigate('/login')
      setLogoutBusy(false)
    }
  }
  return (
    <AppShell
      pageTitle={pageTitle}
      taskPage={!!taskId}
      user={session?.userInfo || null}
      returnTo={route}
      error={error}
      logoutBusy={logoutBusy}
      appearanceBusy={appearanceBusy}
      onDismissError={() => setError(null)}
      onLogout={logout}
      onAppearanceChange={changeAppearance}
    >
          {pathname === '/' ? (
            session ? <Home search={search} /> : <GuestHome search={search} />
          ) : pathname === '/login' || pathname === '/register' ? (
            <Auth
              key={pathname}
              register={pathname === '/register'}
              search={search}
            />
          ) : pathname === '/tasks' ? (
            <Tasks search={search} />
          ) : pathname === '/settings' ? (
            <Settings search={search} />
          ) : pathname === '/auth/github/callback' ? (
            <GitHubCallback search={search} />
          ) : pathname === '/admin/models' && session?.userInfo.role === 'ADMIN' ? (
            <AdminModels />
          ) : pathname === '/admin/users' && session?.userInfo.role === 'ADMIN' ? (
            <AdminUsers />
          ) : pathname === '/analytics' ? (
            <Suspense fallback={null}><Analytics /></Suspense>
          ) : taskId !== undefined ? (
            <TaskWorkspace
              key={`${taskId}:${guestToken}`}
              taskId={taskId}
              guestToken={guestToken}
              tab={tab}
            >
              {(props) =>
                tab === 'overview' ? (
                  <Overview {...props} />
                ) : tab === 'files' ? (
                  <Files {...props} search={search} fileId={fileId} />
                ) : tab === 'issues' ? (
                  <Issues
                    {...props}
                    search={search}
                    issueId={issueId}
                  />
                ) : (
                  <Compare {...props} search={search} />
                )
              }
            </TaskWorkspace>
          ) : (
            <Empty title="notFound">
              <Link className="button primary" to="/">
                {t('returnHome')}
              </Link>
            </Empty>
          )}
    </AppShell>
  )
}
