import type { Language } from './types'

export function parseServerDate(value: string) {
  return new Date(value.includes('T') ? value : value.replace(' ', 'T') + '+08:00')
}

export function formatDateTime(value: string | null, language: Language) {
  if (!value) return '—'
  const date = parseServerDate(value)
  return Number.isNaN(date.getTime()) ? value : new Intl.DateTimeFormat(language, {
    dateStyle: 'medium', timeStyle: 'short', timeZone: 'Asia/Shanghai',
  }).format(date)
}
