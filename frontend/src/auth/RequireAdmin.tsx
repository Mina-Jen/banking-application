/**
 * ADMIN-only route guard.
 *
 * Like <RequireAuth>, this is a UX convenience rather than the security
 * boundary: the backend refuses an admin-only API call from a CUSTOMER with a
 * 403 regardless of what the bundle shows. Sending the customer back to their
 * own dashboard simply avoids a page that would fail to load.
 */

import type { ReactNode } from 'react'
import { Navigate } from 'react-router-dom'
import { useAuth } from './AuthContext'

export function RequireAdmin({ children }: { children: ReactNode }) {
  const { isAdmin } = useAuth()

  if (!isAdmin) {
    return <Navigate to="/" replace />
  }

  return <>{children}</>
}
