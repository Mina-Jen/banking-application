import { cx } from './ui'

/**
 * The one legal line the whole product shares. Small enough not to compete
 * with the page, present on every screen so nobody mistakes the demo for an
 * institution.
 */
export function SiteFooter({
  variant = 'light',
  className,
}: {
  variant?: 'light' | 'dark'
  className?: string
}) {
  return (
    <footer
      className={cx(
        'text-xs',
        variant === 'dark' ? 'text-on-dark-muted' : 'text-text-subtle',
        className,
      )}
    >
      Demo project. Not a licensed bank.
    </footer>
  )
}
