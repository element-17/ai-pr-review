import { usePreferences } from '../../lib/preferences'
import type { MessageKey } from '../../i18n/types'

/** 接收枚举分组和数值；返回对应本地化文案的文本节点。 */
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
