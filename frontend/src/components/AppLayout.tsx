import { Link, NavLink, Outlet, useNavigate } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'
import { buttonSecondary, cx } from './ui'

/**
 * The signed-in shell: a header with who you are, and an <Outlet/> where the
 * protected pages render. Reached only through <RequireAuth>.
 */
export function AppLayout() {
  const { username, role, signOut } = useAuth()
  const navigate = useNavigate()

  return (
    <div className="min-h-screen">
      <header className="border-b border-slate-200 bg-white">
        <div className="mx-auto flex max-w-5xl flex-wrap items-center gap-4 px-4 py-3 sm:px-6">
          <Link to="/" className="flex items-center gap-2 text-lg font-semibold text-slate-900">
            <span
              aria-hidden="true"
              className="flex h-8 w-8 items-center justify-center rounded-lg bg-emerald-700 text-sm font-bold text-white"
            >
              C
            </span>
            CIICC Banking
          </Link>

          <nav aria-label="Main" className="ml-auto flex items-center gap-4">
            <NavLink
              to="/"
              end
              className={({ isActive }) =>
                cx(
                  'text-sm font-medium',
                  isActive ? 'text-emerald-700' : 'text-slate-600 hover:text-slate-900',
                )
              }
            >
              Accounts
            </NavLink>

            <div className="hidden text-right sm:block">
              <p className="text-sm font-medium text-slate-900">{username}</p>
              <p className="text-xs text-slate-500">{role === 'ADMIN' ? 'Administrator' : 'Customer'}</p>
            </div>

            <button
              type="button"
              className={buttonSecondary}
              onClick={() => {
                signOut()
                navigate('/login', { replace: true })
              }}
            >
              Sign out
            </button>
          </nav>
        </div>
      </header>

      <main className="mx-auto max-w-5xl px-4 py-8 sm:px-6">
        <Outlet />
      </main>
    </div>
  )
}
