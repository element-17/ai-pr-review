import styles from './ExternalLink.module.css'
import type { ReactNode } from 'react'
import { safeGithubUrl } from '../../lib/api'
import { Icon } from './Icon'

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
