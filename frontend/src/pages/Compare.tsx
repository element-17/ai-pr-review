import { classNames } from '../lib/classNames'
import styles from './Compare.module.css'
import { useState } from 'react'
import { comparisonEnabled, query } from '../lib/api'
import { navigate, useResource, useSession } from '../hooks'
import { usePreferences } from '../lib/preferences'
import type { Comparison, Page, Task } from '../lib/types'
import type { WorkspaceProps } from '../components/workspace/types'
import {
  Badge,
  DateText,
  Empty,
  Link,
  LoginPrompt,
  Notice,
  ResourceState,
} from '../components/ui'

export default function Compare({
  task,
  taskPath,
  search,
}: WorkspaceProps & { search: string }) {
  const { t } = usePreferences()
  const session = useSession()
  const params = new URLSearchParams(search)
  const baseId = Number(params.get('base')) || task.taskId
  const targetId = Number(params.get('target')) || undefined
  const [invalid, setInvalid] = useState(false)
  const tasks = useResource<Page<Task>>(
    session && !task.isGuest
      ? `/tasks${query({ repo: task.pr.repoFullName, prNumber: task.pr.prNumber, pageSize: 100 })}`
      : null,
  )
  const eligible =
    tasks.data?.records.filter((item) => [3, 4].includes(item.status)) || []
  const comparison = useResource<Comparison>(
    session && comparisonEnabled && targetId && baseId !== targetId
      ? `/tasks/${baseId}/compare/${targetId}`
      : null,
  )
  const result = comparison.data
  if (!session || task.isGuest) return <LoginPrompt />
  return (
    <div className="compare-page">
      <p className="muted">{t('compareDescription')}</p>
      {!comparisonEnabled && <Notice>{t('comparisonUnavailable')}</Notice>}
      <ResourceState
        loading={tasks.loading}
        error={tasks.error}
        retry={tasks.reload}
      />
      <form
        className={classNames('card', styles['compare-form'])}
        key={`${baseId}:${targetId}`}
        onSubmit={(event) => {
          event.preventDefault()
          const data = new FormData(event.currentTarget)
          if (data.get('base') === data.get('target')) {
            setInvalid(true)
            return
          }
          setInvalid(false)
          navigate(
            taskPath('/compare', {
              base: String(data.get('base')),
              target: String(data.get('target')),
            }),
          )
        }}
      >
        <label className="field">
          <span>{t('baseVersion')}</span>
          <select
            aria-label={t('baseVersion')}
            name="base"
            required
            defaultValue={baseId}
          >
            <option value="">{t('baseVersion')}</option>
            {eligible.map((item) => (
              <option value={item.taskId} key={item.taskId}>
                #{item.taskId} · {item.headSha.slice(0, 8)} · {item.createTime}
              </option>
            ))}
          </select>
        </label>
        <span className={styles['compare-arrow']}>→</span>
        <label className="field">
          <span>{t('targetVersion')}</span>
          <select
            aria-label={t('targetVersion')}
            name="target"
            required
            defaultValue={targetId || ''}
          >
            <option value="">{t('targetVersion')}</option>
            {eligible.map((item) => (
              <option value={item.taskId} key={item.taskId}>
                #{item.taskId} · {item.headSha.slice(0, 8)} · {item.createTime}
              </option>
            ))}
          </select>
        </label>
        <button
          className={classNames('button', 'primary')}
          disabled={!comparisonEnabled || comparison.loading}
        >
          {t('compareAction')}
        </button>
      </form>
      {invalid && <Notice danger>{t('sameVersion')}</Notice>}
      <ResourceState
        loading={comparison.loading}
        error={comparison.error}
        retry={comparison.reload}
      />
      {result && (
        <>
          <Notice>{t('matchingWarning')}</Notice>
          <div className={styles['comparison-versions']}>
            {(['base', 'target'] as const).map((key) => (
              <div className="card" key={key}>
                <span className="eyebrow">
                  {t(key === 'base' ? 'baseVersion' : 'targetVersion')}
                </span>
                <h2>
                  #{result[key].taskId} ·{' '}
                  <code>{result[key].headSha.slice(0, 12)}</code>
                </h2>
                <p className={classNames('muted', 'small-text')}>
                  <DateText value={result[key].createTime} /> ·{' '}
                  {t(
                    result[key].reportLanguage === 'zh-CN'
                      ? 'chinese'
                      : 'english',
                  )}
                </p>
              </div>
            ))}
          </div>
          <section className={classNames('card', styles['comparison-summary'])}>
            {(['purpose', 'changes', 'impacts'] as const).map((key) => (
              <div key={key}>
                <h3>{t(key)}</h3>
                <div className={styles['comparison-versions']}>
                  <p className="preserve-lines">
                    {result.summary[key].base || t('noSummary')}
                  </p>
                  <p className="preserve-lines">
                    {result.summary[key].target || t('noSummary')}
                  </p>
                </div>
              </div>
            ))}
          </section>
          <div className={styles['comparison-issues']}>
            {(
              [
                { key: 'added', title: 'addedIssues' },
                { key: 'remaining', title: 'remainingIssues' },
                { key: 'resolved', title: 'resolvedIssues' },
              ] as const
            ).map((group) => (
              <section className="card" key={group.key}>
                <div className="section-heading">
                  <h2>{t(group.title)}</h2>
                  <span className="count">
                    {result.issues[group.key].length}
                  </span>
                </div>
                {result.issues[group.key].length ? (
                  result.issues[group.key].map((issue, index) => {
                    const itemTask =
                      group.key === 'resolved' ? baseId : targetId
                    const id =
                      'targetIssueId' in issue
                        ? issue.targetIssueId
                        : issue.issueId
                    return (
                      <Link
                        className={styles['comparison-issue']}
                        key={index}
                        to={`/tasks/${itemTask}/issues?issueId=${id}`}
                      >
                        <Badge group="severity" value={issue.severity} />
                        <h3>{issue.title}</h3>
                        <code>
                          {issue.filePath || t('reportIssue')}
                          {issue.startLine ? `:${issue.startLine}` : ''}
                        </code>
                        {'lineShifted' in issue && issue.lineShifted && (
                          <p
                            className={classNames('text-warning', 'small-text')}
                          >
                            {t('matchingWarning')}
                          </p>
                        )}
                      </Link>
                    )
                  })
                ) : (
                  <Empty title="empty" />
                )}
              </section>
            ))}
          </div>
        </>
      )}
    </div>
  )
}
