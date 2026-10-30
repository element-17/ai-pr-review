import styles from './Empty.module.scss'
import type { ReactNode } from 'react'
import { usePreferences } from '../../lib/preferences'
import type { MessageKey } from '../../i18n/types'
import { Icon } from './Icon'

/** 接收标题键、可选说明键和子节点；返回带提示图标的空状态节点。 */
export function Empty({
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
    <div className={styles['empty-state']}>
      <div className={styles['empty-icon']}>
        <Icon name="branch" size={30} />
      </div>
      <h3>{t(title)}</h3>
      {description && <p>{t(description)}</p>}
      {children}
    </div>
  )
}
