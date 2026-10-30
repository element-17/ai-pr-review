import { classNames } from '../../lib/classNames'
import styles from './Badge.module.scss'
import { EnumText } from './EnumText'

/** 接收枚举分组和值；返回对应颜色和本地化文案的徽标节点。 */
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
