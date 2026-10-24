import { usePreferences } from '../../lib/preferences'
import { formatDateTime } from '../../lib/format'

/** 接收可空服务端时间；返回按当前界面语言格式化的日期文本节点。 */
export function DateText({ value }: { value: string | null }) {
  const { language } = usePreferences()
  return <>{formatDateTime(value, language)}</>
}
