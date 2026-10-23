import type { ReactNode } from 'react'
import { Link } from 'react-router-dom'

/**
 * Centred card used by the login and register pages, which are the only screens
 * reachable without a token.
 */
export function AuthLayout({
  title,
  subtitle,
  children,
  footer,
}: {
  title: string
  subtitle: string
  children: ReactNode
  footer: ReactNode
}) {
  return (
    <div className="flex min-h-screen items-center justify-center bg-slate-100 px-4 py-12">
      <div className="w-full max-w-md">
        <div className="mb-6 text-center">
          <Link to="/" className="inline-flex items-center gap-2">
            <span
              aria-hidden="true"
              className="flex h-10 w-10 items-center justify-center rounded-xl bg-emerald-700 text-lg font-bold text-white"
            >
              C
            </span>
            <span className="text-xl font-semibold text-slate-900">CIICC Banking</span>
          </Link>
          <h1 className="mt-6 text-2xl font-semibold tracking-tight text-slate-900">{title}</h1>
          <p className="mt-1 text-sm text-slate-600">{subtitle}</p>
        </div>

        <div className="rounded-xl bg-white p-6 shadow-sm ring-1 ring-slate-200">{children}</div>

        <p className="mt-4 text-center text-sm text-slate-600">{footer}</p>
      </div>
    </div>
  )
}
