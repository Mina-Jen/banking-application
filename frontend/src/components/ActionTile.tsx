import { Link } from 'react-router-dom'
import { useId, type ComponentPropsWithRef, type ReactNode } from 'react'
import { cx, focusRing, focusRingOnDark } from './ui'

interface ActionTileProps {
  icon: ReactNode
  /** The verb the control performs: "Deposit", "Convert". Not a description. */
  label: string
  hint?: string
  /** Give `to` for a tile that navigates, `onClick` for one that acts. */
  to?: string
  onClick?: () => void
  disabled?: boolean
  tone?: 'light' | 'onDark'
  className?: string
}

const TILE =
  'press flex min-h-26 w-full flex-col items-start justify-between gap-4 rounded-xl p-3 text-left ' +
  'disabled:cursor-not-allowed disabled:opacity-45 disabled:[transform:none] sm:p-4 '
const LIGHT = cx(TILE, 'bg-surface text-text ring-1 ring-line hover:ring-2 hover:ring-action', focusRing)
const ON_DARK = cx(
  TILE,
  'bg-depth-scrim/45 text-on-dark ring-1 ring-on-dark/25 hover:bg-depth-scrim/65',
  focusRingOnDark,
)

/**
 * A tappable square on the money-actions row. The whole tile is the target, not
 * the glyph inside it, so the tap area is far above the 44px minimum and a
 * mis-hit is not a mis-money-movement.
 *
 * Tiles are links or buttons, never divs with a click handler: keyboard and
 * screen-reader users get the same affordance, and a link can be opened in a new
 * tab the way people expect of a destination.
 */
export function ActionTile({
  icon,
  label,
  hint,
  to,
  onClick,
  disabled = false,
  tone = 'light',
  className,
}: ActionTileProps) {
  const hintId = useId()
  const classes = cx(tone === 'onDark' ? ON_DARK : LIGHT, className)
  // Named by the verb alone. Left to the markup, the label and the hint run
  // together into one word, and the control that moves money is announced as
  // "DepositInto savings".
  const accessibility = {
    'aria-label': label,
    'aria-describedby': hint ? hintId : undefined,
  }
  const badge =
    tone === 'onDark'
      ? 'bg-on-dark/15 text-on-dark'
      : 'bg-action-soft text-action-strong'

  const inner = (
    <>
      <span
        aria-hidden="true"
        className={cx('flex h-10 w-10 items-center justify-center rounded-lg', badge)}
      >
        {icon}
      </span>
      <span className="block">
        <span className="block text-sm font-semibold">{label}</span>
        {hint ? (
          <span
            id={hintId}
            className={cx(
              'mt-0.5 block text-xs',
              tone === 'onDark' ? 'text-on-dark-muted' : 'text-text-subtle',
            )}
          >
            {hint}
          </span>
        ) : null}
      </span>
    </>
  )

  if (to) {
    return (
      <Link to={to} className={classes} {...accessibility}>
        {inner}
      </Link>
    )
  }

  const buttonProps: ComponentPropsWithRef<'button'> = {
    type: 'button',
    className: classes,
    onClick,
    disabled,
    ...accessibility,
  }

  return <button {...buttonProps}>{inner}</button>
}
