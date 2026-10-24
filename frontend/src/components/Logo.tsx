import { cx } from './ui'

/**
 * The CIICC mark: an open ring with a transfer arrow leaving through the gap.
 * Drawn here rather than sourced from a brand pack — it is original artwork, and
 * it is a path, not an image, so it recolours with the text around it.
 *
 * `tone` picks the colour pair; `wordmark` adds the name beside it for headers
 * and the sign-in screen.
 */
export function Logo({
  size = 32,
  tone = 'onLight',
  className,
}: {
  size?: number
  tone?: 'onLight' | 'onDark' | 'current'
  className?: string
}) {
  return (
    <svg
      width={size}
      height={size}
      viewBox="0 0 28 28"
      fill="none"
      aria-hidden="true"
      className={cx(
        'shrink-0',
        tone === 'onDark' && 'text-on-dark',
        tone === 'onLight' && 'text-action',
        className,
      )}
    >
      <path
        d="M23.5 6.6A11.2 11.2 0 1 0 23.5 21.4"
        stroke="currentColor"
        strokeWidth="2.6"
        strokeLinecap="round"
      />
      <path
        d="M8.5 14H21M17.4 10.4 21 14l-3.6 3.6"
        stroke="currentColor"
        strokeWidth="2.6"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
    </svg>
  )
}

/**
 * Mark plus name, as used in the header and on the sign-in screens. The name is
 * real text, not part of the drawing, so it is readable, selectable and
 * translatable.
 */
export function LogoWordmark({
  size = 32,
  tone = 'onLight',
  className,
}: {
  size?: number
  tone?: 'onLight' | 'onDark'
  className?: string
}) {
  return (
    <span className={cx('inline-flex items-center gap-2.5', className)}>
      <Logo size={size} tone={tone} />
      <span
        className={cx(
          'text-base font-extrabold uppercase tracking-tight',
          tone === 'onDark' ? 'text-on-dark' : 'text-text',
        )}
      >
        CIICC Banking
      </span>
    </span>
  )
}
