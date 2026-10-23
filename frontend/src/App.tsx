import { useEffect, useState } from 'react'
import { api } from './lib/api'
import { setSession } from './lib/session'
import { navigate, useRoute, useSession } from './hooks'
import { PreferencesProvider } from './lib/PreferencesProvider'
import { usePreferences } from './lib/preferences'
import type { Settings as UserSettings, User } from './lib/types'
import { Empty, Link } from './components/ui'
import { parseRoute } from './lib/routes'
import AppShell from './components/layout/AppShell'
import Home from './pages/Home'
import Auth from './pages/Auth'
import Tasks from './pages/Tasks'
import TaskWorkspace from './components/workspace/TaskWorkspace'
import Overview from './pages/Overview'
import Issues from './pages/Issues'
import Files from './pages/Files'
import Settings from './pages/Settings'
import Compare from './pages/Compare'

function App() {
  return (
    <PreferencesProvider>
      <Application />
    </PreferencesProvider>
  )
}

export default App

function Application() {
  const route = useRoute()
  const session = useSession()
  const { setPreferences, t } = usePreferences()
  const [error, setError] = useState<unknown>(null)
  const [logoutBusy, setLogoutBusy] = useState(false)
  const [appearanceBusy, setAppearanceBusy] = useState(false)
  const { pathname, search, taskId, tab, guestToken, pageTitle, fileId, issueId } = parseRoute(route)
  const userId = session?.userInfo.id
  useEffect(() => {
    if (!userId) return
    const controller = new AbortController()
    api.get<User>('/auth/me', { signal: controller.signal }).catch((caught) => {
      if (!controller.signal.aborted) setError(caught)
    })
    return () => controller.abort()
  }, [userId])
  useEffect(() => {
    document.title = `${t(pageTitle)} · AI PR Review`
  }, [pageTitle, t])
  async function changeAppearance(values: {
    language?: 'zh-CN' | 'en-US'
    theme?: number
  }) {
    setAppearanceBusy(true)
    setPreferences(values)
    if (!session) { setAppearanceBusy(false); return }
    try {
      await api.patch<UserSettings>('/users/me/settings', values)
    } catch (caught) {
      setError(caught)
    } finally { setAppearanceBusy(false) }
  }
  async function logout() {
    setError(null)
    setLogoutBusy(true)
    try {
      await api.post('/auth/logout')
    } catch (caught) {
      setError(caught)
    } finally {
      setSession(null)
      navigate('/')
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
            <Home />
          ) : pathname === '/login' || pathname === '/register' ? (
            <Auth
              key={pathname}
              register={pathname === '/register'}
              search={search}
            />
          ) : pathname === '/tasks' ? (
            <Tasks search={search} />
          ) : pathname === '/settings' ? (
            <Settings />
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
