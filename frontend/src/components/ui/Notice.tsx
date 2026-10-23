import { classNames } from '../../lib/classNames'
import styles from './Notice.module.css'
import type { ReactNode } from 'react'
import { Icon } from './Icon'

export function Notice({
  children,
  danger = false,
}: {
  children: ReactNode
  danger?: boolean
}) {
  return (
    <div
      className={classNames(styles['notice'], danger ? styles['danger'] : '')}
      role={danger ? 'alert' : 'status'}
    >
      <Icon name="alert" size={18} />
      <div>{children}</div>
    </div>
  )
}
