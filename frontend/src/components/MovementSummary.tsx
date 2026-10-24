import { cx } from './ui'

export interface SummaryRow {
  label: string
  value: string
  /** Render in the monospace face, for account numbers and references. */
  mono?: boolean
  /** Emphasise the figure the user is about to commit. */
  strong?: boolean
}

/**
 * The facts of a money movement, shown inside a ConfirmDialog so the user
 * confirms figures rather than a button label. Values arrive already formatted
 * (see formatPesos); this component only lays them out.
 */
export function MovementSummary({ rows }: { rows: SummaryRow[] }) {
  return (
    <dl className="divide-y divide-line rounded-lg bg-surface-muted px-4 ring-1 ring-line">
      {rows.map((row) => (
        <div key={row.label} className="flex items-baseline justify-between gap-4 py-2.5">
          <dt className="text-sm text-text-muted">{row.label}</dt>
          <dd
            className={cx(
              'text-right text-sm text-text',
              row.mono && 'font-mono',
              row.strong && 'tabular-money text-lg font-semibold',
            )}
          >
            {row.value}
          </dd>
        </div>
      ))}
    </dl>
  )
}
