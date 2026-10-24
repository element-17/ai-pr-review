import { classNames } from '../../lib/classNames'
import styles from './IssueEditor.module.css'
import { useState } from 'react'
import { api } from '../../lib/api'
import { usePreferences } from '../../lib/preferences'
import { useFormatting } from '../../hooks'
import type { IssueDetail, IssuePage } from '../../lib/types'
import type { WorkspaceProps } from '../workspace/types'
import {
  Badge,
  DateText,
  EnumText,
  ErrorNotice,
  ExternalLink,
  Icon,
  Link,
  Notice,
} from '../ui'

export default function IssueEditor({
  issue,
  taskId,
  guestToken,
  taskPath,
  returnTo,
  onSaved,
  onNext,
  readOnly = false,
}: {
  issue: IssueDetail
  taskId: number
  guestToken: string
  taskPath: WorkspaceProps['taskPath']
  returnTo: string | null
  onSaved: () => void
  onNext: (value: number) => void
  readOnly?: boolean
}) {
  const { t } = usePreferences()
  const { percent } = useFormatting()
  const [feedbackStatus, setFeedbackStatus] = useState(issue.feedbackStatus)
  const [remark, setRemark] = useState(issue.feedbackRemark || '')
  const [suggestion, setSuggestion] = useState(
    issue.suggestion || issue.aiSuggestion || '',
  )
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<unknown>(null)
  const [validation, setValidation] = useState<
    'reasonRequired' | 'suggestionRequired' | 'noPending' | null
  >(null)
  async function save(undo = false) {
    if (readOnly || busy) return
    setValidation(null)
    if (undo && !window.confirm(t('undoConfirm'))) return
    if (!undo && feedbackStatus === 4 && !remark.trim()) {
      setValidation('reasonRequired')
      return
    }
    if (!undo && !suggestion.trim()) {
      setValidation('suggestionRequired')
      return
    }
    setBusy(true)
    setError(null)
    try {
      const path = `/tasks/${taskId}/issues/${issue.issueId}`
      if (undo) await api.post(`${path}/undo`, undefined, { guestToken })
      else
        await api.patch(
          path,
          {
            feedbackStatus,
            feedbackRemark: remark.trim(),
            suggestion: suggestion.trim(),
          },
          { guestToken },
        )
      onSaved()
    } catch (caught) {
      setError(caught)
    } finally {
      setBusy(false)
    }
  }
  async function nextPending() {
    setBusy(true)
    setError(null)
    setValidation(null)
    try {
      const result = await api.get<IssuePage>(
        `/tasks/${taskId}/issues?feedbackStatus=1&pageSize=2&sort=severity_desc`,
        { guestToken },
      )
      const next = result.records.find((item) => item.issueId !== issue.issueId)
      if (next) onNext(next.issueId)
      else setValidation('noPending')
    } catch (caught) {
      setError(caught)
    } finally {
      setBusy(false)
    }
  }
  const diffPath = returnTo?.startsWith(`/tasks/${taskId}/files?`)
    ? returnTo
    : taskPath('/files', {
        fileId: issue.file?.fileId,
        line: issue.startLine || undefined,
        issueId: issue.issueId,
      })
  return (
    <>
      <div className="actions">
        <Badge group="severity" value={issue.severity} />
        <span className="muted small-text">
          {t('confidence')} {percent(issue.confidence)}
        </span>
      </div>
      <h2 className={styles['issue-title']}>{issue.title}</h2>
      <p className="muted">
        <EnumText group="category" value={issue.category} />
      </p>
      {(!issue.evidence || issue.confidence < 70) && (
        <Notice>{t('manualCheck')}</Notice>
      )}
      <div className={styles['issue-location']}>
        <code>
          {issue.file?.filePath || t('reportIssue')}
          {issue.startLine
            ? `:${issue.startLine}–${issue.endLine || issue.startLine}`
            : ''}
        </code>
        <div className="actions">
          {issue.file && issue.startLine ? (
            <Link className="text-link" to={diffPath}>
              <Icon name="code" size={15} />
              {t('locate')}
            </Link>
          ) : (
            <span className={classNames('muted', 'small-text')}>
              {t('noLocation')}
            </span>
          )}
          <ExternalLink url={issue.githubUrl}>{t('github')}</ExternalLink>
        </div>
      </div>
      {(['triggerCondition', 'impact', 'evidence'] as const).map((key) => (
        <section className={styles['detail-section']} key={key}>
          <h3>{t(key)}</h3>
          {key === 'evidence' ? (
            <pre className={styles['evidence']}>{issue[key] || '—'}</pre>
          ) : (
            <p className="preserve-lines">{issue[key] || '—'}</p>
          )}
        </section>
      ))}
      <form
        onSubmit={(event) => {
          event.preventDefault()
          void save()
        }}
      >
        {readOnly && <Notice>{t('expiredReadOnly')}</Notice>}
        <fieldset disabled={busy || readOnly}>
          <label className="field">
            <span>{t('suggestion')}</span>
            <textarea
              required
              rows={6}
              maxLength={5000}
              value={suggestion}
              onChange={(event) => setSuggestion(event.target.value)}
            />
          </label>
          {issue.aiSuggestion && (
            <details className={styles['original-suggestion']}>
              <summary>{t('aiSuggestion')}</summary>
              <p
                className={classNames('preserve-lines', 'muted', 'small-text')}
              >
                {issue.aiSuggestion}
              </p>
            </details>
          )}
          <label className="field">
            <span>{t('feedback')}</span>
            <select
              aria-label={t('feedback')}
              value={feedbackStatus}
              onChange={(event) =>
                setFeedbackStatus(Number(event.target.value))
              }
            >
              {[1, 2, 3, 4].map((value) => (
                <option value={value} key={value}>
                  <EnumText group="feedback" value={value} />
                </option>
              ))}
            </select>
          </label>
          <label className="field">
            <span>
              {t('feedbackRemark')}
              {feedbackStatus === 4 ? ' *' : ''}
            </span>
            <textarea
              rows={3}
              maxLength={500}
              required={feedbackStatus === 4}
              value={remark}
              onChange={(event) => setRemark(event.target.value)}
            />
          </label>
          {validation && (
            <Notice danger={validation !== 'noPending'}>{t(validation)}</Notice>
          )}
          {!!error && <ErrorNotice error={error} />}
          <div className={classNames('actions', 'wrap')}>
            <button className={classNames('button', 'primary')}>
              {busy ? t('saving') : t('save')}
            </button>
            <button
              className="button"
              type="button"
              onClick={() => void save(true)}
            >
              {t('undo')}
            </button>
          </div>
        </fieldset>
        <button className="button" type="button" disabled={busy} onClick={() => void nextPending()}>
          {t('nextPending')}<Icon name="arrow" size={15} />
        </button>
      </form>
      {issue.feedbackTime && (
        <p className={classNames('muted', 'small-text')}>
          {t('lastFeedback')}:{' '}
          {issue.feedbackUserId ? `#${issue.feedbackUserId}` : t('guest')} ·{' '}
          <DateText value={issue.feedbackTime} />
        </p>
      )}
    </>
  )
}
