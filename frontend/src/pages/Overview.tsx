import { classNames } from '../lib/classNames'
import styles from './Overview.module.css'
import { useResource } from '../hooks'
import { usePreferences } from '../lib/preferences'
import type { Report } from '../lib/types'
import type { WorkspaceProps } from '../components/workspace/types'
import {
  Badge,
  DateText,
  Empty,
  EnumText,
  Icon,
  Link,
  ResourceState,
} from '../components/ui'

export default function Overview({
  task,
  guestToken,
  taskPath,
}: WorkspaceProps) {
  const { t, language } = usePreferences()
  const report = useResource<Report>(
    task.hasReport ? `/tasks/${task.taskId}/report` : null,
    guestToken,
  )
  const result = report.data
  const stats = task.stats
  const number = (value: number) =>
    new Intl.NumberFormat(language).format(value)
  const severityCounts = [
    stats.issues.bySeverity.low,
    stats.issues.bySeverity.medium,
    stats.issues.bySeverity.high,
    stats.issues.bySeverity.critical,
  ]
  return (
    <div className="overview-content">
      <div className={styles['stat-grid']}>
        {[
          { key: 'changedFiles', value: stats.files.total, icon: 'file' },
          { key: 'issues', value: stats.issues.total, icon: 'shield' },
          { key: 'additions', value: task.pr.addLines, icon: 'plus' },
          { key: 'deletions', value: task.pr.deleteLines, icon: 'code' },
        ].map((item) => (
          <Link
            key={item.key}
            className={classNames('card', styles['stat-card'])}
            to={taskPath(item.key === 'issues' ? '/issues' : '/files')}
          >
            <span className="muted">
              <Icon name={item.icon as 'file'} size={17} />
              {t(item.key as 'files')}
            </span>
            <strong>{number(item.value)}</strong>
          </Link>
        ))}
      </div>
      <div className={styles['overview-columns']}>
        <div className="stack">
          <section className={classNames('card', styles['summary-card'])}>
            <h2>{t('overview')}</h2>
            <div className={styles['metadata']}>
              <span>
                {t('author')}: {task.pr.author}
              </span>
              <span>
                <EnumText group="prState" value={task.pr.state} />
              </span>
              <span>
                {task.pr.headBranch} → {task.pr.baseBranch}
              </span>
            </div>
            <ResourceState
              loading={report.loading}
              error={report.error}
              retry={report.reload}
            />
            {!task.hasReport && (
              <Empty title="noReport" description="noReportHint" />
            )}
            {result &&
              (['purpose', 'changes', 'impacts'] as const).map((key) => (
                <div className={styles['summary-section']} key={key}>
                  <h3>{t(key)}</h3>
                  <p className="preserve-lines">
                    {result[key] || t('noSummary')}
                  </p>
                </div>
              ))}
          </section>
          <section className="card">
            <div className="section-heading">
              <h2>{t('priorityFiles')}</h2>
              <Link to={taskPath('/files', { priorityOnly: 'true' })}>
                {t('files')} →
              </Link>
            </div>
            {task.priorityFiles.length ? (
              task.priorityFiles.map((file) => (
                <Link
                  className={styles['priority-row']}
                  to={taskPath('/files', { fileId: file.fileId })}
                  key={file.fileId}
                >
                  <Icon name="file" />
                  <div>
                    <code>{file.filePath}</code>
                    <p>{file.priorityReason}</p>
                  </div>
                  <span className="count">{file.issueCount}</span>
                  <Icon name="chevron" size={16} />
                </Link>
              ))
            ) : (
              <p className="muted">{t('empty')}</p>
            )}
          </section>
        </div>
        <aside className="stack">
          <section className="card">
            <h2>{t('riskDistribution')}</h2>
            <div className={styles['severity-bar']} aria-hidden="true">
              {severityCounts.map((value, index) => (
                <span
                  className={classNames(
                    'severity-fill',
                    styles[`severity-fill-${index + 1}`],
                  )}
                  key={index}
                  style={{ flex: value || 0.001 }}
                />
              ))}
            </div>
            <div className={styles['severity-list']}>
              {[4, 3, 2, 1].map((value) => (
                <Link to={taskPath('/issues', { severity: value })} key={value}>
                  <Badge group="severity" value={value} />
                  <strong>{severityCounts[value - 1]}</strong>
                </Link>
              ))}
            </div>
            <hr />
            <div className="spread">
              <h3>{t('reviewProgress')}</h3>
              <span>
                {stats.issues.total - stats.issues.pending}/{stats.issues.total}
              </span>
            </div>
            <progress
              aria-label={t('reviewProgress')}
              value={stats.issues.total - stats.issues.pending}
              max={Math.max(stats.issues.total, 1)}
            />
            <Link
              className="text-link"
              to={taskPath('/issues', { feedbackStatus: 1 })}
            >
              {t('feedback1')} ({stats.issues.pending}) →
            </Link>
          </section>
          <section className="card">
            <h2>{t('coverage')}</h2>
            <div className={styles['coverage-number']}>
              {stats.files.analyzed}
              <span> / {stats.files.total}</span>
            </div>
            <p className="muted">{t('analyzed')}</p>
            <div className={styles['coverage-counts']}>
              <span>
                {t('skipped')} <strong>{stats.files.skipped}</strong>
              </span>
              <span>
                {t('failed')} <strong>{stats.files.failed}</strong>
              </span>
            </div>
            <p className={classNames('small-text', 'muted')}>
              {t('coverageHint')}
            </p>
            <Link className="text-link" to={taskPath('/files', { status: 3 })}>
              {t('viewSkipped')} →
            </Link>
          </section>
          <section className={classNames('card', 'compact')}>
            <h3>{t('analysisConfig')}</h3>
            <p className={classNames('small-text', 'muted')}>
              {t('model')}: {task.modelName || '—'}
            </p>
            <p className={classNames('small-text', 'muted')}>
              {t('maxFiles')}: {task.analysisConfig.maxFileCount}
            </p>
            <p className={classNames('small-text', 'muted')}>
              {t('maxDiff')}: {task.analysisConfig.maxDiffLines}
            </p>
            <p className={classNames('small-text', 'muted')}>
              {task.analysisConfig.ignoreDirs.join(', ') || '—'}
            </p>
            <p className={classNames('small-text', 'muted')}>
              {t('finished')}: <DateText value={task.finishTime} />
            </p>
          </section>
        </aside>
      </div>
    </div>
  )
}
