import { classNames } from '../lib/classNames'
import styles from './Compare.module.scss'
import { useState } from 'react'
import { comparisonEnabled, query } from '../lib/api'
import { navigate, useCollection, useFormatting, useResource, useSession } from '../hooks'
import { usePreferences } from '../lib/preferences'
import type { Comparison, Task } from '../lib/types'
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

/** 接收任务上下文和版本查询串；返回同一 PR 的版本选择、统计及问题差异页面节点。 */
export default function Compare({
  task,
  taskPath,
  search,
}: WorkspaceProps & { search: string }) {
  const { t } = usePreferences()
  const { number, dateTime } = useFormatting()
  const session = useSession()
  const params = new URLSearchParams(search)
  const baseId = Number(params.get('base')) || task.taskId
  const targetId = Number(params.get('target')) || undefined
  const [invalid, setInvalid] = useState(false)
  const tasks = useCollection<Task>(
    session && !task.isGuest
      ? `/tasks${query({ repo: task.pr.repoFullName, prNumber: task.pr.prNumber, sort: 'createTime_desc' })}`
      : null,
    100,
  )
  const eligible =
    tasks.data?.records.filter((item) => [3, 4].includes(item.status)) || []
  const comparison = useResource<Comparison>(
    session && !task.isGuest && comparisonEnabled && targetId && baseId !== targetId
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
                #{item.taskId} · {item.headSha.slice(0, 8)} · {dateTime(item.createTime)}
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
                #{item.taskId} · {item.headSha.slice(0, 8)} · {dateTime(item.createTime)}
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
          <Notice>
            {t('matchingWarning')}
            {result.matching.note && <p className="preserve-lines">{result.matching.note}</p>}
          </Notice>
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
                <Badge group="status" value={result[key].status} />
                {result[key].stats && (
                  <>
                    <dl className={styles['version-stats']}>
                      {(['total', 'pending', 'accepted', 'resolved', 'falsePositive'] as const).map((stat, index) => (
                        <div key={stat}>
                          <dt>{t(index === 0 ? 'issues' : `feedback${index}` as 'feedback1' | 'feedback2' | 'feedback3' | 'feedback4')}</dt>
                          <dd>{number(result[key].stats!.issues[stat])}</dd>
                        </div>
                      ))}
                    </dl>
                    <div className="actions wrap">
                      {(['low', 'medium', 'high', 'critical'] as const).map((severity, index) => (
                        <span key={severity}><Badge group="severity" value={index + 1} /> {number(result[key].stats!.issues.bySeverity[severity])}</span>
                      ))}
                    </div>
                  </>
                )}
              </div>
            ))}
          </div>
          <section className={classNames('card', styles['comparison-summary'])}>
            {(['purpose', 'changes', 'impacts'] as const).map((key) => (
              <div key={key}>
                <h3>{t(key)} <span className={styles['change-label']} data-changed={result.summary[key].changed}>
                  {t(result.summary[key].changed ? 'summaryChanged' : 'summaryUnchanged')}
                </span></h3>
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
                    {number(result.issues[group.key].length)}
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
                            {t('lineShifted')}
                          </p>
                        )}
                        {'matchConfidence' in issue && issue.matchConfidence === 'low' && (
                          <p className="text-warning small-text">{t('lowMatchConfidence')}</p>
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
