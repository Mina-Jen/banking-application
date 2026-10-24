import { useId, type ReactNode } from 'react'
import { card, cardRaised, cx, heading, subheading } from './ui'

interface CardProps {
  /** Renders the heading and labels the section with it, so the region is
      announced by name rather than as "region, " with nothing in it. */
  title?: string
  description?: ReactNode
  /** Controls beside the heading: refresh, open, collapse. */
  actions?: ReactNode
  /** `raised` is for a sheet that overlaps the hero and needs to read as nearer. */
  level?: 'flat' | 'raised'
  className?: string
  children?: ReactNode
}

/** A titled surface. For an untitled block of utilities, use the `card` class
    from ui.ts on the element you already have instead of nesting another box. */
export function Card({
  title,
  description,
  actions,
  level = 'flat',
  className,
  children,
}: CardProps) {
  const headingId = useId()

  return (
    <section
      aria-labelledby={title ? headingId : undefined}
      className={cx(level === 'raised' ? cardRaised : card, className)}
    >
      {title ? (
        <div className={cx('flex flex-wrap items-start justify-between gap-3', children ? 'mb-4' : undefined)}>
          <div>
            <h2 id={headingId} className={heading}>
              {title}
            </h2>
            {description ? <p className={cx(subheading, 'mt-1')}>{description}</p> : null}
          </div>
          {actions ? <div className="flex items-center gap-2">{actions}</div> : null}
        </div>
      ) : null}
      {children}
    </section>
  )
}
