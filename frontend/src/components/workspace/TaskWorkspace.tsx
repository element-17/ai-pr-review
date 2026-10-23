import { classNames } from '../../lib/classNames'
import styles from './TaskWorkspace.module.css'
import { useRef, useState } from 'react'
import type { ReactNode } from 'react'
import { analysisEnabled, api, deletionEnabled, query } from '../../lib/api'
import { navigate, useResource, useSession, useTaskProgress } from '../../hooks'
import { usePreferences } from '../../lib/preferences'
import type { CreatedTask, TaskDetail } from '../../lib/types'
import type { MessageKey } from '../../i18n/types'
import type { WorkspaceProps } from './types'
import {
  Badge,
  DateText,
  EnumText,
  ErrorNotice,
  ExternalLink,
  Icon,
  Link,
  Notice,
  ResourceState,
} from '../ui'

export default function TaskWorkspace({
  taskId,
  guestToken,
  tab,
  children,
}: {
  taskId: number
  guestToken: string
  tab: string
  children: (props: WorkspaceProps) => ReactNode
}) {
  const { t } = usePreferences()
  const session = useSession()
  const retryKey = useRef<string | null>(null)
  const resource = useResource<TaskDetail>(`/tasks/${taskId}`, guestToken)
  const [actionError, setActionError] = useState<unknown>(null)
  const [busy, setBusy] = useState(false)
  const [copied, setCopied] = useState<'copied' | 'copyFailed' | null>(null)
  const task = resource.data
  const reloadTask = resource.reload
  const isRunning = !!task && [1, 2].includes(task.status)
  const { progress, error: pollError, retry: retryPolling } = useTaskProgress(taskId, isRunning, guestToken, reloadTask)
  const taskPath: WorkspaceProps['taskPath'] = (suffix = '', params = {}) =>
    `/tasks/${taskId}${suffix}${query({ guestToken: guestToken || undefined, ...params })}`
  async function action(type: 'export' | 'cancel' | 'retry' | 'delete') {
    if (type === 'delete' && !window.confirm(t('deleteConfirm'))) return
    setBusy(true)
    setActionError(null)
    try {
      if (type === 'export') {
        const markdown = await api.get<string>(
          `/tasks/${taskId}/export?format=markdown`,
          { guestToken, raw: true },
        )
        const objectUrl = URL.createObjectURL(
          new Blob([markdown], { type: 'text/markdown;charset=utf-8' }),
        )
        const anchor = document.createElement('a')
        anchor.href = objectUrl
        anchor.download = `pr-review-${taskId}.md`
        anchor.click()
        setTimeout(() => URL.revokeObjectURL(objectUrl), 1000)
      } else if (type === 'retry') {
        retryKey.current ||= crypto.randomUUID()
        const result = await api.post<CreatedTask>(
          `/tasks/${taskId}/retry`,
          undefined,
          { guestToken, idempotencyKey: retryKey.current },
        )
        navigate(
          `/tasks/${result.taskId}${query({ guestToken: result.guestToken || undefined })}`,
        )
      } else if (type === 'delete') {
        await api.delete(`/tasks/${taskId}`)
        navigate('/tasks')
      } else {
        await api.post(`/tasks/${taskId}/cancel`, undefined, { guestToken })
        resource.reload()
      }
    } catch (error) {
      setActionError(error)
    } finally {
      setBusy(false)
    }
  }
  if (!task)
    return (
      <ResourceState
        loading={resource.loading}
        error={resource.error}
        retry={resource.reload}
      />
    )
  const current = isRunning && progress?.taskId === taskId ? progress : task
  return (
    <>
      <div className={styles['breadcrumbs']}>
        <Link to="/tasks">{t('tasks')}</Link>
        <Icon name="chevron" size={13} />
        <span>#{taskId}</span>
      </div>
      <header className={styles['review-header']}>
        <div>
          <div className="actions">
            <span className="eyebrow">
              {task.pr.repoFullName} #{task.pr.prNumber}
            </span>
            <Badge group="status" value={current.status} />
          </div>
          <h1>{task.pr.title}</h1>
          <div className={styles['review-meta']}>
            <code title={task.headSha}>{task.headSha.slice(0, 12)}</code>
            <span>
              {t('reportLanguage')}:{' '}
              {t(task.reportLanguage === 'zh-CN' ? 'chinese' : 'english')}
            </span>
            <span>
              <DateText value={task.createTime} />
            </span>
          </div>
        </div>
        <div className="actions">
          <button
            className="button"
            disabled={busy || !task.hasReport}
            onClick={() => void action('export')}
          >
            <Icon name="download" size={17} />
            {t('export')}
          </button>
          <button
            className="icon-button"
            aria-label={t('refresh')}
            onClick={resource.reload}
          >
            <Icon name="history" size={18} />
          </button>
        </div>
      </header>
      {task.isExpired && <Notice>{t('expiredHint')}</Notice>}
      {[4, 5, 6].includes(current.status) && (
        <Notice danger={current.status === 5}>
          {t(
            current.status === 4
              ? 'partialHint'
              : current.status === 5
                ? 'failedHint'
                : 'cancelledHint',
          )}
          {!!current.errorCode &&
            current.errorCode >= 1 &&
            current.errorCode <= 8 && (
              <p>{t(`taskError${current.errorCode}` as MessageKey)}</p>
            )}
          {current.errorMessage && (
            <p className="preserve-lines">{current.errorMessage}</p>
          )}
        </Notice>
      )}
      {isRunning && (
        <div className={classNames('card', styles['progress-card'])}>
          <div className="spread">
            <strong>
              <EnumText group="stage" value={current.stage} />
            </strong>
            <span>{current.progress}%</span>
          </div>
          <progress
            value={current.progress}
            max="100"
            aria-label={t('status')}
          />
          <div className={styles['stages']}>
            {[1, 2, 3, 4, 5, 6, 7].map((value) => (
              <span
                key={value}
                className={value <= current.stage ? styles['active'] : ''}
              >
                <EnumText group="stage" value={value} />
              </span>
            ))}
          </div>
        </div>
      )}
      {!!pollError && (
        <ErrorNotice
          error={pollError}
          retry={retryPolling}
        />
      )}
      {!!actionError && <ErrorNotice error={actionError} />}
      <nav className={styles['review-tabs']} aria-label={t('workspace')}>
        {(
          [
            { key: 'overview', suffix: '', icon: 'branch' },
            { key: 'files', suffix: '/files', icon: 'code' },
            { key: 'issues', suffix: '/issues', icon: 'shield' },
            { key: 'compare', suffix: '/compare', icon: 'compare' },
          ] as const
        ).map((item) => (
          <Link
            key={item.key}
            to={taskPath(item.suffix)}
            aria-current={tab === item.key ? 'page' : undefined}
          >
            <Icon name={item.icon} size={17} />
            {t(item.key)}
            {item.key === 'issues' && (
              <span className="count">{task.stats.issues.total}</span>
            )}
          </Link>
        ))}
      </nav>
      <ResourceState error={resource.error} retry={resource.reload} />
      {children({ task, guestToken, taskPath, onUpdate: resource.reload })}
      <footer className={styles['task-footer']}>
        <ExternalLink
          url={`https://github.com/${task.pr.repoFullName}/pull/${task.pr.prNumber}`}
        >
          {t('github')}
        </ExternalLink>
        <div className="actions">
          {guestToken && (
            <button
              className={classNames('button', 'small')}
              onClick={async () => {
                try {
                  await navigator.clipboard.writeText(
                    `${location.origin}${taskPath()}`,
                  )
                  setCopied('copied')
                } catch {
                  setCopied('copyFailed')
                }
              }}
            >
              {t(copied || 'share')}
            </button>
          )}
          {task.canCancel && (
            <button
              className={classNames('button', 'small')}
              disabled={!analysisEnabled || busy}
              title={!analysisEnabled ? t('analysisUnavailable') : undefined}
              onClick={() => void action('cancel')}
            >
              {t('cancel')}
            </button>
          )}
          {task.canRetry && (
            <button
              className={classNames('button', 'small')}
              disabled={!analysisEnabled || busy}
              title={!analysisEnabled ? t('analysisUnavailable') : undefined}
              onClick={() => void action('retry')}
            >
              {t('retry')}
            </button>
          )}
          {task.canDelete && session && !task.isGuest && (
            <button
              className={classNames('button', 'small', 'danger-text')}
              disabled={!deletionEnabled || busy}
              title={!deletionEnabled ? t('deletionUnavailable') : undefined}
              onClick={() => void action('delete')}
            >
              {t('delete')}
            </button>
          )}
        </div>
      </footer>
    </>
  )
}
