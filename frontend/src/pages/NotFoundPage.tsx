import { Link } from 'react-router-dom'
import { buttonPrimary } from '../components/ui'

/** Catch-all for an unknown path. */
export function NotFoundPage() {
  return (
    <div className="flex min-h-screen flex-col items-center justify-center gap-4 px-4 text-center">
      <p className="text-sm font-semibold uppercase tracking-wide text-emerald-700">404</p>
      <h1 className="text-2xl font-semibold tracking-tight text-slate-900">Page not found</h1>
      <p className="max-w-sm text-sm text-slate-600">
        That address does not match any page in the banking app.
      </p>
      <Link to="/" className={buttonPrimary}>
        Back to your accounts
      </Link>
    </div>
  )
}
