import { formatPesos } from '../lib/money'
import type { Transaction } from '../api/types'
import { EmptyState } from './EmptyState'
import { IconArrowDownLeft, IconArrowUpRight, IconInbox } from './Icon'
import { cx, eyebrow } from './ui'

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
    <div className="space-y-6">
      {groups.map((group) => (
        <section key={group.key} aria-labelledby={`transactions-${group.key}`}>
          <h3 id={`transactions-${group.key}`} className={eyebrow}>
            {group.label}
          </h3>
          <ul className="mt-2 divide-y divide-line">
            {group.transactions.map((transaction) => {
              const inflow = INFLOW.has(transaction.type)
              const DirectionIcon = inflow ? IconArrowDownLeft : IconArrowUpRight

              return (
                <li
                  key={transaction.id}
                  className="grid grid-cols-[auto_minmax(0,1fr)_auto] items-center gap-3 py-4"
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
                      {formatTime(transaction.createdAt)} · {transaction.reference}
                    </span>
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
      ))}
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
