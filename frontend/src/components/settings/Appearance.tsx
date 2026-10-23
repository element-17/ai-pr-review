import { useState } from 'react'
import { api } from '../../lib/api'
import { useSession } from '../../hooks'
import { usePreferences } from '../../lib/preferences'
import type { Language } from '../../lib/types'
import { EnumText, ErrorNotice } from '../ui'

export default function Appearance() {
  const { t, theme, language, setPreferences } = usePreferences()
  const session = useSession()
  const [error, setError] = useState<unknown>(null)
  const [busy, setBusy] = useState(false)
  async function change(values: { theme?: number; language?: Language }) {
    setPreferences(values)
    setError(null)
    if (!session) return
    setBusy(true)
    try {
      await api.patch('/users/me/settings', values)
    } catch (caught) {
      setError(caught)
    } finally {
      setBusy(false)
    }
  }
  return (
    <section className="card">
      <h2>{t('appearance')}</h2>
      <label className="field">
        <span>{t('theme')}</span>
        <select
          aria-label={t('theme')}
          value={theme}
          disabled={busy}
          onChange={(event) =>
            void change({ theme: Number(event.target.value) })
          }
        >
          {[1, 2, 3].map((value) => (
            <option key={value} value={value}>
              <EnumText group="theme" value={value} />
            </option>
          ))}
        </select>
      </label>
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
