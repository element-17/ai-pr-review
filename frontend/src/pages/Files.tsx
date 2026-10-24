import { classNames } from '../lib/classNames'
import styles from './Files.module.scss'
import { query } from '../lib/api'
import { navigate, useFormatting, useResource } from '../hooks'
import { usePreferences } from '../lib/preferences'
import type { FileDiff, Page, TaskFile } from '../lib/types'
import type { WorkspaceProps } from '../components/workspace/types'
import DiffViewer from '../components/diff/DiffViewer'
import {
  Badge,
  Empty,
  EnumText,
  ExternalLink,
  Icon,
  Link,
  Notice,
  Pagination,
  ResourceState,
} from '../components/ui'

/** 接收任务上下文、查询串和可选文件 ID；返回文件筛选列表与当前文件 Diff 页面节点。 */
export default function Files({
  task,
  guestToken,
  taskPath,
  search,
  fileId: routeFileId,
}: WorkspaceProps & { search: string; fileId?: number }) {
  const { t } = usePreferences()
  const { number } = useFormatting()
  const params = new URLSearchParams(search)
  const page = Math.max(1, Number(params.get('page')) || 1)
  const view = params.get('view') === 'split' ? 'split' : 'unified'
  const filters = {
    keyword: params.get('keyword') || '',
    status: params.get('status') || '',
    changeType: params.get('changeType') || '',
    priorityOnly: params.get('priorityOnly') || '',
    sort: params.get('sort') || 'path_asc',
    page,
    pageSize: 100,
  }
  const resource = useResource<Page<TaskFile>>(
    `/tasks/${task.taskId}/files${query(filters)}`,
    guestToken,
  )
  const fileId =
    routeFileId ||
    Number(params.get('fileId')) ||
    resource.data?.records[0]?.fileId
  const detail = useResource<FileDiff>(
    fileId ? `/tasks/${task.taskId}/files/${fileId}/diff` : null,
    guestToken,
  )
  /** 接收待覆盖或清除的查询参数；返回保留其余筛选条件的文件页地址。 */
  function filePath(values: Record<string, string | number | undefined>) {
    return taskPath('/files', { ...Object.fromEntries(params), ...values })
  }
  return (
    <div className="files-page">
      <form
        className="card filters file-filters"
        key={search}
        onSubmit={(event) => {
          event.preventDefault()
          const data = Object.fromEntries(
            new FormData(event.currentTarget).entries(),
          ) as Record<string, string>
          navigate(taskPath('/files', { ...data, page: 1, view }))
        }}
      >
        <label className="field search-field">
          <span>{t('fileSearch')}</span>
          <input
            name="keyword"
            maxLength={200}
            defaultValue={filters.keyword}
            placeholder={t('fileSearch')}
          />
        </label>
        {(
          [
            { name: 'status', group: 'fileStatus', values: [1, 2, 3, 4] },
            {
              name: 'changeType',
              group: 'changeType',
              values: [1, 2, 3, 4, 5],
            },
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
                <option key={value} value={value}>
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
            name="sort"
            defaultValue={filters.sort}
          >
            <option value="path_asc">{t('pathSort')}</option>
            <option value="addLines_desc">{t('addSort')}</option>
            <option value="deleteLines_desc">{t('deleteSort')}</option>
          </select>
        </label>
        <label className="check-label">
          <input
            type="checkbox"
            name="priorityOnly"
            value="true"
            defaultChecked={filters.priorityOnly === 'true'}
          />
          {t('priorityOnly')}
        </label>
        <button className="button primary" aria-label={t('search')}>
          <Icon name="search" size={17} />
        </button>
        <button
          type="button"
          className="button"
          onClick={() => navigate(taskPath('/files'))}
        >
          {t('reset')}
        </button>
      </form>
      <div className={styles['file-columns']}>
        <aside className={classNames('card', styles['file-tree'])}>
          <div className="section-heading">
            <h2>{t('changedFiles')}</h2>
            <span className="count">{resource.data ? number(resource.data.total) : '—'}</span>
          </div>
          <ResourceState
            loading={resource.loading}
            error={resource.error}
            retry={resource.reload}
          />
          {resource.data && (
            <>
              {resource.data.records.length ? (
                resource.data.records.map((file) => (
                  <Link
                    key={file.fileId}
                    className={classNames(
                      styles['file-tree-row'],
                      fileId === file.fileId ? styles['selected'] : '',
                    )}
                    to={filePath({
                      fileId: file.fileId,
                      line: undefined,
                      issueId: undefined,
                    })}
                  >
                    <Icon name="file" size={17} />
                    <div>
                      <code>{file.filePath}</code>
                      <div className={styles['file-tree-meta']}>
                        <span className="positive">+{number(file.addLines)}</span>
                        <span className="negative">−{number(file.deleteLines)}</span>
                        <span>
                          <EnumText group="fileStatus" value={file.status} />
                        </span>
                        {file.priorityReason && (
                          <span title={file.priorityReason}>★</span>
                        )}
                      </div>
                    </div>
                  </Link>
                ))
              ) : (
                <Empty title="noFiles" />
              )}
              <Pagination
                compact
                page={page}
                total={resource.data.total}
                pageSize={100}
                onChange={(value) =>
                  navigate(
                    filePath({
                      page: value,
                      fileId: undefined,
                      line: undefined,
                      issueId: undefined,
                    }),
                  )
                }
              />
            </>
          )}
        </aside>
        <section className={classNames('card', styles['diff-panel'])}>
          <ResourceState
            loading={detail.loading}
            error={detail.error}
            retry={detail.reload}
          />
          {!fileId && !resource.loading && <Empty title="noFiles" />}
          {detail.data && (
            <>
              <div className={styles['diff-heading']}>
                <div>
                  <h2>
                    <code>{detail.data.filePath}</code>
                  </h2>
                  <p className={classNames('muted', 'small-text')}>
                    <EnumText
                      group="changeType"
                      value={detail.data.changeType}
                    />{' '}
                    · {detail.data.language || '—'} ·{' '}
                    <span className="positive">+{number(detail.data.addLines)}</span>{' '}
                    <span className="negative">−{number(detail.data.deleteLines)}</span>
                  </p>
                  {detail.data.oldPath && (
                    <p className={classNames('muted', 'small-text')}>
                      {t('oldPath')}: <code>{detail.data.oldPath}</code>
                    </p>
                  )}
                </div>
                <div className={styles['segmented']}>
                  {(['unified', 'split'] as const).map((value) => (
                    <Link
                      key={value}
                      className={view === value ? styles['active'] : ''}
                      to={filePath({ view: value })}
                    >
                      {t(value)}
                    </Link>
                  ))}
                </div>
              </div>
              <div className={styles['file-description']}>
                <Badge group="fileStatus" value={detail.data.status} />
                {detail.data.skipReason && (
                  <Notice>{detail.data.skipReason}</Notice>
                )}
                {detail.data.priorityReason && (
                  <p>{detail.data.priorityReason}</p>
                )}
                {detail.data.summary && (
                  <p className="preserve-lines">{detail.data.summary}</p>
                )}
                <ExternalLink url={detail.data.githubUrl}>
                  {t('github')}
                </ExternalLink>
              </div>
              {detail.data.isBinary ? (
                <Empty title="binary" />
              ) : !detail.data.patchAvailable || !detail.data.rows.length ? (
                <Empty title="noPatch" />
              ) : (
                <DiffViewer
                  key={`${task.taskId}:${fileId}:${view}`}
                  file={detail.data}
                  view={view}
                  focusLine={Number(params.get('line')) || undefined}
                  taskPath={taskPath}
                  returnTo={filePath({ fileId })}
                />
              )}
              {detail.data.issues.length > 0 && (
                <div className={styles['related-issues']}>
                  <h3>{t('relatedIssues')}</h3>
                  {detail.data.issues.map((issue) => (
                    <Link
                      className={styles['related-issue']}
                      key={issue.issueId}
                      to={taskPath('/issues', {
                        issueId: issue.issueId,
                        returnTo: filePath({
                          fileId,
                          line: issue.startLine || undefined,
                          issueId: issue.issueId,
                        }),
                      })}
                    >
                      <Badge group="severity" value={issue.severity} />
                      <span>{issue.title}</span>
                      <Icon name="arrow" size={15} />
                    </Link>
                  ))}
                </div>
              )}
            </>
          )}
        </section>
      </div>
    </div>
  )
}
