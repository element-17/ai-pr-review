import { classNames } from '../../lib/classNames'
import styles from './SettingsForm.module.css'
import { useState } from 'react'
import { api } from '../../lib/api'
import { usePreferences } from '../../lib/preferences'
import type { Settings as UserSettings } from '../../lib/types'
import { EnumText, ErrorNotice, Icon, Notice } from '../ui'

export default function SettingsForm({ initial }: { initial: UserSettings }) {
  const { t } = usePreferences()
  const [categories, setCategories] = useState(initial.checkCategories)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<unknown>(null)
  const [message, setMessage] = useState<'saved' | 'settingsInvalid' | null>(
    null,
  )
  return (
    <form
      className="card settings-form"
      onSubmit={async (event) => {
        event.preventDefault()
        const data = new FormData(event.currentTarget)
        const ignoreDirs = String(data.get('ignoreDirs'))
          .split(/\r?\n/)
          .map((value) => value.trim())
          .filter(Boolean)
        if (
          !categories.length ||
          !ignoreDirs.length ||
          ignoreDirs.length > 50 ||
          new Set(ignoreDirs).size !== ignoreDirs.length ||
          ignoreDirs.some(
            (value) =>
              value.length > 100 ||
              value.startsWith('/') ||
              value.includes('..') ||
              value.includes('\\') ||
              /^[A-Za-z]:/.test(value),
          )
        ) {
          setMessage('settingsInvalid')
          return
        }
        setBusy(true)
        setError(null)
        setMessage(null)
        try {
          await api.patch('/users/me/settings', {
            checkCategories: categories,
            ignoreDirs,
            maxFileCount: Number(data.get('maxFileCount')),
            maxDiffLines: Number(data.get('maxDiffLines')),
          })
          setMessage('saved')
        } catch (caught) {
          setError(caught)
        } finally {
          setBusy(false)
        }
      }}
    >
      <fieldset disabled={busy}>
        <h2>{t('checks')}</h2>
        <div className={styles['category-grid']}>
          {[1, 2, 3, 4, 5, 6, 7].map((value) => (
            <label className={styles['category-option']} key={value}>
              <input
                type="checkbox"
                checked={categories.includes(value)}
                onChange={(event) =>
                  setCategories((current) =>
                    event.target.checked
                      ? [...current, value]
                      : current.filter((category) => category !== value),
                  )
                }
              />
              <EnumText group="category" value={value} />
            </label>
          ))}
        </div>
        <label className="field">
          <span>{t('ignoreDirs')}</span>
          <textarea
            name="ignoreDirs"
            defaultValue={initial.ignoreDirs.join('\n')}
            rows={5}
          />
        </label>
        <div className={styles['form-columns']}>
          <label className="field">
            <span>{t('maxFiles')}</span>
            <input
              name="maxFileCount"
              type="number"
              min="1"
              max="2000"
              required
              defaultValue={initial.maxFileCount}
            />
          </label>
          <label className="field">
            <span>{t('maxDiff')}</span>
            <input
              name="maxDiffLines"
              type="number"
              min="1"
              max="200000"
              required
              defaultValue={initial.maxDiffLines}
            />
          </label>
        </div>
        {message && (
          <Notice danger={message === 'settingsInvalid'}>{t(message)}</Notice>
        )}
        {!!error && <ErrorNotice error={error} />}
        <button className={classNames('button', 'primary')}>
          {busy ? t('saving') : t('save')}
          <Icon name="check" size={16} />
        </button>
      </fieldset>
    </form>
  )
}
