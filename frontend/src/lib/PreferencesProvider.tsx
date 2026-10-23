import { useEffect, useState } from 'react'
import type { ReactNode } from 'react'
import type { Language } from './types'
import { translate } from '../i18n'
import { PreferencesContext } from './preferences'

interface Preferences {
  language: Language
  theme: number
}
function initialPreferences(): Preferences {
  try {
    const stored = JSON.parse(
      localStorage.getItem('pr-review.preferences') || '{}',
    )
    return {
      language: stored.language === 'en-US' ? 'en-US' : 'zh-CN',
      theme: [1, 2, 3].includes(stored.theme) ? stored.theme : 3,
    }
  } catch {
    return { language: 'zh-CN', theme: 3 }
  }
}

export function PreferencesProvider({ children }: { children: ReactNode }) {
  const [preferences, updatePreferences] = useState(initialPreferences)
  useEffect(() => {
    localStorage.setItem('pr-review.preferences', JSON.stringify(preferences))
    document.documentElement.lang = preferences.language
    const media = window.matchMedia('(prefers-color-scheme: dark)')
    const applyTheme = () => {
      document.documentElement.dataset.theme =
        preferences.theme === 2 || (preferences.theme === 3 && media.matches)
          ? 'dark'
          : 'light'
    }
    applyTheme()
    media.addEventListener('change', applyTheme)
    return () => media.removeEventListener('change', applyTheme)
  }, [preferences])
  return (
    <PreferencesContext.Provider
      value={{
        ...preferences,
        setPreferences: (values) =>
          updatePreferences((current) => ({ ...current, ...values })),
        t: (key) => translate(preferences.language, key),
      }}
    >
      {children}
    </PreferencesContext.Provider>
  )
}

