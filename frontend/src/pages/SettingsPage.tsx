import { useEffect, useState, type FormEvent } from 'react'
import { useSearchParams } from 'react-router'
import * as api from '../api'
import { useAuth, useIsOwner } from '../auth'
import { formatDateTime } from '../catalog'
import { ActionForm, ConfirmDeleteDialog, Dialog } from '../components/Dialog'
import { Layout } from '../components/Layout'
import { defaultCurrency, LANGUAGES, useI18n, useT, type Language, type Theme } from '../i18n/context'

type Tab = 'general' | 'locations' | 'users' | 'trash' | 'audit' | 'about'
const OWNER_TABS: Tab[] = ['locations', 'users', 'trash', 'audit']

export function SettingsPage() {
  const t = useT()
  const isOwner = useIsOwner()
  const [params, setParams] = useSearchParams()
  const tabs: Tab[] = ['general', ...(isOwner ? OWNER_TABS : []), 'about']
  const asked = params.get('tab') as Tab | null
  const tab: Tab = asked && tabs.includes(asked) ? asked : 'general'

  return (
    <Layout>
      <div className="page-head">
        <h1>{t('set.title')}</h1>
      </div>
      <div className="settings-layout">
        <nav className="settings-tabs" aria-label={t('set.sections')}>
          {tabs.map((id) => (
            <button
              key={id}
              type="button"
              aria-current={tab === id ? 'page' : undefined}
              onClick={() => setParams(id === 'general' ? {} : { tab: id }, { replace: true })}
            >
              {t(`set.${id}`)}
            </button>
          ))}
        </nav>
        <div className="stack">
          {tab === 'general' && <GeneralTab />}
          {tab === 'general' && <BusinessSection />}
          {tab === 'locations' && <LocationsTab />}
          {tab === 'users' && <UsersTab />}
          {tab === 'trash' && <TrashTab />}
          {tab === 'audit' && <AuditTab />}
          {tab === 'about' && <AboutTab />}
        </div>
      </div>
    </Layout>
  )
}

function GeneralTab() {
  const { preferences, setPreferences, t } = useI18n()
  const [version, setVersion] = useState<string | null>(null)

  useEffect(() => {
    api.fetchVersion().then(setVersion).catch(() => setVersion(null))
  }, [])

  const themes: { id: Theme; label: string }[] = [
    { id: 'system', label: t('set.themeSystem') },
    { id: 'light', label: t('set.themeLight') },
    { id: 'dark', label: t('set.themeDark') },
  ]

  return (
    <section className="card section">
      <h2>{t('set.general')}</h2>
      <label className="setting">
        <span className="label">{t('set.language')}</span>
        <span className="muted small">{t('set.languageHint')}</span>
        <select value={preferences.language} onChange={(e) => setPreferences({ language: e.target.value as Language })}>
          {LANGUAGES.map((l) => (
            <option key={l.code} value={l.code} lang={l.code}>
              {l.name}
            </option>
          ))}
        </select>
      </label>

      <fieldset className="setting">
        <legend className="label">{t('set.theme')}</legend>
        <div className="segmented" role="radiogroup" aria-label={t('set.theme')}>
          {themes.map((theme) => (
            <button
              key={theme.id}
              type="button"
              role="radio"
              aria-checked={preferences.theme === theme.id}
              aria-pressed={preferences.theme === theme.id}
              onClick={() => setPreferences({ theme: theme.id })}
            >
              {theme.label}
            </button>
          ))}
        </div>
      </fieldset>

      <p className="hint small">{t('set.onDevice')}</p>
      {version && <p className="muted small">{t('set.version', { version })}</p>}
    </section>
  )
}

/** Saved on the server, so every computer and every person sees the same. Only the owner can change it. */
function BusinessSection() {
  const { preferences, setPreferences, t } = useI18n()
  const isOwner = useIsOwner()
  const [currencies, setCurrencies] = useState<string[]>([])
  const [form, setForm] = useState<{ currency: string; lowStockLimit: string; paymentReminderDays: string; slowStockDays: string } | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [saved, setSaved] = useState(false)
  const [busy, setBusy] = useState(false)

  useEffect(() => {
    Promise.all([api.fetchCurrencies(), api.fetchBusinessSettings()])
      .then(([available, settings]) => {
        setCurrencies(available)
        setForm({
          currency: settings.currency ?? (preferences.currency || defaultCurrency(available)),
          lowStockLimit: String(settings.lowStockLimit),
          paymentReminderDays: String(settings.paymentReminderDays),
          slowStockDays: String(settings.slowStockDays),
        })
      })
      .catch((e: Error) => setError(e.message))
    // Loaded once: later changes to the preference come from this form.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  function change(part: Partial<NonNullable<typeof form>>) {
    setSaved(false)
    setForm((current) => (current ? { ...current, ...part } : current))
  }

  async function save(event: FormEvent) {
    event.preventDefault()
    if (!form) return
    setBusy(true)
    setError(null)
    try {
      const result = await api.saveBusinessSettings({
        currency: form.currency,
        lowStockLimit: Number(form.lowStockLimit),
        paymentReminderDays: Number(form.paymentReminderDays),
        slowStockDays: Number(form.slowStockDays),
      })
      setPreferences({ currency: result.currency ?? '' })
      setSaved(true)
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e))
    } finally {
      setBusy(false)
    }
  }

  return (
    <form className="card section" onSubmit={save}>
      <h2>{t('set.business')}</h2>
      {error && <p className="error" role="alert">{error}</p>}
      {form && (
        <>
          <label className="setting">
            <span className="label">{t('set.currency')}</span>
            <span className="muted small">{t('set.currencyHint')}</span>
            <select value={form.currency} disabled={!isOwner} onChange={(e) => change({ currency: e.target.value })}>
              {currencies.map((c) => (
                <option key={c} value={c}>
                  {c}
                </option>
              ))}
            </select>
          </label>
          <label className="setting">
            <span className="label">{t('set.lowStock')}</span>
            <span className="muted small">{t('set.lowStockHint')}</span>
            <input
              type="number"
              min={0}
              step={1}
              required
              disabled={!isOwner}
              value={form.lowStockLimit}
              onChange={(e) => change({ lowStockLimit: e.target.value })}
            />
          </label>
          <label className="setting">
            <span className="label">{t('set.reminderDays')}</span>
            <span className="muted small">{t('set.reminderHint')}</span>
            <input
              type="number"
              min={1}
              step={1}
              required
              disabled={!isOwner}
              value={form.paymentReminderDays}
              onChange={(e) => change({ paymentReminderDays: e.target.value })}
            />
          </label>
          <label className="setting">
            <span className="label">{t('set.slowStock')}</span>
            <span className="muted small">{t('set.slowStockHint')}</span>
            <input
              type="number"
              min={1}
              step={1}
              required
              disabled={!isOwner}
              value={form.slowStockDays}
              onChange={(e) => change({ slowStockDays: e.target.value })}
            />
          </label>
          {isOwner ? (
            <div className="row">
              <button type="submit" disabled={busy}>{busy ? t('common.saving') : t('set.save')}</button>
              {saved && <span className="muted small" role="status">{t('set.saved')}</span>}
            </div>
          ) : (
            <p className="hint small">{t('set.ownerCanChange')}</p>
          )}
        </>
      )}
    </form>
  )
}

function LocationsTab() {
  const t = useT()
  const [locations, setLocations] = useState<api.Location[] | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [notice, setNotice] = useState<string | null>(null)
  const [adding, setAdding] = useState(false)
  const [deleting, setDeleting] = useState<api.Location | null>(null)

  useEffect(() => {
    api.fetchLocations().then(setLocations).catch((e: Error) => setError(e.message))
  }, [])

  return (
    <section className="card table-card">
      <div className="card-head">
        <div className="stack tight">
          <h2>{t('set.locations')}</h2>
          <p className="muted small">{t('set.locIntro')}</p>
        </div>
        <button
          onClick={() => {
            setNotice(null)
            setAdding(true)
          }}
        >
          {t('set.locAdd')}
        </button>
      </div>
      {notice && <p className="pad success" role="status">{notice}</p>}
      {error && <p className="pad error" role="alert">{error}</p>}
      {!locations && !error && <p className="pad muted">{t('common.loading')}</p>}
      {locations?.length === 0 && <p className="pad muted">{t('set.locNone')}</p>}
      {locations && locations.length > 0 && (
        <div className="table-wrap">
          <table>
            <thead>
              <tr>
                <th>{t('set.locName')}</th>
                <th>{t('set.locCountry')}</th>
                <th><span className="sr-only">{t('set.action')}</span></th>
              </tr>
            </thead>
            <tbody>
              {locations.map((l) => (
                <tr key={l.id}>
                  <td className="strong">{l.name}</td>
                  <td>{l.country}</td>
                  <td className="row-actions num">
                    <button className="secondary danger-text" onClick={() => setDeleting(l)}>
                      {t('set.locDelete')}
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {adding && (
        <Dialog title={t('set.locAdd')} onClose={() => setAdding(false)}>
          <ActionForm
            submitLabel={t('set.locAdd')}
            busyLabel={t('common.saving')}
            onClose={() => setAdding(false)}
            onSubmit={async (form) => {
              const location = await api.createLocation({
                name: String(form.get('name')),
                country: String(form.get('country')),
              })
              setLocations(await api.fetchLocations())
              setNotice(t('set.locAdded', { name: location.name }))
            }}
          >
            <label>
              {t('set.locName')}
              <input name="name" required autoFocus maxLength={100} placeholder="Berlin, Tunis…" />
            </label>
            <label>
              {t('set.locCountry')}
              <input name="country" required maxLength={100} placeholder="DE, TN…" />
            </label>
          </ActionForm>
        </Dialog>
      )}
      {deleting && (
        <ConfirmDeleteDialog
          title={t('set.locDeleteTitle', { name: deleting.name })}
          message={t('set.locDeleteText')}
          confirmLabel={t('set.locDelete')}
          onClose={() => setDeleting(null)}
          onConfirm={async (reason) => {
            await api.deleteLocation(deleting.id, reason)
            setLocations(await api.fetchLocations())
            setNotice(t('set.locDeleted', { name: deleting.name }))
          }}
        />
      )}
    </section>
  )
}

function UsersTab() {
  const t = useT()
  const { session } = useAuth()
  const [users, setUsers] = useState<api.User[] | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [adding, setAdding] = useState(false)
  const [notice, setNotice] = useState<string | null>(null)

  useEffect(() => {
    api.fetchUsers().then(setUsers).catch((e: Error) => setError(e.message))
  }, [])

  return (
    <section className="card table-card">
      <div className="card-head">
        <div className="stack tight">
          <h2>{t('set.users')}</h2>
          <p className="muted small">{t('set.usersIntro')}</p>
        </div>
        <button
          onClick={() => {
            setNotice(null)
            setAdding(true)
          }}
        >
          {t('set.newUser')}
        </button>
      </div>
      {notice && <p className="pad success" role="status">{notice}</p>}
      {error && <p className="pad error" role="alert">{error}</p>}
      {!users && !error && <p className="pad muted">{t('common.loading')}</p>}
      {users && (
        <div className="table-wrap">
          <table>
            <thead>
              <tr>
                <th>{t('set.username')}</th>
                <th>{t('set.role')}</th>
                <th>{t('set.status')}</th>
              </tr>
            </thead>
            <tbody>
              {users.map((u) => (
                <tr key={u.id}>
                  <td className="strong">
                    {u.username} {u.username === session?.username && <span className="badge">{t('set.you')}</span>}
                  </td>
                  <td>{t(u.role === 'OWNER' ? 'role.OWNER' : 'role.VIEWER')}</td>
                  <td>
                    {u.active ? (
                      <span className="pill pill-paid">{t('set.active')}</span>
                    ) : (
                      <span className="muted">{t('set.disabled')}</span>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
      {adding && (
        <Dialog title={t('set.newUser')} onClose={() => setAdding(false)}>
          <ActionForm
            submitLabel={t('set.createUser')}
            busyLabel={t('common.saving')}
            onClose={() => setAdding(false)}
            onSubmit={async (form) => {
              const password = String(form.get('password'))
              if (password !== form.get('repeat')) throw new Error(t('set.mismatch'))
              const user = await api.createUser({
                username: String(form.get('username')),
                password,
                role: form.get('role') as api.Role,
              })
              setUsers(await api.fetchUsers())
              setNotice(t('set.userCreated', { name: user.username }))
            }}
          >
            <label>
              {t('set.username')}
              <input name="username" required autoFocus maxLength={100} autoComplete="off" />
            </label>
            <label>
              {t('set.role')}
              <select name="role" defaultValue="VIEWER">
                <option value="VIEWER">{t('set.roleViewerLong')}</option>
                <option value="OWNER">{t('set.roleOwnerLong')}</option>
              </select>
            </label>
            <label>
              <span>
                {t('set.password')} <span className="muted">{t('set.passwordHint')}</span>
              </span>
              <input name="password" type="password" minLength={10} required autoComplete="new-password" />
            </label>
            <label>
              {t('set.repeat')}
              <input name="repeat" type="password" minLength={10} required autoComplete="new-password" />
            </label>
          </ActionForm>
        </Dialog>
      )}
    </section>
  )
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

function TrashTab() {
  const t = useT()
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
      setNotice(t('set.restored', { what: t(`trash.${item.type}`), name: item.name }))
    } catch (e) {
      setError((e as Error).message)
    } finally {
      setBusy(null)
    }
  }

  return (
    <section className="card table-card">
      <div className="card-head">
        <div className="stack tight">
          <h2>{t('set.trash')}</h2>
          <p className="muted small">{t('set.trashIntro')}</p>
        </div>
      </div>
      {notice && <p className="pad success" role="status">{notice}</p>}
      {error && <p className="pad error" role="alert">{error}</p>}
      {!items && !error && <p className="pad muted">{t('common.loading')}</p>}
      {items?.length === 0 && <p className="pad muted">{t('set.trashEmpty')}</p>}
      {items && items.length > 0 && (
        <div className="table-wrap">
          <table>
            <thead>
              <tr>
                <th>{t('set.what')}</th>
                <th>{t('set.name')}</th>
                <th>{t('set.deleted')}</th>
                <th>{t('set.reason')}</th>
                <th><span className="sr-only">{t('set.action')}</span></th>
              </tr>
            </thead>
            <tbody>
              {items.map((item) => {
                const key = `${item.type}-${item.id}`
                return (
                  <tr key={key}>
                    <td>{t(`trash.${item.type}`)}</td>
                    <td className="strong">{item.name}</td>
                    <td className="nowrap">{formatDateTime(item.deletedAt)}</td>
                    <td>{item.reason ?? <span className="muted">—</span>}</td>
                    <td className="row-actions num">
                      <button className="secondary" disabled={busy !== null} onClick={() => handleRestore(item)}>
                        {busy === key ? t('set.restoring') : t('set.restore')}
                      </button>
                    </td>
                  </tr>
                )
              })}
            </tbody>
          </table>
        </div>
      )}
    </section>
  )
}

function AuditTab() {
  const t = useT()
  const [entries, setEntries] = useState<api.AuditEntry[] | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    api.fetchAudit(300).then(setEntries).catch((e: Error) => setError(e.message))
  }, [])

  const action = (method: string) =>
    method === 'POST' || method === 'PUT' || method === 'PATCH' || method === 'DELETE' ? t(`audit.${method}`) : method

  return (
    <section className="card table-card">
      <div className="card-head">
        <div className="stack tight">
          <h2>{t('set.audit')}</h2>
          <p className="muted small">{t('set.auditIntro')}</p>
        </div>
      </div>
      {error && <p className="pad error" role="alert">{error}</p>}
      {!entries && !error && <p className="pad muted">{t('common.loading')}</p>}
      {entries && (
        <div className="table-wrap">
          <table className="compact">
            <thead>
              <tr>
                <th>{t('set.when')}</th>
                <th>{t('set.who')}</th>
                <th>{t('set.action')}</th>
                <th>{t('set.on')}</th>
                <th>{t('set.result')}</th>
              </tr>
            </thead>
            <tbody>
              {entries.map((e) => (
                <tr key={e.id}>
                  <td className="nowrap">{formatDateTime(e.occurredAt)}</td>
                  <td>{e.username}</td>
                  <td>{action(e.method)}</td>
                  <td className="mono-ish" dir="ltr">{e.path.replace(/^\/api\/v1/, '')}</td>
                  <td>
                    {e.status < 400 ? (
                      <span className="pill pill-paid">{t('set.ok')}</span>
                    ) : (
                      <span className="pill pill-unpaid">
                        {t(e.status === 401 || e.status === 403 ? 'set.notAllowed' : 'set.refused')} ({e.status})
                      </span>
                    )}
                  </td>
                </tr>
              ))}
              {entries.length === 0 && (
                <tr>
                  <td colSpan={5} className="muted">{t('set.nothingRecorded')}</td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      )}
    </section>
  )
}

function AboutTab() {
  const t = useT()
  const [info, setInfo] = useState<{ up: boolean; currencies: string[] } | null>(null)

  useEffect(() => {
    api
      .fetchCurrencies()
      .then((currencies) => setInfo({ up: true, currencies }))
      .catch(() => setInfo({ up: false, currencies: [] }))
  }, [])

  return (
    <section className="card section">
      <h2>{t('set.about')}</h2>
      <p>{t('set.aboutApp')}</p>
      <dl className="details">
        <div>
          <dt>{t('set.server')}</dt>
          <dd>
            {!info ? (
              t('common.loading')
            ) : info.up ? (
              <span className="pill pill-paid">{t('set.serverUp')}</span>
            ) : (
              <span className="pill pill-unpaid">{t('set.serverDown')}</span>
            )}
          </dd>
        </div>
        <div>
          <dt>{t('set.currencies')}</dt>
          <dd>{info?.currencies.join(', ') || '—'}</dd>
        </div>
      </dl>
      <div className="stack tight">
        <span className="label">{t('set.backups')}</span>
        <p className="muted">{t('set.backupsText')}</p>
      </div>
    </section>
  )
}
