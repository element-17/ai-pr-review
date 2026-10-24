import { classNames } from '../../lib/classNames'
import styles from './Notice.module.scss'
import type { ReactNode } from 'react'
import { Icon } from './Icon'

/** 接收提示内容和危险标记；返回普通状态或错误警告节点。 */
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
