import { useState, type FormEvent } from 'react'
import { useLocation, useNavigate } from 'react-router-dom'
import { loginAdmin } from './adminAuthApi'

function intendedRoute(state: unknown): string {
  const from = (state as { from?: unknown } | null)?.from
  return typeof from === 'string' && (from === '/admin' || from.startsWith('/admin/')) && from !== '/admin/login' ? from : '/admin'
}

export function AdminLoginPage() {
  const location = useLocation()
  const navigate = useNavigate()
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [failed, setFailed] = useState(false)

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (submitting) return
    setSubmitting(true)
    setFailed(false)
    try {
      await loginAdmin(username, password)
      setPassword('')
      navigate(intendedRoute(location.state), { replace: true })
    } catch {
      setPassword('')
      setFailed(true)
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <main className="admin-login-page">
      <section className="admin-login-card" aria-labelledby="admin-login-title">
        <p className="admin-login-kicker">El Tano Admin</p>
        <h1 id="admin-login-title">Iniciar sesión</h1>
        <p>Acceso exclusivo para operadores autorizados.</p>
        <form onSubmit={handleSubmit}>
          <label htmlFor="admin-username">Usuario</label>
          <input id="admin-username" name="username" autoComplete="username" required value={username} onChange={(event) => setUsername(event.target.value)} />
          <label htmlFor="admin-password">Contraseña</label>
          <input id="admin-password" name="password" type="password" autoComplete="current-password" required value={password} onChange={(event) => setPassword(event.target.value)} />
          {failed ? <p role="alert">No pudimos iniciar sesión. Revisa tus credenciales e intenta nuevamente.</p> : null}
          <button type="submit" disabled={submitting}>{submitting ? 'Iniciando sesión…' : 'Iniciar sesión'}</button>
        </form>
      </section>
    </main>
  )
}
