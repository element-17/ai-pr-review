import { useState } from 'react'
import { api } from '../../lib/api'
import { useResource } from '../../hooks'
import { usePreferences } from '../../lib/preferences'
import type { ModelOption, ModelSnapshot, Settings as UserSettings } from '../../lib/types'
import { ErrorNotice, Icon, Notice, ResourceState } from '../ui'
import styles from './ModelPreference.module.scss'

function providerLabel(provider: string) {
  return provider === 'ANTHROPIC_MESSAGES' ? 'Anthropic Messages' : 'OpenAI compatible'
}

/** 接收用户设置；提供可选模型偏好与当前生效模型的展示和保存。 */
export default function ModelPreference({ initial }: { initial: UserSettings }) {
  const { t } = usePreferences()
  const models = useResource<ModelOption[]>('/models/available')
  const [selected, setSelected] = useState(String(initial.preferredModelId || 0))
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<unknown>(null)
  const [saved, setSaved] = useState(false)
  const [effective, setEffective] = useState<ModelSnapshot | null | undefined>(initial.effectiveModel)
  const [hasDisabledPreference, setHasDisabledPreference] = useState(
    !!initial.preferredModelId && initial.preferredModelEnabled === false,
  )

  async function submit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setBusy(true)
    setError(null)
    setSaved(false)
    try {
      const updated = await api.patch<UserSettings>('/users/me/settings', { preferredModelId: Number(selected) })
      setSelected(String(updated.preferredModelId || 0))
      setEffective(updated.effectiveModel)
      setHasDisabledPreference(!!updated.preferredModelId && updated.preferredModelEnabled === false)
      setSaved(true)
    } catch (caught) {
      setError(caught)
    } finally {
      setBusy(false)
    }
  }

  return (
    <section className="card" id="settings-model" tabIndex={-1} aria-labelledby="model-preference-heading">
      <h2 id="model-preference-heading">{t('modelPreference')}</h2>
      <p className={styles.description}>{t('modelPreferenceDescription')}</p>
      <ResourceState loading={models.loading} error={models.error} retry={models.reload} />
      {models.data && <form className={styles.form} onSubmit={event => void submit(event)}>
        <label className="field">
          <span>{t('preferredModel')}</span>
          <select value={selected} onChange={event => { setSelected(event.target.value); setSaved(false) }}>
            <option value="0">{t('modelUseDefault')}</option>
            {models.data.map(model => <option key={model.id} value={model.id}>
              {model.displayName} · {providerLabel(model.provider)}{model.isDefault ? ` · ${t('modelDefault')}` : ''}
            </option>)}
          </select>
        </label>
        <button className="button primary" type="submit" disabled={busy}>
          <Icon name="check" size={16} />{busy ? t('saving') : t('save')}
        </button>
      </form>}
      {hasDisabledPreference && <Notice>{t('preferredModelDisabled')}</Notice>}
      {effective && <div className={styles.effective}>
        <span>{t('effectiveModel')}</span>
        <strong>{effective.displayName}</strong>
        <span>{providerLabel(effective.provider)} · {t(`modelSource${effective.source}` as 'modelSourceUSER_PREFERENCE')}</span>
      </div>}
      {saved && <Notice>{t('saved')}</Notice>}
      {!!error && <ErrorNotice error={error} />}
    </section>
  )
}
