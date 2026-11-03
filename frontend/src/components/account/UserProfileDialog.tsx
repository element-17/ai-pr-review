import { useEffect, useId, useRef } from 'react'
import { useResource } from '../../hooks'
import { usePreferences } from '../../lib/preferences'
import type { UserProfile } from '../../lib/types'
import { DateText, Link, ResourceState } from '../ui'
import UserAvatar from './UserAvatar'
import styles from './UserProfileDialog.module.scss'

/** 接收关闭回调；请求当前用户资料并返回可关闭的模态对话框节点。 */
export default function UserProfileDialog({ onClose }: { onClose: () => void }) {
  const { t } = usePreferences()
  const titleId = useId()
  const dialogRef = useRef<HTMLDialogElement>(null)
  const profile = useResource<UserProfile>('/users/me/profile')
  useEffect(() => {
    const dialog = dialogRef.current
    if (!dialog) return
    const previousFocus = document.activeElement
    const previousOverflow = document.body.style.overflow
    dialog.showModal()
    document.body.style.overflow = 'hidden'
    return () => {
      dialog.close()
      document.body.style.overflow = previousOverflow
      if (previousFocus instanceof HTMLElement && previousFocus.isConnected) previousFocus.focus()
    }
  }, [])
  const user = profile.data
  /** 接收可空资料文本；返回去除首尾空白的内容，空值返回“未提供”文案。 */
  const value = (text: string | null | undefined) => text?.trim() || t('notProvided')
  return (
    <dialog
      id="user-profile-dialog"
      ref={dialogRef}
      className={styles.dialog}
      aria-labelledby={titleId}
      onCancel={event => { event.preventDefault(); onClose() }}
      onClick={event => {
        if (event.target !== event.currentTarget) return
        const bounds = event.currentTarget.getBoundingClientRect()
        if (event.clientX < bounds.left || event.clientX > bounds.right || event.clientY < bounds.top || event.clientY > bounds.bottom) onClose()
      }}
    >
      <header className={styles.heading}>
        <h2 id={titleId}>{t('profileTitle')}</h2>
        <button type="button" className="button small" onClick={onClose}>{t('close')}</button>
      </header>
      <ResourceState loading={profile.loading} error={profile.error} retry={profile.reload} />
      {user && (
        <>
          <div className={styles.identity}>
            <UserAvatar user={user} large />
            <div><h3>{value(user.nickname || user.username)}</h3><p>@{user.username}</p></div>
          </div>
          <dl className={styles.details}>
            <div><dt>{t('userId')}</dt><dd>{user.id}</dd></div>
            <div><dt>{t('username')}</dt><dd>{value(user.username)}</dd></div>
            <div><dt>{t('profileNickname')}</dt><dd>{value(user.nickname)}</dd></div>
            <div><dt>{t('profileEmail')}</dt><dd>{value(user.email)}</dd></div>
            <div><dt>{t('phone')}</dt><dd>{value(user.phone)}</dd></div>
            <div><dt>{t('userRole')}</dt><dd>{user.role === 'ADMIN' ? t('roleAdmin') : user.role === 'USER' ? t('roleUser') : value(user.role)}</dd></div>
            <div><dt>{t('accountStatus')}</dt><dd>{user.status === 1 ? t('accountActive') : user.status === 0 ? t('accountInactive') : t('notProvided')}</dd></div>
            {([
              ['createTime', 'accountCreated'],
              ['lastLoginTime', 'lastLogin'],
              ['updateTime', 'profileUpdated'],
            ] as const).map(([field, label]) => (
              <div key={field}><dt>{t(label)}</dt><dd>{user[field] ? <DateText value={user[field]} /> : t('notProvided')}</dd></div>
            ))}
          </dl>
          <Link className="button" to="/settings" onClick={onClose}>{t('profileEdit')}</Link>
        </>
      )}
    </dialog>
  )
}
