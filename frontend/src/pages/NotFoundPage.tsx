import { Link } from 'react-router-dom'
import { buttonClasses } from '../components/ui'

/** Catch-all for an unknown path. */
export function NotFoundPage() {
  return (
    <div className="flex min-h-screen flex-col items-center justify-center gap-4 px-4 text-center">
      <p className="text-xs font-bold uppercase tracking-[0.18em] text-action">404</p>
      <h1 className="text-display-lg text-text display">Page not found</h1>
      <p className="max-w-sm text-sm text-text-muted">
        That address does not match any page in the banking app.
      </p>
      <Link to="/" className={buttonClasses('primary', 'md', 'mt-2')}>
        Back to your accounts
      </Link>
    </div>
  )
}
