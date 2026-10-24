import type { ReactNode } from 'react'
import { Link } from 'react-router-dom'
import { LogoWordmark } from './Logo'
import { cx, display, focusRingOnDark } from './ui'

const LEDGER_FACTS = [
  'Deposits, withdrawals and transfers in one ledger',
  'A reference number on every movement',
  'Balances kept to the centavo',
]

function HomeLink() {
  return (
    <Link to="/" className={cx('inline-flex rounded-md', focusRingOnDark)}>
      <LogoWordmark size={34} tone="onDark" />
    </Link>
  )
}

/**
 * The frame for the login and register pages, the only screens reachable
 * without a token. The whole page is the hero gradient and the form sits
 * directly on it, which is why its main action is the white pill.
 *
 * From `lg` up the page splits: the brand panel on the bright side of the
 * gradient, and the form on a darker plate where the white controls read best.
 * Below `lg` the brand panel is dropped and only the form remains, so a phone
 * loads straight into the fields.
 *
 * The brand panel has no heading on purpose: the page's one heading is the
 * form's title, which is what tests and screen readers look for.
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
    <div className="hero-gradient min-h-dvh lg:grid lg:grid-cols-[minmax(0,1.15fr)_minmax(28rem,1fr)]">
      <div className="hidden flex-col justify-between p-14 lg:flex">
        <HomeLink />
        <div>
          <p className={cx('max-w-[11ch] text-display-xl text-on-dark', display)}>
            Every peso, accounted for.
          </p>
          <ul className="mt-10 max-w-md divide-y divide-on-dark/15 border-y border-on-dark/15">
            {LEDGER_FACTS.map((fact) => (
              <li key={fact} className="py-3 text-sm text-on-dark-muted">
                {fact}
              </li>
            ))}
          </ul>
        </div>
      </div>

      <main className="flex min-h-dvh flex-col px-5 py-8 sm:px-10 lg:border-l lg:border-on-dark/10 lg:bg-depth-scrim/55 lg:px-14">
        <div className="lg:hidden">
          <HomeLink />
        </div>

        <div className="mx-auto flex w-full max-w-sm flex-1 flex-col justify-center py-10">
          <h1 className={cx('text-display-xl text-on-dark lg:text-display-lg', display)}>{title}</h1>
          <p className="mt-3 text-base text-pretty text-on-dark-muted">{subtitle}</p>

          <div className="mt-8">{children}</div>

          <p className="mt-8 text-sm text-on-dark-muted">{footer}</p>
        </div>
      </main>
    </div>
  )
}
