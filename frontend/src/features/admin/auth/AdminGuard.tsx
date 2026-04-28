import { Navigate, Outlet, useLocation } from 'react-router-dom'
import { hasAdminSession } from './adminAccess'

export function AdminGuard() {
  const location = useLocation()

  if (hasAdminSession()) {
    return <Outlet />
  }

  return <Navigate to="/" replace state={{ from: location.pathname }} />
}
