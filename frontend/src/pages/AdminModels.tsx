import { useCallback, useEffect, useState } from 'react'
import type { FormEvent } from 'react'
import { api } from '../lib/api'
import { usePreferences } from '../lib/preferences'
import { classNames } from '../lib/classNames'
import { ErrorNotice, Icon, Notice, PageHeading } from '../components/ui'
import styles from './AdminModels.module.scss'

type Provider = 'OPENAI_CHAT' | 'ANTHROPIC_MESSAGES'

interface ModelConfig {
  id: number
  displayName: string
  provider: Provider
  baseUrl: string
  modelId: string
  apiKeyConfigured: boolean
  apiKeyHint: string | null
  maxOutputTokens: number
  temperature: number
  enabled: boolean
  isDefault: boolean
  createTime?: string
  updateTime?: string
}

interface ModelTestResult {
  success: boolean
  provider: Provider
  modelId: string
  latencyMs: number | null
  message: string
}

interface ModelForm {
  displayName: string
  provider: Provider
  baseUrl: string
  modelId: string
  apiKey: string
  maxOutputTokens: number
  temperature: number
  enabled: boolean
  isDefault: boolean
}

const emptyForm: ModelForm = {
  displayName: '',
  provider: 'OPENAI_CHAT',
  baseUrl: '',
  modelId: '',
  apiKey: '',
  maxOutputTokens: 4096,
  temperature: 0.2,
  enabled: true,
  isDefault: false,
}

/** 管理员模型配置：Key 仅写入，列表和详情始终使用后端掩码。 */
export default function AdminModels() {
  const { t } = usePreferences()
  const [items, setItems] = useState<ModelConfig[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<unknown>(null)
  const [form, setForm] = useState<ModelForm>(emptyForm)
  const [editingId, setEditingId] = useState<number | null>(null)
  const [busy, setBusy] = useState(false)
  const [tested, setTested] = useState<ModelTestResult | null>(null)
  const [notice, setNotice] = useState<'saved' | null>(null)
  const [reloadKey, setReloadKey] = useState(0)

  const loadItems = useCallback(async (signal?: AbortSignal) => {
    setLoading(true)
    setError(null)
    try {
      const result = await api.get<ModelConfig[]>('/admin/model-configs', { signal })
      if (!signal?.aborted) setItems(result)
    } catch (caught) {
      if (!signal?.aborted) setError(caught)
    } finally {
      if (!signal?.aborted) setLoading(false)
    }
  }, [])

  useEffect(() => {
    const controller = new AbortController()
    void Promise.resolve().then(() => loadItems(controller.signal))
    return () => controller.abort()
  }, [loadItems, reloadKey])

  function resetForm() {
    setEditingId(null)
    setForm(emptyForm)
    setTested(null)
    setNotice(null)
    setError(null)
  }

  async function editItem(id: number) {
    setBusy(true)
    setError(null)
    setNotice(null)
    setTested(null)
    try {
      const item = await api.get<ModelConfig>(`/admin/model-configs/${id}`)
      setEditingId(item.id)
      setForm({
        displayName: item.displayName,
        provider: item.provider,
        baseUrl: item.baseUrl,
        modelId: item.modelId,
        apiKey: '',
        maxOutputTokens: item.maxOutputTokens,
        temperature: item.temperature,
        enabled: item.enabled,
        isDefault: item.isDefault,
      })
    } catch (caught) {
      setError(caught)
    } finally {
      setBusy(false)
    }
  }

  function update<K extends keyof ModelForm>(key: K, value: ModelForm[K]) {
    setForm(current => ({ ...current, [key]: value }))
    setTested(null)
    setNotice(null)
  }

  function payload() {
    return {
      displayName: form.displayName.trim(),
      provider: form.provider,
      baseUrl: form.baseUrl.trim(),
      modelId: form.modelId.trim(),
      apiKey: form.apiKey,
      maxOutputTokens: Number(form.maxOutputTokens),
      temperature: Number(form.temperature),
      enabled: form.enabled,
      isDefault: form.isDefault,
    }
  }

  function testPayload() {
    return {
      provider: form.provider,
      baseUrl: form.baseUrl.trim(),
      modelId: form.modelId.trim(),
      apiKey: form.apiKey,
      maxOutputTokens: Number(form.maxOutputTokens),
      temperature: Number(form.temperature),
    }
  }

  async function save(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (busy) return
    setBusy(true)
    setError(null)
    setNotice(null)
    try {
      const body = payload()
      if (editingId === null) await api.post('/admin/model-configs', body)
      else await api.put(`/admin/model-configs/${editingId}`, body)
      setForm(current => ({ ...current, apiKey: '' }))
      setNotice('saved')
      setReloadKey(value => value + 1)
      if (editingId === null) {
        setEditingId(null)
        setForm(emptyForm)
        setTested(null)
      }
    } catch (caught) {
      setError(caught)
    } finally {
      setBusy(false)
    }
  }

  async function testDraft() {
    if (busy) return
    setBusy(true)
    setError(null)
    setNotice(null)
    setTested(null)
    try {
      const result = await api.post<ModelTestResult>('/admin/model-configs/test', testPayload())
      setTested(result)
    } catch (caught) {
      setError(caught)
    } finally {
      setBusy(false)
    }
  }

  async function testSaved(id: number) {
    setBusy(true)
    setError(null)
    setNotice(null)
    setTested(null)
    try {
      const result = await api.post<ModelTestResult>(`/admin/model-configs/${id}/test`)
      setTested(result)
    } catch (caught) {
      setError(caught)
    } finally {
      setBusy(false)
    }
  }

  async function setEnabled(item: ModelConfig) {
    setBusy(true)
    setError(null)
    try {
      await api.put(`/admin/model-configs/${item.id}/status?enabled=${!item.enabled}`)
      setReloadKey(value => value + 1)
    } catch (caught) {
      setError(caught)
    } finally {
      setBusy(false)
    }
  }

  async function setDefault(item: ModelConfig) {
    setBusy(true)
    setError(null)
    try {
      await api.put(`/admin/model-configs/${item.id}/default`)
      setReloadKey(value => value + 1)
    } catch (caught) {
      setError(caught)
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className={styles.page}>
      <PageHeading title="modelAdminTitle" description="modelAdminDescription" />
      {!!error && <ErrorNotice error={error} />}
      {notice === 'saved' && <Notice>{t('modelAdminSaved')}</Notice>}
      {tested && (
        <Notice danger={!tested.success}>
          <strong>{tested.success ? t('modelAdminTestSuccess') : t('modelAdminTestFailed')}</strong>
          <p>{t('modelAdminLatency')}: {tested.latencyMs ?? 0} ms</p>
          <p>{tested.message}</p>
        </Notice>
      )}

      <section className={styles.listSection} aria-labelledby="model-list-heading">
        <header className={styles.sectionHeading}>
          <div>
            <h2 id="model-list-heading">{t('modelAdminList')}</h2>
            <p>{t('modelAdminListDescription')}</p>
          </div>
          <button type="button" className="button" disabled={loading || busy} onClick={() => setReloadKey(value => value + 1)}>
            <Icon name="history" size={16} />
            {t('refresh')}
          </button>
        </header>
        {loading ? <p className={styles.status}>{t('loading')}</p> : items.length ? (
          <div className={styles.tableWrap}>
            <table className={styles.table}>
              <thead>
                <tr>
                  <th>{t('modelAdminName')}</th>
                  <th>{t('modelAdminProvider')}</th>
                  <th>{t('modelAdminModelId')}</th>
                  <th>{t('modelAdminApiKey')}</th>
                  <th>{t('status')}</th>
                  <th>{t('modelAdminActions')}</th>
                </tr>
              </thead>
              <tbody>
                {items.map(item => (
                  <tr key={item.id}>
                    <td>
                      <strong>{item.displayName}</strong>
                      {item.isDefault && <span className={styles.defaultMark}>{t('modelAdminDefault')}</span>}
                    </td>
                    <td>{t(item.provider === 'OPENAI_CHAT' ? 'modelAdminOpenAi' : 'modelAdminAnthropic')}</td>
                    <td><code>{item.modelId}</code></td>
                    <td>{item.apiKeyConfigured ? item.apiKeyHint || t('modelAdminKeyConfigured') : t('modelAdminKeyMissing')}</td>
                    <td><span className={classNames(styles.statusMark, item.enabled ? styles.enabled : styles.disabled)}>{t(item.enabled ? 'modelAdminEnabled' : 'modelAdminDisabled')}</span></td>
                    <td>
                      <div className={styles.rowActions}>
                        <button type="button" className="button small" disabled={busy} onClick={() => void editItem(item.id)}>{t('modelAdminEdit')}</button>
                        <button type="button" className="button small" disabled={busy || !item.enabled || item.isDefault} onClick={() => void setDefault(item)}>{t('modelAdminSetDefault')}</button>
                        <button type="button" className="button small" disabled={busy} onClick={() => void setEnabled(item)}>{t(item.enabled ? 'modelAdminDisable' : 'modelAdminEnable')}</button>
                        <button type="button" className="button small" disabled={busy} onClick={() => void testSaved(item.id)}>{t('modelAdminTest')}</button>
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        ) : (
          <p className={styles.status}>{t('modelAdminEmpty')}</p>
        )}
      </section>

      <section className={styles.editor} aria-labelledby="model-editor-heading">
        <header className={styles.sectionHeading}>
          <div>
            <h2 id="model-editor-heading">{t(editingId === null ? 'modelAdminCreate' : 'modelAdminEditTitle')}</h2>
            <p>{t('modelAdminKeyHint')} {t('modelAdminTestDraftKeyHint')}</p>
          </div>
          {editingId !== null && <button type="button" className="button small" onClick={resetForm}>{t('modelAdminNew')}</button>}
        </header>
        <form className={styles.form} onSubmit={event => void save(event)}>
          <div className={styles.fields}>
            <label className="field">
              <span>{t('modelAdminName')}</span>
              <input required maxLength={100} value={form.displayName} onChange={event => update('displayName', event.target.value)} />
            </label>
            <label className="field">
              <span>{t('modelAdminProvider')}</span>
              <select value={form.provider} onChange={event => update('provider', event.target.value as Provider)}>
                <option value="OPENAI_CHAT">{t('modelAdminOpenAi')}</option>
                <option value="ANTHROPIC_MESSAGES">{t('modelAdminAnthropic')}</option>
              </select>
            </label>
            <label className="field spanTwo">
              <span>{t('modelAdminBaseUrl')}</span>
              <input required type="url" maxLength={500} value={form.baseUrl} onChange={event => update('baseUrl', event.target.value)} placeholder="https://api.example.com/v1" />
            </label>
            <label className="field">
              <span>{t('modelAdminModelId')}</span>
              <input required maxLength={200} value={form.modelId} onChange={event => update('modelId', event.target.value)} />
            </label>
            <label className="field">
              <span>{t('modelAdminApiKey')}</span>
              <input type="password" autoComplete="new-password" required={editingId === null} value={form.apiKey} onChange={event => update('apiKey', event.target.value)} placeholder={editingId !== null ? items.find(item => item.id === editingId)?.apiKeyHint || t('modelAdminKeyConfigured') : ''} />
            </label>
            <label className="field">
              <span>{t('modelAdminMaxOutputTokens')}</span>
              <input type="number" min={1} max={200000} required value={form.maxOutputTokens} onChange={event => update('maxOutputTokens', Number(event.target.value))} />
            </label>
            <label className="field">
              <span>{t('modelAdminTemperature')}</span>
              <input type="number" min={0} max={2} step="0.1" required value={form.temperature} onChange={event => update('temperature', Number(event.target.value))} />
            </label>
          </div>
          {editingId === null && <div className={styles.options}>
            <label><input type="checkbox" checked={form.enabled} onChange={event => update('enabled', event.target.checked)} />{t('modelAdminEnabled')}</label>
            <label><input type="checkbox" checked={form.isDefault} onChange={event => update('isDefault', event.target.checked)} />{t('modelAdminDefault')}</label>
          </div>}
          <div className={styles.formActions}>
            <button type="button" className="button" disabled={busy || (editingId !== null && !form.apiKey)} onClick={() => void testDraft()}>
              {busy ? t('loading') : t('modelAdminTestDraft')}
            </button>
            <button type="submit" className={classNames('button', 'primary')} disabled={busy}>
              <Icon name="check" size={16} />
              {busy ? t('saving') : t('save')}
            </button>
          </div>
        </form>
      </section>
    </div>
  )
}
