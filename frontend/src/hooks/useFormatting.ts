import { usePreferences } from '../lib/preferences'
import { formatDateTime } from '../lib/format'

export function useFormatting() {
  const { language } = usePreferences()
  return {
    number: (value: number) => new Intl.NumberFormat(language).format(value),
    percent: (value: number) => new Intl.NumberFormat(language, { style: 'percent', maximumFractionDigits: 1 }).format(value / 100),
    dateTime: (value: string | null) => formatDateTime(value, language),
  }
}
