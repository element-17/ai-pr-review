import { usePreferences } from '../../lib/preferences'

export function DateText({ value }: { value: string | null }) {
  const { language } = usePreferences()
  if (!value) return <>—</>
  const date = new Date(
    value.includes('T') ? value : value.replace(' ', 'T') + '+08:00',
  )
  return (
    <>
      {Number.isNaN(date.getTime())
        ? value
        : new Intl.DateTimeFormat(language, {
            dateStyle: 'medium',
            timeStyle: 'short',
            timeZone: 'Asia/Shanghai',
          }).format(date)}
    </>
  )
}
