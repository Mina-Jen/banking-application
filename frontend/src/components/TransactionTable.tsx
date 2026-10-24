import { formatPesos, totalAmounts } from '../lib/money'
import type { Transaction } from '../api/types'
import { EmptyState } from './EmptyState'
import { IconArrowDownLeft, IconArrowUpRight, IconInbox } from './Icon'
import { cx, divider, eyebrow, figure } from './ui'

const INFLOW = new Set(['DEPOSIT', 'TRANSFER_IN'])
const DAY_IN_MS = 86_400_000

type TransactionGroup = {
  key: string
  label: string
  transactions: Transaction[]
}

export function TransactionTable({
  transactions,
  now = new Date(),
}: {
  transactions: Transaction[]
  now?: Date
}) {
  if (transactions.length === 0) {
    return (
      <EmptyState
        title="No transactions yet"
        description="Deposits, withdrawals, and transfers will appear here."
        icon={<IconInbox className="h-6 w-6" />}
      />
    )
  }

  const groups = groupTransactions(transactions, now)

  return (
    <div className="space-y-7">
      {groups.map((group) => {
        const credits = totalAmounts(
          group.transactions
            .filter((transaction) => INFLOW.has(transaction.type))
            .map((transaction) => transaction.amount),
        )
        const debits = totalAmounts(
          group.transactions
            .filter((transaction) => !INFLOW.has(transaction.type))
            .map((transaction) => transaction.amount),
        )

        return (
        <section key={group.key} aria-labelledby={`transactions-${group.key}`}>
          <div className="flex flex-wrap items-baseline justify-between gap-x-4 gap-y-1">
            <h3 id={`transactions-${group.key}`} className={eyebrow}>
              {group.label}
            </h3>
            {/* The day's totals, the way a printed statement foots each page. */}
            <p className={cx(figure, 'text-xs text-text-subtle')}>
              In {formatPesos(credits)} · Out {formatPesos(debits)}
            </p>
          </div>
          <ul className={cx(divider, 'mt-2')}>
            {group.transactions.map((transaction) => {
              const inflow = INFLOW.has(transaction.type)
              const DirectionIcon = inflow ? IconArrowDownLeft : IconArrowUpRight

              return (
                <li
                  key={transaction.id}
                  className="grid grid-cols-[auto_minmax(0,1fr)_auto] items-center gap-3 py-3.5 sm:grid-cols-[auto_minmax(0,1fr)_auto_auto] sm:gap-5"
                >
                  <span
                    className={cx(
                      'flex h-10 w-10 items-center justify-center rounded-full',
                      inflow ? 'bg-success-soft text-success-strong' : 'bg-danger-soft text-danger-strong',
                    )}
                  >
                    <DirectionIcon className="h-5 w-5" />
                  </span>

                  <span className="min-w-0">
                    <span className="block text-sm font-semibold leading-snug text-text">
                      {transactionTitle(transaction)}
                    </span>
                    <span className="mt-0.5 block truncate text-xs text-text-subtle">
                      {formatTime(transaction.createdAt)}
                      <span className={cx(figure, 'ml-2 text-[11px]')}>{transaction.reference}</span>
                    </span>
                  </span>

                  {/* The balance the transaction left behind: what a statement's
                      third column is for, and the API already sends it. */}
                  <span className={cx(figure, 'hidden text-xs text-text-subtle sm:block')}>
                    <span className="sr-only">Balance after: </span>
                    {formatPesos(transaction.balanceAfter)}
                  </span>

                  <span
                    className={cx(
                      'whitespace-nowrap text-right text-sm font-bold tabular-nums',
                      inflow ? 'text-success' : 'text-danger',
                    )}
                  >
                    {inflow ? '+' : '−'}
                    {formatPesos(transaction.amount)}
                  </span>
                </li>
              )
            })}
          </ul>
        </section>
        )
      })}
    </div>
  )
}

function groupTransactions(transactions: Transaction[], now: Date): TransactionGroup[] {
  const groups = new Map<string, TransactionGroup>()
  const newestFirst = [...transactions].sort(
    (left, right) => timestamp(right.createdAt) - timestamp(left.createdAt),
  )

  for (const transaction of newestFirst) {
    const date = new Date(transaction.createdAt)
    const valid = !Number.isNaN(date.getTime())
    const key = valid ? localDateKey(date) : 'unknown-date'
    const group = groups.get(key) ?? {
      key,
      label: valid ? dateLabel(date, now) : 'Date unavailable',
      transactions: [],
    }
    group.transactions.push(transaction)
    groups.set(key, group)
  }

  return [...groups.values()]
}

function transactionTitle(transaction: Transaction): string {
  // The API sends null here for deposits and withdrawals despite the generated type.
  const referenceAccount = transaction.referenceAccount as string | null
  const otherAccount = referenceAccount?.trim() || 'another account'
  switch (transaction.type) {
    case 'TRANSFER_OUT':
      return `Sent to ${otherAccount}`
    case 'TRANSFER_IN':
      return `Received from ${otherAccount}`
    case 'WITHDRAW':
      return 'Withdrawal'
    default:
      return 'Deposit'
  }
}

function dateLabel(date: Date, now: Date): string {
  const difference = localDayNumber(now) - localDayNumber(date)
  if (difference === 0) {
    return 'Today'
  }
  if (difference === 1) {
    return 'Yesterday'
  }
  return date.toLocaleDateString(undefined, {
    month: 'long',
    day: 'numeric',
    year: 'numeric',
  })
}

function formatTime(iso: string): string {
  const date = new Date(iso)
  if (Number.isNaN(date.getTime())) {
    return 'Time unavailable'
  }
  return date.toLocaleTimeString(undefined, {
    hour: 'numeric',
    minute: '2-digit',
  })
}

function timestamp(iso: string): number {
  const value = new Date(iso).getTime()
  return Number.isNaN(value) ? Number.NEGATIVE_INFINITY : value
}

function localDateKey(date: Date): string {
  return `${date.getFullYear()}-${date.getMonth() + 1}-${date.getDate()}`
}

function localDayNumber(date: Date): number {
  return Date.UTC(date.getFullYear(), date.getMonth(), date.getDate()) / DAY_IN_MS
}
