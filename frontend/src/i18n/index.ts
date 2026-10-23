import zh from './locales/zh-CN/common.json'
import en from './locales/en-US/common.json'
import type { Language, MessageKey, Translation } from './types'

const resources = {
  'zh-CN': zh,
  'en-US': en satisfies Translation & Record<Exclude<keyof typeof en, MessageKey>, never>,
} satisfies Record<Language, Translation>

export function translate(language: Language, key: MessageKey): string {
  return resources[language]?.[key] || resources['zh-CN'][key] || key
}
