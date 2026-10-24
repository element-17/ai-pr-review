import { classNames } from '../../lib/classNames'
import styles from './Pagination.module.scss'
import { usePreferences } from '../../lib/preferences'

/** 接收当前页、总条数、每页条数、紧凑标记和翻页回调；返回分页控件节点。 */
export function Pagination({
  page,
  total,
  pageSize = 20,
  compact = false,
  onChange,
}: {
  page: number
  total: number
  pageSize?: number
  compact?: boolean
  onChange: (value: number) => void
}) {
  const { t, language } = usePreferences()
  const pages = Math.max(1, Math.ceil(total / pageSize))
  /** 接收数字；返回当前界面语言的数字文本。 */
  const number = (value: number) => new Intl.NumberFormat(language).format(value)
  return (
    <div className={classNames(styles['pagination'], compact && styles.compact)}>
      <span>
        {t('total')} {new Intl.NumberFormat(language).format(total)}{' '}
        {t('records')}
      </span>
      <div className="actions">
        <button
          className={classNames('button', 'small')}
          disabled={page <= 1}
          onClick={() => onChange(page - 1)}
        >
          {t('previous')}
        </button>
        <span>
          {number(page)} / {number(pages)}
        </span>
        <button
          className={classNames('button', 'small')}
          disabled={page >= pages}
          onClick={() => onChange(page + 1)}
        >
          {t('next')}
        </button>
      </div>
    </div>
  )
}
