import styles from './PageHeading.module.css'
import type { ReactNode } from 'react'
import { usePreferences } from '../../lib/preferences'
import type { MessageKey } from '../../i18n/types'

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
