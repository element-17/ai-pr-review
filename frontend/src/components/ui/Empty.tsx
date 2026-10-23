import styles from './Empty.module.css'
import type { ReactNode } from 'react'
import { usePreferences } from '../../lib/preferences'
import type { MessageKey } from '../../i18n/types'
import { Icon } from './Icon'

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
