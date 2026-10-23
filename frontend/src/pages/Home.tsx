import { classNames } from '../lib/classNames'
import styles from './Home.module.css'
import { useRef, useState } from 'react'
import type { FormEvent } from 'react'
import {
  analysisEnabled,
  api,
  ApiError,
  normalizePrUrl,
  query,
} from '../lib/api'
import { navigate } from '../hooks'
import { usePreferences } from '../lib/preferences'
import type { CreatedTask, Language, Preview } from '../lib/types'
import { ErrorNotice, ExternalLink, Icon, Link, Notice } from '../components/ui'

export default function Home() {
  const { t, language } = usePreferences()
  const [url, setUrl] = useState('')
  const [reportLanguageOverride, setReportLanguage] = useState<Language | null>(
    null,
  )
  const reportLanguage = reportLanguageOverride || language
  const submission = useRef({ signature: '', key: '' })
  const [preview, setPreview] = useState<Preview | null>(null)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<unknown>(null)
  const [validation, setValidation] = useState(false)
  const [taskId, setTaskId] = useState('')
  const [guestToken, setGuestToken] = useState('')
  const [taskInvalid, setTaskInvalid] = useState(false)
  const [consent, setConsent] = useState(false)
  const [existingTask, setExistingTask] = useState<number | null>(null)
  async function submit(event: FormEvent) {
    event.preventDefault()
    const normalized = normalizePrUrl(url)
    if (!normalized) {
      setValidation(true)
      return
    }
    if (!analysisEnabled) return
    setBusy(true)
    setError(null)
    setValidation(false)
    setExistingTask(null)
    try {
      if (!preview)
        setPreview(await api.post<Preview>('/prs/preview', { url: normalized }))
      else {
        if (!consent) return
        const signature = `${normalized}:${reportLanguage}`
        if (submission.current.signature !== signature) {
          submission.current = { signature, key: crypto.randomUUID() }
        }
        const task = await api.post<CreatedTask>(
          '/tasks',
          { url: normalized, reportLanguage },
          { idempotencyKey: submission.current.key },
        )
        navigate(
          `/tasks/${task.taskId}${query({ guestToken: task.guestToken || undefined })}`,
        )
      }
    } catch (caught) {
      setError(caught)
      if (
        caught instanceof ApiError &&
        typeof caught.data?.existingTaskId === 'number'
      )
        setExistingTask(caught.data.existingTaskId)
    } finally {
      setBusy(false)
    }
  }
  return (
    <div className={styles['home-page']}>
      <div className={styles['home-masthead']}>
        <span>{t('deskLabel')}</span>
        <span>GITHUB / PULL REQUEST</span>
      </div>
      <div className={styles['review-studio']}>
        <section className={styles['hero']}>
          <span className="eyebrow">
            <span className={styles['editorial-dash']} /> {t('homeKicker')}
          </span>
          <h1>
            {t('homeTitle')}
            <em>{t('homeTitleAccent')}</em>
          </h1>
          <p>{t('homeDescription')}</p>
        </section>
        <section className={classNames('card', styles['new-review-card'])}>
          <div className={styles['section-label']}>
            <span className={styles['section-index']}>01 /</span>
            <h2>{t('home')}</h2>
          </div>
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
                  onChange={(event) => {
                    setUrl(event.target.value)
                    setPreview(null)
                    setConsent(false)
                    setValidation(false)
                  }}
                />
              </div>
            </label>
            {validation && <Notice danger>{t('invalidPr')}</Notice>}
            <div className={styles['review-options']}>
              <label className="field">
                <span>{t('reportLanguage')}</span>
                <select
                  aria-label={t('reportLanguage')}
                  disabled={busy}
                  value={reportLanguage}
                  onChange={(event) =>
                    setReportLanguage(event.target.value as Language)
                  }
                >
                  <option value="zh-CN">{t('chinese')}</option>
                  <option value="en-US">{t('english')}</option>
                </select>
              </label>
              <button
                className={classNames('button', 'primary')}
                disabled={!analysisEnabled || busy || (!!preview && !consent)}
              >
                {busy ? t('loading') : preview ? t('start') : t('preview')}
                <Icon name="arrow" size={17} />
              </button>
            </div>
            {!analysisEnabled && <Notice>{t('analysisUnavailable')}</Notice>}
            {!!error && <ErrorNotice error={error} />}
            {existingTask && (
              <Link className="button" to={`/tasks/${existingTask}`}>
                {t('viewResult')}
              </Link>
            )}
            {preview && (
              <div className={styles['pr-preview']}>
                <span className="eyebrow">
                  {preview.pr.repoFullName} #{preview.pr.prNumber}
                </span>
                <h3>{preview.pr.title}</h3>
                <p>
                  {preview.pr.author} · {preview.pr.headBranch} →{' '}
                  {preview.pr.baseBranch} ·{' '}
                  <code>{preview.pr.headSha.slice(0, 8)}</code>
                </p>
                <p>
                  <span className="positive">+{preview.pr.addLines}</span>{' '}
                  <span className="negative">−{preview.pr.deleteLines}</span> ·{' '}
                  {preview.pr.fileCount} {t('changedFiles')}
                </p>
                <ExternalLink url={normalizePrUrl(url)}>
                  {t('github')}
                </ExternalLink>
                {preview.reusableTask && (
                  <Notice>
                    {t('reusable')}{' '}
                    <Link to={`/tasks/${preview.reusableTask.taskId}`}>
                      {t('viewResult')}
                    </Link>
                  </Notice>
                )}
                <label className="check-label">
                  <input
                    type="checkbox"
                    checked={consent}
                    onChange={(event) => setConsent(event.target.checked)}
                  />
                  {t('privacy')}
                </label>
              </div>
            )}
            <p className={styles['privacy-note']}>
              <Icon name="shield" size={16} />
              {t('privacy')}
            </p>
          </form>
        </section>
        <figure className={styles['review-scene']}>
          <div className={styles['review-scene-image']}>
            <img
              src="/images/review-desk.webp"
              alt={t('deskImageAlt')}
              width="1122"
              height="1402"
              fetchPriority="high"
            />
            <div className={styles['scene-note']} aria-hidden="true">
              <span>↳</span> Read between
              <br />
              the lines.
            </div>
            <span className={styles['scene-corner']} aria-hidden="true">
              PR / 01
            </span>
          </div>
          <figcaption>
            <span>{t('deskCaption')}</span>
            <span className={styles['scene-caption-index']}>
              FIG. 01 — REVIEW DESK
            </span>
          </figcaption>
        </figure>
      </div>
      <div className={styles['workflow-heading']}>
        <span>{t('workflowLabel')}</span>
        <span>01 — 03</span>
      </div>
      <div className={styles['feature-grid']}>
        {(['feature1', 'feature2', 'feature3'] as const).map((key, index) => (
          <article key={key}>
            <span className={styles['feature-number']}>0{index + 1}</span>
            <h3>{t(key)}</h3>
            <p>{t(`${key}Text`)}</p>
          </article>
        ))}
      </div>
      <section className={classNames('card', styles['open-task-card'])}>
        <div className={styles['open-task-intro']}>
          <span className={styles['section-index']}>02 /</span>
          <h2>{t('openTask')}</h2>
          <p>{t('loginHint')}</p>
        </div>
        <form
          className={styles['open-task-form']}
          onSubmit={(event) => {
            event.preventDefault()
            if (
              !/^[1-9]\d*$/.test(taskId) ||
              !Number.isSafeInteger(Number(taskId))
            ) {
              setTaskInvalid(true)
              return
            }
            navigate(
              `/tasks/${taskId}${query({ guestToken: guestToken.trim() || undefined })}`,
            )
          }}
        >
          <label className="field">
            <span>{t('taskId')}</span>
            <input
              type="number"
              min="1"
              step="1"
              required
              value={taskId}
              onChange={(event) => {
                setTaskId(event.target.value)
                setTaskInvalid(false)
              }}
            />
          </label>
          <label className="field">
            <span>{t('guestToken')}</span>
            <input
              type="password"
              autoComplete="off"
              value={guestToken}
              onChange={(event) => setGuestToken(event.target.value)}
            />
          </label>
          <button className="button">
            {t('open')}
            <Icon name="arrow" size={16} />
          </button>
        </form>
        {taskInvalid && <Notice danger>{t('invalidTask')}</Notice>}
      </section>
    </div>
  )
}
