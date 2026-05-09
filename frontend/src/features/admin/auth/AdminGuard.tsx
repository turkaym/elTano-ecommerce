import { useEffect, useState } from 'react'
import { Navigate, Outlet, useLocation } from 'react-router-dom'
import { adminGuardMessages, bootstrapAdminSession } from './adminAccess'
import type { AdminAccessState } from './adminAccess'

export function AdminGuard() {
  const location = useLocation()
  const [state, setState] = useState<AdminAccessState>('loading')

  useEffect(() => {
    let isActive = true

    bootstrapAdminSession()
      .then((next) => {
        if (isActive) {
          setState(next)
        }
      })
      .catch(() => {
        if (isActive) {
          setState('service-unavailable')
        }
      })

    return () => {
      isActive = false
    }
  }, [])

  if (state === 'loading') {
    return (
      <main className="main-content" role="status" aria-live="polite">
        <section className="section">
          <h1>Verificando acceso admin…</h1>
        </section>
      </main>
    )
  }

  if (state === 'authenticated') {
    return <Outlet />
  }

  if (state === 'forbidden') {
    return (
      <main className="main-content">
        <section className="section" role="alert" aria-labelledby="admin-forbidden-title">
          <h1 id="admin-forbidden-title">Acceso denegado</h1>
          <p>{adminGuardMessages.forbidden}</p>
        </section>
      </main>
    )
  }

  if (state === 'service-unavailable') {
    return (
      <main className="main-content">
        <section className="section" role="alert" aria-labelledby="admin-unavailable-title">
          <h1 id="admin-unavailable-title">Panel admin temporalmente no disponible</h1>
          <p>{adminGuardMessages.serviceUnavailable}</p>
        </section>
      </main>
    )
  }

  return <Navigate to="/" replace state={{ from: location.pathname, reason: 'login-required' }} />
}
