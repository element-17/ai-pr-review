import { classNames } from '../lib/classNames'
import styles from './Issues.module.css'
import { query } from '../lib/api'
import { navigate, useCollection, useFormatting, useResource } from '../hooks'
import { usePreferences } from '../lib/preferences'
import type { IssueDetail, IssuePage, TaskFile } from '../lib/types'
import type { WorkspaceProps } from '../components/workspace/types'
import IssueEditor from '../components/issues/IssueEditor'
import {
  Badge,
  Empty,
  EnumText,
  Icon,
  Link,
  Pagination,
  ResourceState,
} from '../components/ui'

export default function Issues({
  task,
  guestToken,
  taskPath,
  onUpdate,
  search,
  issueId,
}: WorkspaceProps & { search: string; issueId?: number }) {
  const { t } = usePreferences()
  const { number, percent } = useFormatting()
  const params = new URLSearchParams(search)
  const page = Math.max(1, Number(params.get('page')) || 1)
  const selected = issueId || Number(params.get('issueId')) || undefined
  const filterValues = {
    keyword: params.get('keyword') || '',
    severity: params.get('severity') || '',
    category: params.get('category') || '',
    feedbackStatus: params.get('feedbackStatus') || '',
    fileId: params.get('fileId') || '',
    sort: params.get('sort') || 'severity_desc',
    page,
    pageSize: 20,
  }
  const resource = useResource<IssuePage>(
    `/tasks/${task.taskId}/issues${query(filterValues)}`,
    guestToken,
  )
  const files = useCollection<TaskFile>(
    `/tasks/${task.taskId}/files`,
    200,
    guestToken,
  )
  const detail = useResource<IssueDetail>(
    selected ? `/tasks/${task.taskId}/issues/${selected}` : null,
    guestToken,
  )
  function issuePath(value?: number) {
    return taskPath('/issues', {
      ...Object.fromEntries(params.entries()),
      issueId: value,
    })
  }
  return (
    <div className="issues-page">
      <form
        className={classNames('card', 'filters', styles['issue-filters'])}
        key={search}
        onSubmit={(event) => {
          event.preventDefault()
          const data = Object.fromEntries(
            new FormData(event.currentTarget).entries(),
          ) as Record<string, string>
          navigate(taskPath('/issues', { ...data, page: 1 }))
        }}
      >
        <label className={classNames('field', 'search-field')}>
          <span>{t('keyword')}</span>
          <input
            name="keyword"
            maxLength={200}
            defaultValue={params.get('keyword') || ''}
            placeholder={t('keyword')}
          />
        </label>
        {(
          [
            { name: 'severity', group: 'severity', values: [4, 3, 2, 1] },
            {
              name: 'category',
              group: 'category',
              values: [1, 2, 3, 4, 5, 6, 7],
            },
            { name: 'feedbackStatus', group: 'feedback', values: [1, 2, 3, 4] },
          ] as const
        ).map((filter) => (
          <label className="field" key={filter.name}>
            <span>{t(filter.group)}</span>
            <select
              aria-label={t(filter.group)}
              name={filter.name}
              defaultValue={params.get(filter.name) || ''}
            >
              <option value="">{t('all')}</option>
              {filter.values.map((value) => (
                <option value={value} key={value}>
                  <EnumText group={filter.group} value={value} />
                </option>
              ))}
            </select>
          </label>
        ))}
        <label className="field">
          <span>{t('file')}</span>
          <select
            aria-label={t('file')}
            name="fileId"
            defaultValue={params.get('fileId') || ''}
          >
            <option value="">{t('all')}</option>
            {files.data?.records.map((file) => (
              <option key={file.fileId} value={file.fileId}>
                {file.filePath}
              </option>
            ))}
            {params.get('fileId') &&
              !files.data?.records.some(
                (file) => String(file.fileId) === params.get('fileId'),
              ) && (
                <option value={params.get('fileId') || ''}>
                  #{params.get('fileId')}
                </option>
              )}
          </select>
        </label>
        <label className="field">
          <span>{t('created')}</span>
          <select
            aria-label={t('created')}
            name="sort"
            defaultValue={params.get('sort') || 'severity_desc'}
          >
            <option value="severity_desc">{t('severityDesc')}</option>
            <option value="severity_asc">{t('severityAsc')}</option>
            <option value="createTime_desc">{t('newest')}</option>
            <option value="createTime_asc">{t('oldest')}</option>
          </select>
        </label>
        <div className="actions">
          <button
            className={classNames('button', 'primary')}
            aria-label={t('search')}
          >
            <Icon name="search" size={17} />
          </button>
          <button
            className="button"
            type="button"
            onClick={() => navigate(taskPath('/issues'))}
          >
            {t('reset')}
          </button>
        </div>
      </form>
      <ResourceState loading={files.loading} error={files.error} retry={files.reload} />
      {filterValues.fileId && (
        <p className={classNames('muted', 'small-text')}>
          {t('file')}: #{filterValues.fileId}
        </p>
      )}
      <ResourceState
        loading={resource.loading}
        error={resource.error}
        retry={resource.reload}
      />
      {resource.data && (
        <div className={classNames('spread', styles['issue-progress'])}>
          <p>
            {t('reviewed')}{' '}
            <strong>
              {number(resource.data.stats.total - resource.data.stats.pending)}
            </strong>{' '}
            / {number(resource.data.stats.total)}
          </p>
          <span className={classNames('muted', 'small-text')}>
            {t('feedback1')}: {number(resource.data.stats.pending)}
          </span>
        </div>
      )}
      <div
        className={classNames(
          styles['issue-columns'],
          selected ? styles['has-detail'] : '',
        )}
      >
        <section className={classNames('card', styles['issue-list'])}>
          {resource.data &&
            (resource.data.records.length ? (
              resource.data.records.map((issue) => (
                <Link
                  className={classNames(
                    styles['issue-row'],
                    selected === issue.issueId ? styles['selected'] : '',
                  )}
                  key={issue.issueId}
                  to={issuePath(issue.issueId)}
                >
                  <div className="spread">
                    <Badge group="severity" value={issue.severity} />
                    <Badge group="feedback" value={issue.feedbackStatus} />
                  </div>
                  <h3>{issue.title}</h3>
                  <p>
                    <code>
                      {issue.filePath || t('reportIssue')}
                      {issue.startLine ? `:${issue.startLine}` : ''}
                    </code>
                  </p>
                  <div className={classNames('spread', 'muted', 'small-text')}>
                    <span>
                      <EnumText group="category" value={issue.category} />
                    </span>
                    <span>
                      {t('confidence')} {percent(issue.confidence)}
                    </span>
                  </div>
                </Link>
              ))
            ) : (
              <Empty title="noIssues" description="noIssuesHint" />
            ))}
          {resource.data && (
            <Pagination
              page={page}
              total={resource.data.total}
              onChange={(value) => {
                params.set('page', String(value))
                params.delete('issueId')
                navigate(taskPath('/issues', Object.fromEntries(params)))
              }}
            />
          )}
        </section>
        {selected && (
          <aside className={classNames('card', styles['issue-detail'])}>
            <div className="section-heading">
              <h2>
                {t('issues')} #{selected}
              </h2>
              <Link className={classNames('button', 'small')} to={issuePath()}>
                {t('back')}
              </Link>
            </div>
            <ResourceState
              loading={detail.loading}
              error={detail.error}
              retry={detail.reload}
            />
            {detail.data && (
              <IssueEditor
                key={`${detail.data.issueId}:${detail.data.updateTime}`}
                issue={detail.data}
                guestToken={guestToken}
                taskId={task.taskId}
                readOnly={task.isExpired}
                taskPath={taskPath}
                returnTo={params.get('returnTo')}
                onSaved={() => {
                  resource.reload()
                  detail.reload()
                  onUpdate()
                }}
                onNext={(value) => navigate(issuePath(value))}
              />
            )}
          </aside>
        )}
      </div>
    </div>
  )
}
