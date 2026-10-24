import { createContext, useContext } from 'react'
import type { MessageKey } from '../i18n/types'
import type { Language } from './types'

export interface PreferencesUpdate {
  language?: Language
  theme?: number
  followTime?: boolean
}

export const PreferencesContext = createContext<{
  language: Language
  theme: number
  followTime: boolean
  resolvedTheme: 'light' | 'dark'
  setPreferences: (values: PreferencesUpdate) => void
  t: (key: MessageKey) => string
} | null>(null)

/** 无参数；返回偏好上下文，未包裹 PreferencesProvider 时抛出错误。 */
export function usePreferences() {
  const context = useContext(PreferencesContext)
  if (!context) throw new Error('PreferencesProvider is required')
  return context
}
