import { Link, Outlet, useLocation, useNavigate } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'
import { Button } from './Button'
import { LogoWordmark } from './Logo'
import { cx, focusRing } from './ui'

/**
 * The signed-in shell: a header with who you are, and an <Outlet/> where the
 * protected pages render. Reached only through <RequireAuth>.
 */
export function AppLayout() {
  const { username, role, isAdmin, signOut } = useAuth()
  const navigate = useNavigate()
  const { pathname } = useLocation()
  const reviewsActive = pathname.startsWith('/review')
  const accountsActive = !reviewsActive

  const navLink = (to: string, label: string, active: boolean) => (
    <Link
      to={to}
      aria-current={active ? 'page' : undefined}
      className={cx(
        'rounded-full px-3 py-2 text-sm font-semibold',
        focusRing,
        active
          ? 'bg-action-soft text-action-strong'
          : 'text-text-muted hover:bg-surface-muted hover:text-text',
      )}
    >
      {label}
    </Link>
  )

  return (
    <div className="min-h-screen">
      <header className="border-b border-line bg-surface">
        <div className="mx-auto flex max-w-6xl flex-wrap items-center gap-4 px-4 py-3 sm:px-6">
          <Link
            to="/"
            className={cx(
              'rounded-md',
              focusRing,
            )}
          >
            <LogoWordmark size={28} />
          </Link>

          <nav aria-label="Main" className="ml-auto flex items-center gap-2 sm:gap-4">
            {isAdmin ? (
              <>
                {navLink('/', 'Overview', accountsActive)}
                {navLink('/review', 'Review queue', reviewsActive)}
              </>
            ) : (
              navLink('/', 'Accounts', accountsActive)
            )}

            <div className="hidden text-right sm:block">
              <p className="text-sm font-semibold text-text">{username}</p>
              <p className="text-xs text-text-subtle">{role === 'ADMIN' ? 'Administrator' : 'Customer'}</p>
            </div>

            <Button
              variant="secondary"
              size="sm"
              onClick={() => {
                signOut()
                navigate('/login', { replace: true })
              }}
            >
              Sign out
            </Button>
          </nav>
        </div>
      </header>

      <main className="mx-auto max-w-6xl px-4 py-8 sm:px-6">
        <Outlet />
      </main>
    </div>
  )
}
