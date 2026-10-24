import type { ReactNode } from 'react'
import { IconInbox } from './Icon'
import { cx, subheading } from './ui'

/**
 * A screen with nothing in it is an invitation to act, so it says what is
 * missing and offers the one thing that would fill it. The message is never
 * vague ("No transactions yet" plus the deposit that fixes it), and the action
 * is a real control, not a sentence telling the user to go find one.
 */
export function EmptyState({
  title,
  description,
  action,
  icon,
  className,
}: {
  title: string
  description?: string
  action?: ReactNode
  icon?: ReactNode
  className?: string
}) {
  return (
    <div className={cx('flex flex-col items-center gap-1 py-10 text-center', className)}>
      <span
        aria-hidden="true"
        className="mb-3 flex h-12 w-12 items-center justify-center rounded-full bg-surface-muted text-text-subtle ring-1 ring-line"
      >
        {icon ?? <IconInbox className="h-6 w-6" />}
      </span>
      <h3 className="text-display-sm font-bold text-text">{title}</h3>
      {description ? <p className={cx(subheading, 'max-w-sm')}>{description}</p> : null}
      {action ? <div className="mt-4">{action}</div> : null}
    </div>
  )
}

/** For a slot inside a table or list where a whole empty state would shout. */
export function EmptyLine({ children }: { children: ReactNode }) {
  return <p className="py-8 text-center text-sm text-text-subtle">{children}</p>
}
