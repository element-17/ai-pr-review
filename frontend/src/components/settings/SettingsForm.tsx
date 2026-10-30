import { classNames } from '../../lib/classNames'
import styles from './SettingsForm.module.scss'
import { useState } from 'react'
import { api } from '../../lib/api'
import { usePreferences } from '../../lib/preferences'
import type { Settings as UserSettings } from '../../lib/types'
import { EnumText, ErrorNotice, Icon, Notice } from '../ui'

/** 接收用户初始设置；返回可校验并保存检查类别、忽略目录和分析规模的表单节点。 */
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
      className={styles['settings-form']}
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
      <fieldset disabled={busy} className={styles.sections}>
        <section className="card" id="settings-checks" tabIndex={-1} aria-labelledby="checks-heading">
          <h2 id="checks-heading">{t('checks')}</h2>
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
        </section>
        <section className="card" id="settings-directories" tabIndex={-1} aria-labelledby="directories-heading">
          <h2 id="directories-heading">{t('ignoreDirsTitle')}</h2>
          <label className="field">
            <span>{t('ignoreDirs')}</span>
            <textarea
              name="ignoreDirs"
              defaultValue={initial.ignoreDirs.join('\n')}
              rows={5}
            />
          </label>
        </section>
        <section className="card" id="settings-limits" tabIndex={-1} aria-labelledby="limits-heading">
          <h2 id="limits-heading">{t('analysisLimits')}</h2>
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
        </section>
      </fieldset>
    </form>
  )
}
