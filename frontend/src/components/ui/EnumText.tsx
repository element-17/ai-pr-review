import { usePreferences } from '../../lib/preferences'
import type { MessageKey } from '../../i18n/types'

export function EnumText({
  group,
  value,
}: {
  group:
    | 'status'
    | 'stage'
    | 'severity'
    | 'category'
    | 'feedback'
    | 'fileStatus'
    | 'changeType'
    | 'prState'
    | 'theme'
  value: number
}) {
  const { t } = usePreferences()
  return <>{t(`${group}${value}` as MessageKey) || '—'}</>
}
