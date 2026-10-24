import { cx } from './ui'

/**
 * Inline spinner. `aria-hidden` because the surrounding text carries meaning:
 * under prefers-reduced-motion the sweep is switched off globally, and the only
 * thing left saying "working" is the button's own label.
 */
export function Spinner({ className }: { className?: string }) {
  return (
    <svg
      aria-hidden="true"
      className={cx('h-4 w-4 animate-spin', className)}
      viewBox="0 0 24 24"
      fill="none"
    >
      <circle cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" className="opacity-25" />
      <path
        d="M22 12a10 10 0 0 0-10-10"
        stroke="currentColor"
        strokeWidth="4"
        strokeLinecap="round"
      />
    </svg>
  )
}
