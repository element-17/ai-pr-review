import { useEffect, useState, useSyncExternalStore } from 'react'
import type { ReactNode } from 'react'
import type { Language } from './types'
import { translate } from '../i18n'
import { PreferencesContext } from './preferences'

interface Preferences {
  language: Language
  theme: number
  followTime: boolean
}
function initialPreferences(): Preferences {
  try {
    const stored = JSON.parse(
      localStorage.getItem('pr-review.preferences') || '{}',
    )
    return {
      language: stored.language === 'en-US' ? 'en-US' : 'zh-CN',
      theme: [1, 2, 3].includes(stored.theme) ? stored.theme : 3,
      followTime: stored.followTime === true,
    }
  } catch {
    return { language: 'zh-CN', theme: 3, followTime: false }
  }
}

function subscribeSystemTheme(callback: () => void) {
  const media = window.matchMedia('(prefers-color-scheme: dark)')
  media.addEventListener('change', callback)
  return () => media.removeEventListener('change', callback)
}

function systemIsDark() {
  return window.matchMedia('(prefers-color-scheme: dark)').matches
}

export function PreferencesProvider({ children }: { children: ReactNode }) {
  const [preferences, updatePreferences] = useState(initialPreferences)
  const prefersDark = useSyncExternalStore(subscribeSystemTheme, systemIsDark)
  const [hour, setHour] = useState(() => new Date().getHours())
  useEffect(() => {
    const updateTime = () => setHour(new Date().getHours())
    const timer = window.setInterval(updateTime, 30_000)
    window.addEventListener('focus', updateTime)
    document.addEventListener('visibilitychange', updateTime)
    return () => {
      window.clearInterval(timer)
      window.removeEventListener('focus', updateTime)
      document.removeEventListener('visibilitychange', updateTime)
    }
  }, [])
  const dark = preferences.followTime
    ? hour < 7 || hour >= 19
    : preferences.theme === 2 || (preferences.theme === 3 && prefersDark)
  const resolvedTheme = dark ? 'dark' : 'light'
  useEffect(() => {
    localStorage.setItem('pr-review.preferences', JSON.stringify(preferences))
    document.documentElement.lang = preferences.language
  }, [preferences])
  useEffect(() => {
    document.documentElement.dataset.theme = resolvedTheme
  }, [resolvedTheme])
  return (
    <PreferencesContext.Provider
      value={{
        ...preferences,
        resolvedTheme,
        setPreferences: (values) =>
          updatePreferences((current) => ({ ...current, ...values })),
        t: (key) => translate(preferences.language, key),
      }}
    >
      {children}
    </PreferencesContext.Provider>
  )
}

