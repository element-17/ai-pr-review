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
/** 无参数；返回本地偏好，缺失或读取失败的字段使用默认值。 */
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

/** 接收系统主题变化回调；注册监听并返回取消订阅函数。 */
function subscribeSystemTheme(callback: () => void) {
  const media = window.matchMedia('(prefers-color-scheme: dark)')
  media.addEventListener('change', callback)
  return () => media.removeEventListener('change', callback)
}

/** 无参数；返回系统当前是否偏好深色主题。 */
function systemIsDark() {
  return window.matchMedia('(prefers-color-scheme: dark)').matches
}

/** 接收子节点；返回提供主题、语言、更新方法和翻译函数的上下文节点。 */
export function PreferencesProvider({ children }: { children: ReactNode }) {
  const [preferences, updatePreferences] = useState(initialPreferences)
  const prefersDark = useSyncExternalStore(subscribeSystemTheme, systemIsDark)
  const [hour, setHour] = useState(() => new Date().getHours())
  useEffect(() => {
    /** 无参数；将本地当前小时写入状态，无返回值。 */
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
        /** 接收部分偏好字段；合并更新本地状态，无返回值。 */
        setPreferences: (values) =>
          updatePreferences((current) => ({ ...current, ...values })),
        /** 接收翻译键；返回当前界面语言的文案。 */
        t: (key) => translate(preferences.language, key),
      }}
    >
      {children}
    </PreferencesContext.Provider>
  )
}

