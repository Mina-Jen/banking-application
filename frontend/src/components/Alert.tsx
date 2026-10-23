import type { ReactNode } from 'react'
import { cx } from './ui'

type Tone = 'error' | 'success' | 'info'

const TONES: Record<Tone, string> = {
  error: 'bg-red-50 text-red-800 ring-red-200',
  success: 'bg-emerald-50 text-emerald-800 ring-emerald-200',
  info: 'bg-slate-50 text-slate-700 ring-slate-200',
}

/**
 * A status banner. `role="alert"` on errors makes screen readers announce the
 * message the moment it appears instead of leaving it invisible.
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
      className={cx('rounded-lg px-4 py-3 text-sm ring-1', TONES[tone])}
    >
      {title ? <p className="font-semibold">{title}</p> : null}
      <div className={title ? 'mt-1' : undefined}>{children}</div>
    </div>
  )
}
