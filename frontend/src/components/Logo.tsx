import { cx, display } from './ui'

/**
 * The Sinag mark: a sun breaking over a horizon. A half circle sits on the
 * horizon line and five rounded rays lift away from it.
 *
 * Drawn here rather than sourced from a brand pack — it is original artwork,
 * and it is a path, not an image, so it recolours with the text around it.
 *
 * `variant` names the surface the mark sits on, not its own colour: `light`
 * draws in the action blue for white pages, `dark` in white for the hero.
 */
export function Logo({
  size = 32,
  variant = 'light',
  className,
}: {
  size?: number
  variant?: 'light' | 'dark'
  className?: string
}) {
  return (
    <svg
      width={size}
      height={size}
      viewBox="0 0 32 32"
      fill="none"
      aria-hidden="true"
      className={cx('shrink-0', variant === 'dark' ? 'text-on-dark' : 'text-action', className)}
    >
      <path d="M3.5 21.5h25" stroke="currentColor" strokeWidth="2.4" strokeLinecap="round" />
      <path
        d="M8.8 21.5a7.2 7.2 0 0 1 14.4 0"
        stroke="currentColor"
        strokeWidth="2.4"
        strokeLinecap="round"
      />
      <path
        d="M16 11.1V7.9M21.2 12.5l1.6-2.8M10.8 12.5 9.2 9.7M25 16.3l2.8-1.6M7 16.3l-2.8-1.6"
        stroke="currentColor"
        strokeWidth="2.4"
        strokeLinecap="round"
      />
    </svg>
  )
}

/**
 * Mark plus wordmark, as used in the header and on the sign-in screens. The
 * name is real text, not part of the drawing, so it is readable, selectable
 * and translatable.
 */
export function LogoWordmark({
  size = 30,
  variant = 'light',
  className,
}: {
  size?: number
  variant?: 'light' | 'dark'
  className?: string
}) {
  return (
    <span className={cx('inline-flex items-center gap-2.5', className)}>
      <Logo size={size} variant={variant} />
      <span
        className={cx(
          display,
          'text-lg font-bold uppercase leading-none tracking-[-0.02em]',
          variant === 'dark' ? 'text-on-dark' : 'text-text',
        )}
      >
        SINAG
      </span>
    </span>
  )
}
