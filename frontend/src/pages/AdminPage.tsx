import { useEffect, useState } from 'react'
import * as api from '../api'
import { useAuth } from '../auth'
import { ActionForm, Dialog } from '../components/Dialog'
import { Layout } from '../components/Layout'

type Tab = 'users' | 'trash' | 'audit'

const TRASH_LABELS: Record<api.TrashType, string> = {
  CATEGORY: 'Category',
  PRODUCT: 'Product',
  LOCATION: 'Location',
  UNIT: 'Piece',
  LOT: 'Batch',
}

/** Pieces and batches are restored by their public ID (U-…, LOT-…), the rest by number. */
function restore(item: api.TrashItem): Promise<unknown> {
  switch (item.type) {
    case 'CATEGORY':
      return api.restoreCategory(item.id)
    case 'PRODUCT':
      return api.restoreProduct(item.id)
    case 'LOCATION':
      return api.restoreLocation(item.id)
    case 'UNIT':
      return api.restoreUnit(item.name)
    case 'LOT':
      return api.restoreLot(item.name)
  }
}

const formatTime = (iso: string) => new Date(iso).toLocaleString(undefined, { dateStyle: 'medium', timeStyle: 'short' })

/** Owner only: who can log in, what was deleted, and every change made. */
export function AdminPage() {
  const [tab, setTab] = useState<Tab>('users')
  return (
    <Layout>
      <div className="page-head">
        <h1>Admin</h1>
        <div className="segmented" role="group" aria-label="Section">
          <button aria-pressed={tab === 'users'} onClick={() => setTab('users')}>Users</button>
          <button aria-pressed={tab === 'trash'} onClick={() => setTab('trash')}>Trash</button>
          <button aria-pressed={tab === 'audit'} onClick={() => setTab('audit')}>Audit log</button>
        </div>
      </div>
      {tab === 'users' && <UsersTab />}
      {tab === 'trash' && <TrashTab />}
      {tab === 'audit' && <AuditTab />}
    </Layout>
  )
}

function UsersTab() {
  const { session } = useAuth()
  const [users, setUsers] = useState<api.User[] | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [adding, setAdding] = useState(false)
  const [notice, setNotice] = useState<string | null>(null)

  useEffect(() => {
    api.fetchUsers().then(setUsers).catch((e: Error) => setError(e.message))
  }, [])

  return (
    <>
      <div className="spread">
        <p className="muted">An owner can change everything. A viewer (an employee, your accountant) can only look.</p>
        <button
          onClick={() => {
            setNotice(null)
            setAdding(true)
          }}
        >
          New user
        </button>
      </div>
      {notice && <p className="success" role="status">{notice}</p>}
      {error && <div className="card error-box" role="alert"><p>{error}</p></div>}
      {!users && !error && <p className="muted">Loading…</p>}
      {users && (
        <div className="card table-card table-wrap">
          <table>
            <thead>
              <tr>
                <th>Username</th>
                <th>Role</th>
                <th>Status</th>
              </tr>
            </thead>
            <tbody>
              {users.map((u) => (
                <tr key={u.id}>
                  <td className="strong">
                    {u.username} {u.username === session?.username && <span className="badge">you</span>}
                  </td>
                  <td>{u.role === 'OWNER' ? 'Owner' : 'Viewer'}</td>
                  <td>{u.active ? 'Active' : <span className="muted">Disabled</span>}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
      {adding && (
        <Dialog title="New user" onClose={() => setAdding(false)}>
          <ActionForm
            submitLabel="Create user"
            busyLabel="Saving…"
            onClose={() => setAdding(false)}
            onSubmit={async (form) => {
              const password = String(form.get('password'))
              if (password !== form.get('repeat')) throw new Error('The two passwords are not the same.')
              const user = await api.createUser({
                username: String(form.get('username')),
                password,
                role: form.get('role') as api.Role,
              })
              setUsers(await api.fetchUsers())
              setNotice(`User ${user.username} created. Give them their password in person, not by email.`)
            }}
          >
            <label>
              Username
              <input name="username" required autoFocus maxLength={100} autoComplete="off" />
            </label>
            <label>
              Role
              <select name="role" defaultValue="VIEWER">
                <option value="VIEWER">Viewer: can only look</option>
                <option value="OWNER">Owner: can change everything</option>
              </select>
            </label>
            <label>
              <span>Password <span className="muted">(at least 10 characters)</span></span>
              <input name="password" type="password" minLength={10} required autoComplete="new-password" />
            </label>
            <label>
              Repeat password
              <input name="repeat" type="password" minLength={10} required autoComplete="new-password" />
            </label>
          </ActionForm>
        </Dialog>
      )}
    </>
  )
}

function TrashTab() {
  const [items, setItems] = useState<api.TrashItem[] | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState<string | null>(null)
  const [notice, setNotice] = useState<string | null>(null)

  useEffect(() => {
    api.fetchTrash().then(setItems).catch((e: Error) => setError(e.message))
  }, [])

  async function handleRestore(item: api.TrashItem) {
    const key = `${item.type}-${item.id}`
    setBusy(key)
    setError(null)
    setNotice(null)
    try {
      await restore(item)
      setItems(await api.fetchTrash())
      setNotice(`${TRASH_LABELS[item.type]} ${item.name} restored.`)
    } catch (e) {
      setError((e as Error).message)
    } finally {
      setBusy(null)
    }
  }

  return (
    <>
      <p className="muted">Deleted records stay here and can be brought back.</p>
      {notice && <p className="success" role="status">{notice}</p>}
      {error && <div className="card error-box" role="alert"><p>{error}</p></div>}
      {!items && !error && <p className="muted">Loading…</p>}
      {items?.length === 0 && (
        <div className="card empty">
          <strong>The trash is empty</strong>
        </div>
      )}
      {items && items.length > 0 && (
        <div className="card table-card table-wrap">
          <table>
            <thead>
              <tr>
                <th>What</th>
                <th>Name</th>
                <th>Deleted</th>
                <th>Reason</th>
                <th><span className="sr-only">Actions</span></th>
              </tr>
            </thead>
            <tbody>
              {items.map((item) => {
                const key = `${item.type}-${item.id}`
                return (
                  <tr key={key}>
                    <td>{TRASH_LABELS[item.type]}</td>
                    <td className="strong">{item.name}</td>
                    <td className="nowrap">{formatTime(item.deletedAt)}</td>
                    <td>{item.reason ?? <span className="muted">—</span>}</td>
                    <td className="row-actions">
                      <button className="secondary" disabled={busy !== null} onClick={() => handleRestore(item)}>
                        {busy === key ? 'Restoring…' : 'Restore'}
                      </button>
                    </td>
                  </tr>
                )
              })}
            </tbody>
          </table>
        </div>
      )}
    </>
  )
}

const METHOD_LABELS: Record<string, string> = { POST: 'Created', PUT: 'Changed', PATCH: 'Changed', DELETE: 'Deleted' }

function AuditTab() {
  const [entries, setEntries] = useState<api.AuditEntry[] | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    api.fetchAudit(300).then(setEntries).catch((e: Error) => setError(e.message))
  }, [])

  return (
    <>
      <p className="muted">Every change, who made it and whether it worked. It cannot be edited or deleted.</p>
      {error && <div className="card error-box" role="alert"><p>{error}</p></div>}
      {!entries && !error && <p className="muted">Loading…</p>}
      {entries && (
        <div className="card table-card table-wrap">
          <table className="compact">
            <thead>
              <tr>
                <th>When</th>
                <th>Who</th>
                <th>Action</th>
                <th>On</th>
                <th>Result</th>
              </tr>
            </thead>
            <tbody>
              {entries.map((e) => (
                <tr key={e.id}>
                  <td className="nowrap">{formatTime(e.occurredAt)}</td>
                  <td>{e.username}</td>
                  <td>{METHOD_LABELS[e.method] ?? e.method}</td>
                  <td className="mono-ish">{e.path.replace(/^\/api\/v1/, '')}</td>
                  <td>
                    {e.status < 400 ? (
                      <span className="success">OK</span>
                    ) : (
                      <span className="error">{e.status === 401 || e.status === 403 ? 'Not allowed' : 'Refused'} ({e.status})</span>
                    )}
                  </td>
                </tr>
              ))}
              {entries.length === 0 && (
                <tr>
                  <td colSpan={5} className="muted">Nothing recorded yet.</td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      )}
    </>
  )
}
