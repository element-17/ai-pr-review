import styles from './ExternalLink.module.scss'
import type { ReactNode } from 'react'
import { safeGithubUrl } from '../../lib/api'
import { Icon } from './Icon'

/** 接收可空 URL 和链接内容；返回新窗口打开的 HTTPS GitHub 链接，无效地址返回 null。 */
export function ExternalLink({
  url,
  children,
}: {
  url?: string | null
  children: ReactNode
}) {
  const href = safeGithubUrl(url)
  return href ? (
    <a
      className={styles['external-link']}
      href={href}
      target="_blank"
      rel="noopener noreferrer"
    >
      {children}
      <Icon name="external" size={14} />
    </a>
  ) : null
}
