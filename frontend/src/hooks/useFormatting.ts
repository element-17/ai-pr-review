import { usePreferences } from '../lib/preferences'
import { formatDateTime } from '../lib/format'

/** 无参数；返回按当前界面语言格式化数字、百分比和日期的函数集合。 */
export function useFormatting() {
  const { language } = usePreferences()
  return {
    /** 接收数字；返回本地化数字文本。 */
    number: (value: number) => new Intl.NumberFormat(language).format(value),
    /** 接收百分数数值（如 75）；返回百分比文本（如 75%）。 */
    percent: (value: number) => new Intl.NumberFormat(language, { style: 'percent', maximumFractionDigits: 1 }).format(value / 100),
    /** 接收可空服务端时间；返回本地化日期时间文本。 */
    dateTime: (value: string | null) => formatDateTime(value, language),
  }
}
