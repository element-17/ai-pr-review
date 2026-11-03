import { useCallback, useEffect, useState, type FormEvent } from 'react'
import { api, query } from '../lib/api'
import { useSession } from '../hooks'
import { usePreferences } from '../lib/preferences'
import type { Page, UserProfile } from '../lib/types'
import { ErrorNotice, Notice, PageHeading } from '../components/ui'
import styles from './AdminModels.module.scss'

type ManagedUser = UserProfile & { status: number; email: string | null; phone: string | null }
type UserForm = { username: string; password: string; nickname: string; email: string; phone: string; avatar: string; role: string }
const emptyForm: UserForm = { username: '', password: '', nickname: '', email: '', phone: '', avatar: '', role: 'USER' }
const PAGE_SIZE = 20

/** 管理员维护用户；所有修改由后端再次校验权限。 */
export default function AdminUsers() {
  const { t } = usePreferences()
  const session = useSession()
  const [items, setItems] = useState<ManagedUser[]>([])
  const [total, setTotal] = useState(0)
  const [counts, setCounts] = useState<{ role: string; total: number }[]>([])
  const [keyword, setKeyword] = useState('')
  const [search, setSearch] = useState('')
  const [role, setRole] = useState('')
  const [status, setStatus] = useState('')
  const [page, setPage] = useState(1)
  const [revision, setRevision] = useState(0)
  const [loading, setLoading] = useState(true)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<unknown>(null)
  const [notice, setNotice] = useState(false)
  const [editingId, setEditingId] = useState<number | null>(null)
  const [form, setForm] = useState<UserForm>(emptyForm)
  const reload = useCallback(() => setRevision(value => value + 1), [])

  useEffect(() => {
    const controller = new AbortController()
    async function load() {
      setLoading(true)
      setError(null)
      try {
        const path = search ? '/users/search' : '/users/page'
        const params = query({ page, pageSize: PAGE_SIZE, keyword: search, role, status })
        const [result, stats] = await Promise.all([
          api.get<Page<ManagedUser>>(`${path}${params}`, { signal: controller.signal }),
          api.get<{ role: string; total: number }[]>('/users/stats/roles', { signal: controller.signal }),
        ])
        if (!controller.signal.aborted) { setItems(result.records); setTotal(result.total); setCounts(stats) }
      } catch (caught) {
        if (!controller.signal.aborted) setError(caught)
      } finally { if (!controller.signal.aborted) setLoading(false) }
    }
    void load()
    return () => controller.abort()
  }, [page, search, role, status, revision])

  async function edit(id: number) {
    setBusy(true); setError(null); setNotice(false)
    try {
      const user = await api.get<ManagedUser>(`/users/${id}`)
      setEditingId(id)
      setForm({ username: user.username, password: '', nickname: user.nickname || '', email: user.email || '', phone: user.phone || '', avatar: user.avatar || '', role: user.role })
      document.getElementById('user-editor')?.scrollIntoView({ behavior: 'smooth' })
    } catch (caught) { setError(caught) } finally { setBusy(false) }
  }

  async function save(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setBusy(true); setError(null); setNotice(false)
    try {
      const { username, password, nickname, email, phone, avatar, role: userRole } = form
      const details = { nickname: nickname.trim(), email: email.trim(), phone: phone.trim(), role: userRole }
      if (editingId === null) await api.post('/users', { username: username.trim(), password, ...details })
      else await api.put(`/users/${editingId}`, { ...details, avatar: avatar.trim() })
      setForm(emptyForm); setEditingId(null); setNotice(true); reload()
    } catch (caught) { setError(caught) } finally { setBusy(false) }
  }

  async function changeUser(user: ManagedUser, action: 'status' | 'delete') {
    if (user.id === session?.userInfo.id) return
    if (!window.confirm(t(action === 'delete' ? 'userAdminDeleteConfirm' : user.status === 1 ? 'userAdminDisableConfirm' : 'userAdminEnableConfirm'))) return
    setBusy(true); setError(null); setNotice(false)
    try {
      if (action === 'delete') await api.delete(`/users/${user.id}`)
      else await api.put(`/users/${user.id}/status?status=${user.status === 1 ? 0 : 1}`)
      if (action === 'delete' && items.length === 1 && page > 1) setPage(value => value - 1)
      else reload()
      setNotice(true)
    } catch (caught) { setError(caught) } finally { setBusy(false) }
  }

  function update(key: keyof UserForm, value: string) { setForm(current => ({ ...current, [key]: value })) }
  const pages = Math.max(1, Math.ceil(total / PAGE_SIZE))
  return <div className={styles.page}>
    <PageHeading title="userAdminTitle" description="userAdminDescription" />
    {!!error && <ErrorNotice error={error} />}
    {notice && <Notice>{t('saved')}</Notice>}
    <section className={styles.listSection}>
      <header className={styles.sectionHeading}><div><h2>{t('userAdminList')}</h2><p>{counts.map(item => `${item.role}: ${item.total}`).join(' · ')}</p></div></header>
      <form className={styles.fields} onSubmit={event => { event.preventDefault(); setPage(1); setSearch(keyword.trim()) }}>
        <label className="field"><span>{t('userAdminSearch')}</span><input value={keyword} onChange={event => setKeyword(event.target.value)} /></label>
        <label className="field"><span>{t('userRole')}</span><select value={role} onChange={event => { setRole(event.target.value); setPage(1) }}><option value="">{t('all')}</option><option value="ADMIN">{t('roleAdmin')}</option><option value="USER">{t('roleUser')}</option></select></label>
        <label className="field"><span>{t('accountStatus')}</span><select value={status} onChange={event => { setStatus(event.target.value); setPage(1) }}><option value="">{t('all')}</option><option value="1">{t('accountActive')}</option><option value="0">{t('accountInactive')}</option></select></label>
        <div className={styles.formActions}><button className="button primary" disabled={loading}>{t('search')}</button></div>
      </form>
      {loading ? <p>{t('loading')}</p> : <div className={styles.tableWrap}><table className={styles.table}>
        <thead><tr><th>{t('username')}</th><th>{t('profileNickname')}</th><th>{t('profileEmail')}</th><th>{t('userRole')}</th><th>{t('accountStatus')}</th><th>{t('modelAdminActions')}</th></tr></thead>
        <tbody>{items.map(user => <tr key={user.id}><td>{user.username}</td><td>{user.nickname || '—'}</td><td>{user.email || '—'}</td><td>{user.role === 'ADMIN' ? t('roleAdmin') : t('roleUser')}</td><td>{t(user.status === 1 ? 'accountActive' : 'accountInactive')}</td><td><div className={styles.rowActions}>
          <button className="button small" disabled={busy} onClick={() => void edit(user.id)}>{t('userAdminEdit')}</button>
          <button className="button small" disabled={busy || user.id === session?.userInfo.id} onClick={() => void changeUser(user, 'status')}>{t(user.status === 1 ? 'userAdminDisable' : 'userAdminEnable')}</button>
          <button className="button small" disabled={busy || user.id === session?.userInfo.id} onClick={() => void changeUser(user, 'delete')}>{t('userAdminDelete')}</button>
        </div></td></tr>)}</tbody>
      </table></div>}
      {!loading && items.length === 0 && <p>{t('userAdminEmpty')}</p>}
      <div className={styles.formActions}><button type="button" className="button" disabled={page <= 1 || loading} onClick={() => setPage(value => value - 1)}>{t('previous')}</button><span>{page} / {pages} · {total}</span><button type="button" className="button" disabled={page >= pages || loading} onClick={() => setPage(value => value + 1)}>{t('next')}</button></div>
    </section>
    <section className={styles.editor} id="user-editor"><h2>{t(editingId === null ? 'userAdminCreate' : 'userAdminEdit')}</h2>
      <form className={styles.form} onSubmit={save}><fieldset disabled={busy} className={styles.fields}>
        <label className="field"><span>{t('username')}</span><input required minLength={3} maxLength={50} pattern="[A-Za-z0-9_]+" disabled={editingId !== null} value={form.username} onChange={event => update('username', event.target.value)} /></label>
        {editingId === null && <label className="field"><span>{t('password')}</span><input required type="password" autoComplete="new-password" minLength={6} maxLength={32} value={form.password} onChange={event => update('password', event.target.value)} /></label>}
        <label className="field"><span>{t('profileNickname')}</span><input maxLength={50} value={form.nickname} onChange={event => update('nickname', event.target.value)} /></label>
        <label className="field"><span>{t('profileEmail')}</span><input type="email" maxLength={100} value={form.email} onChange={event => update('email', event.target.value)} /></label>
        <label className="field"><span>{t('phone')}</span><input pattern="^$|^1[3-9][0-9]{9}$" value={form.phone} onChange={event => update('phone', event.target.value)} /></label>
        {editingId !== null && <label className="field"><span>{t('userAdminAvatar')}</span><input type="url" maxLength={255} value={form.avatar} onChange={event => update('avatar', event.target.value)} /></label>}
        <label className="field"><span>{t('userRole')}</span><select disabled={editingId === session?.userInfo.id} value={form.role} onChange={event => update('role', event.target.value)}><option value="USER">{t('roleUser')}</option><option value="ADMIN">{t('roleAdmin')}</option></select></label>
        <div className={styles.formActions}><button className="button primary">{t('save')}</button>{editingId !== null && <button type="button" className="button" onClick={() => { setEditingId(null); setForm(emptyForm) }}>{t('cancel')}</button>}</div>
      </fieldset></form>
    </section>
  </div>
}
