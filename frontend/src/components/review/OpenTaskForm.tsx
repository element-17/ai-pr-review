import { useState } from 'react'
import { classNames } from '../../lib/classNames'
import { query } from '../../lib/api'
import { positiveInteger } from '../../lib/routes'
import { getGuestAccess } from '../../lib/guestTasks'
import { navigate } from '../../hooks'
import { usePreferences } from '../../lib/preferences'
import { Icon, Notice } from '../ui'
import styles from './OpenTaskForm.module.css'

export default function OpenTaskForm({ guest = false }: { guest?: boolean }) {
  const { t } = usePreferences()
  const [taskId, setTaskId] = useState('')
  const [guestToken, setGuestToken] = useState('')
  const [invalid, setInvalid] = useState<'invalidTask' | 'guestAccessRequired' | null>(null)
  return (
    <form
      className={classNames(styles.form, guest && styles.guest)}
      onSubmit={event => {
        event.preventDefault()
        const id = positiveInteger(taskId)
        if (!id) { setInvalid('invalidTask'); return }
        const token = guestToken.trim() || (guest ? getGuestAccess(id)?.token : '')
        if (guest && !token) { setInvalid('guestAccessRequired'); return }
        setInvalid(null)
        navigate(`/tasks/${id}${query({ guestToken: token || undefined })}`)
      }}
    >
      <label className="field">
        <span>{t('taskId')}</span>
        <input type="number" min="1" step="1" required value={taskId} onChange={event => { setTaskId(event.target.value); setInvalid(null) }} />
      </label>
      <label className="field">
        <span>{t(guest ? 'guestTokenLabel' : 'guestToken')}</span>
        <input type="password" autoComplete="off" value={guestToken} aria-invalid={invalid === 'guestAccessRequired'} aria-describedby={invalid === 'guestAccessRequired' ? 'guest-access-error' : undefined} onChange={event => { setGuestToken(event.target.value); setInvalid(null) }} />
      </label>
      <button className="button">{t('open')}<Icon name="arrow" size={16} /></button>
      {invalid && <div id="guest-access-error" className={styles.error}><Notice danger>{t(invalid)}</Notice></div>}
    </form>
  )
}
