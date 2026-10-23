/**
 * Route guard.
 *
 * Wrapping a route in <RequireAuth> means an unauthenticated visitor is sent to
 * /login and, after signing in, returned to the page they originally asked for.
 *
 * This is a UX convenience only, not a security control: anything in the bundle
 * can be read by the user, so the real protection is Spring Security rejecting
 * every /api call without a valid token, plus BankingService checking that the
 * caller owns the account. A hidden page would still be a 404 on the API.
 */

import type { ReactNode } from 'react'
import { Navigate, useLocation } from 'react-router-dom'
import { useAuth } from './AuthContext'

export function RequireAuth({ children }: { children: ReactNode }) {
  const { isAuthenticated } = useAuth()
  const location = useLocation()

  if (!isAuthenticated) {
    return (
      <Navigate
        to="/login"
        replace
        state={{ from: `${location.pathname}${location.search}` }}
      />
    )
  }

  return <>{children}</>
}
