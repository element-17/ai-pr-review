import { useState } from 'react'
import { classNames } from '../../lib/classNames'
import type { User } from '../../lib/types'
import styles from './UserAvatar.module.scss'

/** 接收可空用户、尺寸标记和类名；返回头像节点，图片不可用时显示姓名首字符。 */
export default function UserAvatar({ user, large = false, className }: {
  user: Pick<User, 'username' | 'nickname' | 'avatar'> | null
  large?: boolean
  className?: string
}) {
  const [failedUrl, setFailedUrl] = useState<string | null>(null)
  let avatarUrl: string | undefined
  try {
    if (user?.avatar) {
      const url = new URL(user.avatar, location.origin)
      if (['https:', 'http:'].includes(url.protocol)) avatarUrl = url.href
    }
  } catch { /* Invalid image URLs use the initials below. */ }
  const imageUrl = avatarUrl && avatarUrl !== failedUrl ? avatarUrl : undefined
  return (
    <span className={classNames(styles.avatar, large && styles.large, className)} aria-hidden="true">
      {imageUrl ? (
        <img src={imageUrl} alt="" referrerPolicy="no-referrer" onError={() => setFailedUrl(imageUrl)} />
      ) : (user?.nickname || user?.username || 'G').slice(0, 1).toUpperCase()}
    </span>
  )
}
