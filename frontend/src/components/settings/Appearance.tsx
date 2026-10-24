import { useState } from 'react'
import { api } from '../../lib/api'
import { useSession } from '../../hooks'
import { usePreferences, type PreferencesUpdate } from '../../lib/preferences'
import type { Language } from '../../lib/types'
import { EnumText, ErrorNotice } from '../ui'

/** 无参数；返回当前主题、按时间切换和界面语言的设置区域节点。 */
export default function Appearance() {
  const { t, theme, language, followTime, setPreferences } = usePreferences()
  const session = useSession()
  const [error, setError] = useState<unknown>(null)
  const [busy, setBusy] = useState(false)
  /** 接收待更新偏好；更新本地配置并同步登录用户的主题/语言，返回 Promise<void>。 */
  async function change(values: PreferencesUpdate) {
    setPreferences(values)
    setError(null)
    const { theme, language } = values
    if (!session || (theme === undefined && language === undefined)) return
    setBusy(true)
    try {
      await api.patch('/users/me/settings', { theme, language })
    } catch (caught) {
      setError(caught)
    } finally {
      setBusy(false)
    }
  }
  return (
    <section className="card" id="settings-appearance" tabIndex={-1} aria-labelledby="appearance-heading">
      <h2 id="appearance-heading">{t('appearance')}</h2>
      <label className="field">
        <span>{t('theme')}</span>
        <select
          aria-label={t('theme')}
          value={theme}
          disabled={busy || followTime}
          onChange={(event) =>
            void change({ theme: Number(event.target.value), followTime: false })
          }
        >
          {[1, 2, 3].map((value) => (
            <option key={value} value={value}>
              <EnumText group="theme" value={value} />
            </option>
          ))}
        </select>
      </label>
      <label className="check-label">
        <input
          type="checkbox"
          checked={followTime}
          disabled={busy}
          aria-describedby="theme-time-hint"
          onChange={event => void change({ followTime: event.target.checked })}
        />
        {t('themeFollowTime')}
      </label>
      <p id="theme-time-hint" className="small-text muted">{t('themeTimeHint')}</p>
      <label className="field">
        <span>{t('language')}</span>
        <select
          aria-label={t('language')}
          value={language}
          disabled={busy}
          onChange={(event) =>
            void change({ language: event.target.value as Language })
          }
        >
          <option value="zh-CN">{t('chinese')}</option>
          <option value="en-US">{t('english')}</option>
        </select>
      </label>
      {!!error && <ErrorNotice error={error} />}
    </section>
  )
}
