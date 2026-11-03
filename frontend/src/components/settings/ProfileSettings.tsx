import { useState, type FormEvent } from 'react'
import { api } from '../../lib/api'
import { getSession, setSession } from '../../lib/session'
import { useResource } from '../../hooks'
import { usePreferences } from '../../lib/preferences'
import type { UserProfile } from '../../lib/types'
import { ErrorNotice, Notice, ResourceState } from '../ui'

export default function ProfileSettings() {
  const { t } = usePreferences()
  const profile = useResource<UserProfile>('/users/me/profile')
  return <section className="card" id="settings-profile" tabIndex={-1} aria-labelledby="profile-settings-heading">
    <h2 id="profile-settings-heading">{t('profileSettings')}</h2>
    <ResourceState loading={profile.loading} error={profile.error} retry={profile.reload} />
    {profile.data && <ProfileForm key={`${profile.data.id}:${profile.data.updateTime || ''}`} initial={profile.data} />}
  </section>
}

function ProfileForm({ initial }: { initial: UserProfile }) {
  const { t } = usePreferences()
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<unknown>(null)
  const [saved, setSaved] = useState(false)
  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const form = new FormData(event.currentTarget)
    setBusy(true); setError(null); setSaved(false)
    try {
      const updated = await api.patch<UserProfile>('/users/me/profile', {
        nickname: String(form.get('nickname') || '').trim(),
        email: String(form.get('email') || '').trim(),
        phone: String(form.get('phone') || '').trim(),
        avatar: String(form.get('avatar') || '').trim(),
      })
      const session = getSession()
      if (session && session.userInfo.id === updated.id) {
        setSession({ ...session, userInfo: { ...session.userInfo, nickname: updated.nickname, avatar: updated.avatar } })
      }
      setSaved(true)
    } catch (caught) { setError(caught) } finally { setBusy(false) }
  }
  return <form onSubmit={submit}><fieldset disabled={busy}>
    <label className="field"><span>{t('username')}</span><input value={initial.username} disabled /></label>
    <label className="field"><span>{t('profileNickname')}</span><input name="nickname" maxLength={50} defaultValue={initial.nickname || ''} /></label>
    <label className="field"><span>{t('profileEmail')}</span><input name="email" type="email" maxLength={100} defaultValue={initial.email || ''} /></label>
    <label className="field"><span>{t('phone')}</span><input name="phone" type="tel" pattern="^$|^1[3-9][0-9]{9}$" defaultValue={initial.phone || ''} /></label>
    <label className="field"><span>{t('userAdminAvatar')}</span><input name="avatar" type="url" maxLength={255} defaultValue={initial.avatar || ''} /></label>
    {!!error && <ErrorNotice error={error} />}{saved && <Notice>{t('saved')}</Notice>}
    <button className="button primary">{busy ? t('saving') : t('save')}</button>
  </fieldset></form>
}
