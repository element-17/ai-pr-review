import { usePreferences } from '../../lib/preferences'
import { formatDateTime } from '../../lib/format'

export function DateText({ value }: { value: string | null }) {
  const { language } = usePreferences()
  return <>{formatDateTime(value, language)}</>
}
