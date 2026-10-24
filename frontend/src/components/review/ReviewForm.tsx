import { useRef, useState, type FormEvent } from 'react'
import { analysisEnabled, api, ApiError, normalizePrUrl, query } from '../../lib/api'
import { navigate, useFormatting } from '../../hooks'
import { rememberGuestTask } from '../../lib/guestTasks'
import { usePreferences } from '../../lib/preferences'
import type { CreatedTask, Language, Preview } from '../../lib/types'
import { EnumText, ErrorNotice, ExternalLink, Icon, Link, Notice } from '../ui'
import styles from './ReviewForm.module.scss'

/** 接收初始查询串和游客模式标记；返回 PR 预览、语言选择及分析提交表单节点。 */
export default function ReviewForm({ search = '', guest = false }: { search?: string; guest?: boolean }) {
  const { t, language } = usePreferences()
  const { number } = useFormatting()
  const params = new URLSearchParams(search)
  const [url, setUrl] = useState(() => normalizePrUrl(params.get('url') || '') || '')
  const [languageOverride, setReportLanguage] = useState<Language | null>(() => {
    const value = params.get('reportLanguage')
    return value === 'zh-CN' || value === 'en-US' ? value : null
  })
  const reportLanguage = languageOverride || language
  const submission = useRef({ signature: '', key: '' })
  const [preview, setPreview] = useState<Preview | null>(null)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<unknown>(null)
  const [validation, setValidation] = useState(false)
  const [consent, setConsent] = useState(false)
  const [existingTask, setExistingTask] = useState<number | null>(null)
  /** 接收表单提交事件；先预览 PR，确认后创建任务并跳转，返回 Promise<void>。 */
  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (busy) return
    const normalized = normalizePrUrl(url)
    if (!normalized) { setValidation(true); return }
    if (!analysisEnabled || (preview && !consent)) return
    setBusy(true)
    setError(null)
    setValidation(false)
    setExistingTask(null)
    try {
      if (!preview) {
        setPreview(await api.post<Preview>('/prs/preview', { url: normalized }, { public: guest }))
      } else {
        const signature = `${normalized}:${reportLanguage}`
        if (submission.current.signature !== signature) submission.current = { signature, key: crypto.randomUUID() }
        const task = await api.post<CreatedTask>(
          '/tasks',
          { url: normalized, reportLanguage },
          { idempotencyKey: submission.current.key, public: guest },
        )
        rememberGuestTask(task)
        navigate(`/tasks/${task.taskId}${query({ guestToken: task.guestToken || undefined })}`)
      }
    } catch (caught) {
      setError(caught)
      if (!guest && caught instanceof ApiError && typeof caught.data?.existingTaskId === 'number') setExistingTask(caught.data.existingTaskId)
    } finally {
      setBusy(false)
    }
  }
  return (
    <form onSubmit={submit}>
      <label className="field">
        <span>{t('prUrl')}</span>
        <div className={styles['input-with-icon']}>
          <Icon name="code" />
          <input
            type="url"
            disabled={busy}
            required
            value={url}
            placeholder={t('prPlaceholder')}
            onChange={event => {
              setUrl(event.target.value)
              setPreview(null)
              setConsent(false)
              setValidation(false)
            }}
          />
        </div>
      </label>
      {validation && <Notice danger>{t('invalidPr')}</Notice>}
      <div className={styles.options}>
        <label className="field">
          <span>{t('reportLanguage')}</span>
          <select aria-label={t('reportLanguage')} disabled={busy} value={reportLanguage} onChange={event => setReportLanguage(event.target.value as Language)}>
            <option value="zh-CN">{t('chinese')}</option>
            <option value="en-US">{t('english')}</option>
          </select>
        </label>
        <button className="button primary" disabled={!analysisEnabled || busy || (!!preview && !consent)}>
          {busy ? t('loading') : preview ? t('start') : t('preview')}
          <Icon name="arrow" size={17} />
        </button>
      </div>
      {!analysisEnabled && <Notice>{t('analysisUnavailable')}</Notice>}
      {!!error && <ErrorNotice error={error} />}
      {existingTask && <Link className="button" to={`/tasks/${existingTask}`}>{t('viewResult')}</Link>}
      {preview && (
        <div className={styles.preview}>
          <span className="eyebrow">{preview.pr.repoFullName} #{preview.pr.prNumber}</span>
          <h3>{preview.pr.title}</h3>
          <p><EnumText group="prState" value={preview.pr.state} /></p>
          <p>{preview.pr.author} · {preview.pr.headBranch} → {preview.pr.baseBranch} · <code>{preview.pr.headSha.slice(0, 8)}</code></p>
          <p><span className="positive">+{number(preview.pr.addLines)}</span> <span className="negative">−{number(preview.pr.deleteLines)}</span> · {number(preview.pr.fileCount)} {t('changedFiles')}</p>
          <ExternalLink url={normalizePrUrl(url)}>{t('github')}</ExternalLink>
          {!guest && preview.reusableTask && (
            <Notice>{t('reusable')} <Link to={`/tasks/${preview.reusableTask.taskId}`}>{t('viewResult')}</Link></Notice>
          )}
          <label className="check-label">
            <input type="checkbox" checked={consent} onChange={event => setConsent(event.target.checked)} />
            {t('privacy')}
          </label>
        </div>
      )}
      <p className={styles['privacy-note']}><Icon name="shield" size={16} />{t('privacy')}</p>
    </form>
  )
}
