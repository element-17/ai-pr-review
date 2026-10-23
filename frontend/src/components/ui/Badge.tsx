import { classNames } from '../../lib/classNames'
import styles from './Badge.module.css'
import { EnumText } from './EnumText'

export function Badge({
  group,
  value,
}: {
  group: 'status' | 'severity' | 'feedback' | 'fileStatus'
  value: number
}) {
  return (
    <span className={classNames(styles['badge'], styles[`${group}-${value}`])}>
      <span className={styles['badge-dot']} />
      <EnumText group={group} value={value} />
    </span>
  )
}
