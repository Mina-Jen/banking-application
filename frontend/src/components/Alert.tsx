import type { ReactNode } from 'react'
import { IconAlert, IconCheck, IconInfo } from './Icon'
import { cx } from './ui'

type Tone = 'error' | 'success' | 'info'

const TONES: Record<Tone, { panel: string; icon: ReactNode }> = {
  error: { panel: 'bg-danger-soft text-danger-strong ring-danger/20', icon: <IconAlert className="h-5 w-5" /> },
  success: {
    panel: 'bg-success-soft text-success-strong ring-success/20',
    icon: <IconCheck className="h-5 w-5" />,
  },
  info: { panel: 'bg-surface-muted text-text ring-line', icon: <IconInfo className="h-5 w-5" /> },
}

/**
 * A status banner for something that belongs in the page's flow — a rejected
 * form, a completed deposit — as opposed to a transient result, which is a
 * <Toast>.
 *
 * `role="alert"` on errors makes screen readers announce the message the moment
 * it appears instead of leaving it invisible. The icon is there for the same
 * reason colour is not enough: red and green mean opposite things here, and a
 * shape says which one even when the colour cannot be seen.
 */
export function Alert({
  tone,
  title,
  children,
}: {
  tone: Tone
  title?: string
  children: ReactNode
}) {
  return (
    <div
      role={tone === 'error' ? 'alert' : 'status'}
      className={cx('flex items-start gap-3 rounded-lg px-4 py-3 text-sm ring-1', TONES[tone].panel)}
    >
      <span aria-hidden="true" className="mt-0.5 shrink-0">
        {TONES[tone].icon}
      </span>
      <div className="min-w-0 flex-1">
        {title ? <p className="font-semibold">{title}</p> : null}
        <div className={title ? 'mt-1' : undefined}>{children}</div>
      </div>
    </div>
  )
}
