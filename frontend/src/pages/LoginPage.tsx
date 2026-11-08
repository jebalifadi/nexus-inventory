import { useState, type FormEvent } from 'react'
import { Navigate, useLocation, useNavigate } from 'react-router'
import { useAuth } from '../auth'
import { PulsingLogo } from '../components/Logo'
import { useT } from '../i18n/context'

export function LoginPage() {
  const { session, login } = useAuth()
  const t = useT()
  const navigate = useNavigate()
  const location = useLocation()
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  // Where to go after login: the page the user wanted, or the dashboard.
  const from = (location.state as { from?: string } | null)?.from ?? '/dashboard'

  if (session) return <Navigate to={from} replace />

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    setBusy(true)
    setError(null)
    try {
      await login(username, password)
      navigate(from, { replace: true })
    } catch (e) {
      setError(e instanceof Error ? e.message : t('login.failed'))
      setBusy(false)
    }
  }

  return (
    <main className="login">
      <form className="card login-card" onSubmit={handleSubmit}>
        <div className="login-brand">
          <PulsingLogo />
          <h1 className="brand">Nexus</h1>
        </div>
        <p className="muted">{t('login.intro')}</p>

        <label>
          {t('login.username')}
          <input
            value={username}
            onChange={(e) => setUsername(e.target.value)}
            autoComplete="username"
            autoFocus
            required
          />
        </label>

        <label>
          {t('login.password')}
          <input
            type="password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            autoComplete="current-password"
            required
          />
        </label>

        {error && (
          <p className="error" role="alert">
            {error}
          </p>
        )}

        <button type="submit" disabled={busy}>
          {busy ? t('login.signingIn') : t('login.signIn')}
        </button>
      </form>
    </main>
  )
}
