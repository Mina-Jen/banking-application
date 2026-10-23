import { formatPesos } from '../lib/money'
import type { Transaction } from '../api/types'
import { cx, tableCell, tableHead } from './ui'

/** Money arriving in the account, so it is drawn in green with a plus sign. */
const INFLOW = new Set(['DEPOSIT', 'TRANSFER_IN'])

const TYPE_LABELS: Record<string, string> = {
  DEPOSIT: 'Deposit',
  WITHDRAW: 'Withdrawal',
  TRANSFER_OUT: 'Transfer out',
  TRANSFER_IN: 'Transfer in',
}

/**
 * One page of an account's transaction history.
 *
 * `createdAt` is an ISO-8601 instant from the backend; it is rendered in the
 * browser's own time zone. Amounts stay strings and are only ever formatted,
 * never parsed into numbers.
 */
export function TransactionTable({ transactions }: { transactions: Transaction[] }) {
  if (transactions.length === 0) {
    return (
      <p className="py-8 text-center text-sm text-slate-500">
        No transactions on this page yet.
      </p>
    )
  }

  return (
    <div className="overflow-x-auto">
      <table className="min-w-full divide-y divide-slate-200">
        <caption className="sr-only">Transaction history, oldest first</caption>
        <thead>
          <tr>
            <th scope="col" className={tableHead}>Date</th>
            <th scope="col" className={tableHead}>Type</th>
            <th scope="col" className={tableHead}>Other account</th>
            <th scope="col" className={cx(tableHead, 'text-right')}>Amount</th>
            <th scope="col" className={cx(tableHead, 'text-right')}>Balance after</th>
            <th scope="col" className={tableHead}>Reference</th>
          </tr>
        </thead>
        <tbody className="divide-y divide-slate-100">
          {transactions.map((transaction) => {
            const inflow = INFLOW.has(transaction.type)
            return (
              <tr key={transaction.id} className="hover:bg-slate-50">
                <td className={cx(tableCell, 'whitespace-nowrap')}>
                  {formatDate(transaction.createdAt)}
                </td>
                <td className={tableCell}>
                  {TYPE_LABELS[transaction.type] ?? transaction.type}
                </td>
                <td className={cx(tableCell, 'tabular-money text-left')}>
                  {transaction.referenceAccount || <span className="text-slate-400">—</span>}
                </td>
                <td
                  className={cx(
                    tableCell,
                    'tabular-money font-medium',
                    inflow ? 'text-emerald-700' : 'text-red-700',
                  )}
                >
                  {inflow ? '+' : '−'}
                  {formatPesos(transaction.amount)}
                </td>
                <td className={cx(tableCell, 'tabular-money text-slate-900')}>
                  {formatPesos(transaction.balanceAfter)}
                </td>
                <td className={cx(tableCell, 'font-mono text-xs text-slate-500')}>
                  {transaction.reference}
                </td>
              </tr>
            )
          })}
        </tbody>
      </table>
    </div>
  )
}

/** ISO instant to a short local date and time; falls back to the raw string. */
function formatDate(iso: string): string {
  const date = new Date(iso)
  if (Number.isNaN(date.getTime())) {
    return iso
  }
  return date.toLocaleString(undefined, {
    year: 'numeric',
    month: 'short',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  })
}
