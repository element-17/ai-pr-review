import styles from './PageHeading.module.scss'
import type { ReactNode } from 'react'
import { usePreferences } from '../../lib/preferences'
import type { MessageKey } from '../../i18n/types'

/** 接收标题键、可选说明键和操作子节点；返回页面标题区域节点。 */
export function PageHeading({
  title,
  description,
  children,
}: {
  title: MessageKey
  description?: MessageKey
  children?: ReactNode
}) {
  const { t } = usePreferences()
  return (
    <div className={styles['page-heading']}>
      <div>
        <h1>{t(title)}</h1>
        {description && <p>{t(description)}</p>}
      </div>
      {children}
    </div>
  )
}
