import { classNames } from '../lib/classNames'
import styles from './Tasks.module.scss'
import { useState } from 'react'
import { query } from '../lib/api'
import { navigate, useResource, useSession } from '../hooks'
import { usePreferences } from '../lib/preferences'
import type { Page, Task } from '../lib/types'
import {
  Badge,
  DateText,
  Empty,
  EnumText,
  Icon,
  Link,
  LoginPrompt,
  Notice,
  PageHeading,
  Pagination,
  ResourceState,
} from '../components/ui'

/** 接收筛选查询串；返回登录用户的任务筛选与分页列表，游客显示登录提示节点。 */
export default function Tasks({ search }: { search: string }) {
  const { t } = usePreferences()
  const session = useSession()
  const params = new URLSearchParams(search)
  const page = Math.max(1, Number(params.get('page')) || 1)
  const [validation, setValidation] = useState<
    'repoRequired' | 'dateInvalid' | null
  >(null)
  const resource = useResource<Page<Task>>(
    session
      ? `/tasks${query({ repo: params.get('repo') || '', prNumber: params.get('prNumber') || '', status: params.get('status') || '', startDate: params.get('startDate') || '', endDate: params.get('endDate') || '', sort: params.get('sort') || 'createTime_desc', page, pageSize: 20 })}`
      : null,
  )
  /** 接收目标页码；保留筛选参数并导航到该页，无返回值。 */
  function changePage(value: number) {
    params.set('page', String(value))
    navigate(`/tasks?${params}`)
  }
  return (
    <>
      <PageHeading title="tasks">
        <Link data-tour="new-review" className="button primary" to="/">
          <Icon name="plus" size={17} />
          {t('home')}
        </Link>
      </PageHeading>
      {!session ? (
        <LoginPrompt />
      ) : (
        <>
          <form
            className={classNames('card', 'filters', styles['task-filters'])}
            key={search}
            onSubmit={(event) => {
              event.preventDefault()
              const data = new FormData(event.currentTarget)
              const values = Object.fromEntries(data.entries()) as Record<
                string,
                string
              >
              if (values.prNumber && !values.repo.trim()) {
                setValidation('repoRequired')
                return
              }
              if (
                values.startDate &&
                values.endDate &&
                values.startDate > values.endDate
              ) {
                setValidation('dateInvalid')
                return
              }
              setValidation(null)
              navigate(
                `/tasks${query({ ...values, repo: values.repo.trim(), page: 1 })}`,
              )
            }}
          >
            <label className="field">
              <span>{t('repo')}</span>
              <input
                name="repo"
                data-tour="task-filter"
                placeholder="owner/repo"
                maxLength={150}
                defaultValue={params.get('repo') || ''}
              />
            </label>
            <label className="field">
              <span>{t('prNumber')}</span>
              <input
                name="prNumber"
                type="number"
                min="1"
                defaultValue={params.get('prNumber') || ''}
              />
            </label>
            <label className="field">
              <span>{t('status')}</span>
              <select
                aria-label={t('status')}
                name="status"
                defaultValue={params.get('status') || ''}
              >
                <option value="">{t('all')}</option>
                {[1, 2, 3, 4, 5, 6].map((value) => (
                  <option value={value} key={value}>
                    <EnumText group="status" value={value} />
                  </option>
                ))}
              </select>
            </label>
            <label className="field">
              <span>{t('startDate')}</span>
              <input
                name="startDate"
                type="date"
                defaultValue={params.get('startDate') || ''}
              />
            </label>
            <label className="field">
              <span>{t('endDate')}</span>
              <input
                name="endDate"
                type="date"
                defaultValue={params.get('endDate') || ''}
              />
            </label>
            <label className="field">
              <span>{t('created')}</span>
              <select
                aria-label={t('created')}
                name="sort"
                defaultValue={params.get('sort') || 'createTime_desc'}
              >
                <option value="createTime_desc">{t('newest')}</option>
                <option value="createTime_asc">{t('oldest')}</option>
              </select>
            </label>
            <div className="actions">
              <button className={classNames('button', 'primary')}>
                <Icon name="search" size={16} />
                {t('search')}
              </button>
              <button
                type="button"
                className="button"
                onClick={() => {
                  setValidation(null)
                  navigate('/tasks')
                }}
              >
                {t('reset')}
              </button>
            </div>
          </form>
          {validation && <Notice danger>{t(validation)}</Notice>}
          <ResourceState
            loading={resource.loading}
            error={resource.error}
            retry={resource.reload}
          />
          {resource.data && (
            <section className={classNames('card', styles['task-list'])}>
              {!resource.data.records.length ? (
                <Empty title="noTasks" description="noTasksHint" />
              ) : (
                <div className={styles['table-scroll']}>
                  <table>
                    <thead>
                      <tr>
                        <th>{t('repo')} / Pull Request</th>
                        <th>{t('status')}</th>
                        <th>{t('commit')}</th>
                        <th>{t('reportLanguage')}</th>
                        <th>{t('created')}</th>
                        <th />
                      </tr>
                    </thead>
                    <tbody>
                      {resource.data.records.map((task) => (
                        <tr key={task.taskId}>
                          <td>
                            <span className={classNames('muted', 'small-text')}>
                              {task.pr.repoFullName} #{task.pr.prNumber}
                            </span>
                            <Link
                              className={styles['task-title']}
                              to={`/tasks/${task.taskId}`}
                            >
                              {task.pr.title}
                            </Link>
                          </td>
                          <td>
                            <Badge group="status" value={task.status} />
                          </td>
                          <td>
                            <code>{task.headSha.slice(0, 8)}</code>
                            {task.isExpired && (
                              <span
                                className={classNames(
                                  'text-warning',
                                  'small-text',
                                  'block',
                                )}
                              >
                                {t('expired')}
                              </span>
                            )}
                          </td>
                          <td>
                            {t(
                              task.reportLanguage === 'zh-CN'
                                ? 'chinese'
                                : 'english',
                            )}
                          </td>
                          <td className={classNames('muted', 'small-text')}>
                            <DateText value={task.createTime} />
                          </td>
                          <td>
                            <Link
                              className="icon-button"
                              aria-label={`${t('open')} #${task.taskId}`}
                              to={`/tasks/${task.taskId}`}
                            >
                              <Icon name="arrow" size={18} />
                            </Link>
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}
              <Pagination
                page={page}
                total={resource.data.total}
                onChange={changePage}
              />
            </section>
          )}
        </>
      )}
    </>
  )
}
