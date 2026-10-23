import { createContext, useContext } from 'react'
import type { MessageKey } from '../i18n/types'
import type { Language } from './types'

export const PreferencesContext = createContext<{
  language: Language
  theme: number
  setPreferences: (values: { language?: Language; theme?: number }) => void
  t: (key: MessageKey) => string
} | null>(null)

export function usePreferences() {
  const context = useContext(PreferencesContext)
  if (!context) throw new Error('PreferencesProvider is required')
  return context
}
