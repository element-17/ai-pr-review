import type { Language } from './types'

/** 接收服务端时间字符串；返回 Date，不含 T 时按 UTC+8 解析，无效时为 Invalid Date。 */
export function parseServerDate(value: string) {
  return new Date(value.includes('T') ? value : value.replace(' ', 'T') + '+08:00')
}

/** 接收可空时间和语言；返回上海时区的本地化文本，空值返回破折号，无效日期返回原文。 */
export function formatDateTime(value: string | null, language: Language) {
  if (!value) return '—'
  const date = parseServerDate(value)
  return Number.isNaN(date.getTime()) ? value : new Intl.DateTimeFormat(language, {
    dateStyle: 'medium', timeStyle: 'short', timeZone: 'Asia/Shanghai',
  }).format(date)
}
