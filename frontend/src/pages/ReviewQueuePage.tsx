import { useCallback, useEffect, useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import { Alert } from '../components/Alert'
import { Button } from '../components/Button'
import { ConfirmDialog } from '../components/ConfirmDialog'
import { EmptyState } from '../components/EmptyState'
import { IconAlert, IconCheck, IconInbox, IconRefresh } from '../components/Icon'
import { Skeleton } from '../components/Skeleton'
import { Toast } from '../components/Toast'
import { buttonClasses, card, cx, display, eyebrow, money } from '../components/ui'
import { bankingApi, type AnomalyFlag, type AnomalyFlagPage, type AnomalyStatus } from '../api/banking'
import { errorMessage } from '../api/errors'
import { formatDateTime } from '../lib/dates'
import { formatPesos } from '../lib/money'

const PAGE_SIZE = 10

type Filter = 'ALL' | AnomalyStatus
type PendingDecision = { flag: AnomalyFlag; status: 'REVIEWED' | 'DISMISSED' }
type ToastState = { tone: 'success' | 'error'; message: string }

const FILTERS: Array<{ value: Filter; label: string }> = [
  { value: 'OPEN', label: 'Open' },
  { value: 'REVIEWED', label: 'Reviewed' },
  { value: 'DISMISSED', label: 'Dismissed' },
  { value: 'ALL', label: 'All' },
]

const RULE_LABEL: Record<string, string> = {
  UNUSUAL_AMOUNT: 'Unusual amount',
  VELOCITY: 'Rapid movement',
}

const STATUS_STYLE: Record<AnomalyStatus, string> = {
  OPEN: 'bg-warning-soft text-warning-strong ring-warning/20',
  REVIEWED: 'bg-success-soft text-success-strong ring-success/20',
  DISMISSED: 'bg-surface-muted text-text-muted ring-line',
}

function StatusBadge({ status }: { status: AnomalyStatus }) {
  return (
    <span
      className={cx(
        'inline-flex items-center gap-1.5 rounded-full px-2.5 py-0.5 text-xs font-semibold ring-1',
        STATUS_STYLE[status],
      )}
    >
      {status === 'OPEN' ? (
        <IconAlert className="h-3.5 w-3.5" />
      ) : status === 'REVIEWED' ? (
        <IconCheck className="h-3.5 w-3.5" />
      ) : null}
      {status === 'OPEN' ? 'Open' : status === 'REVIEWED' ? 'Reviewed' : 'Dismissed'}
    </span>
  )
}

/**
 * The ADMIN work queue for rule findings: unusual amounts and bursts of
 * outbound transactions.
 *
 * A decision closes a flag for good, so both buttons ask for confirmation and
 * the dialog says so. Nothing here moves money; a flag is a note to a human, and
 * the transaction it describes has already been recorded.
 */
export function ReviewQueuePage() {
  const [filter, setFilter] = useState<Filter>('OPEN')
  const [page, setPage] = useState(0)
  const [result, setResult] = useState<AnomalyFlagPage | null>(null)
  const [loading, setLoading] = useState(true)
  const [failure, setFailure] = useState<string | null>(null)
  const [pending, setPending] = useState<PendingDecision | null>(null)
  const [deciding, setDeciding] = useState(false)
  const [toast, setToast] = useState<ToastState | null>(null)

  /** A slow response for an old filter must not overwrite the new filter's list. */
  const sequence = useRef(0)

  const load = useCallback(async (status: Filter, targetPage: number) => {
    const current = ++sequence.current
    setLoading(true)
    setFailure(null)
    try {
      const pageResult = await bankingApi.anomalyFlags(
        status === 'ALL' ? undefined : status,
        targetPage,
        PAGE_SIZE,
      )
      if (current !== sequence.current) {
        return
      }
      setResult(pageResult)
      setPage(pageResult.page)
    } catch (cause) {
      if (current === sequence.current) {
        setFailure(errorMessage(cause))
        setResult(null)
      }
    } finally {
      if (current === sequence.current) {
        setLoading(false)
      }
    }
  }, [])

  useEffect(() => {
    void load(filter, 0)
  }, [filter, load])

  async function decide() {
    if (pending === null) {
      return
    }
    setDeciding(true)
    try {
      await bankingApi.reviewAnomalyFlag(pending.flag.id, pending.status)
      setToast({
        tone: 'success',
        message:
          pending.status === 'REVIEWED'
            ? `Flag ${pending.flag.transactionReference} marked as reviewed.`
            : `Flag ${pending.flag.transactionReference} dismissed.`,
      })
      setPending(null)
      await load(filter, page)
    } catch (cause) {
      setToast({ tone: 'error', message: errorMessage(cause) })
      setPending(null)
    } finally {
      setDeciding(false)
    }
  }

  const flags = result?.flags ?? []
  const totalPages = result?.totalPages ?? 1
  const totalElements = result?.totalElements ?? 0

  return (
    <div className="space-y-6">
      <section className="hero-gradient overflow-hidden rounded-2xl p-5 shadow-lg sm:p-8">
        <div className="flex flex-wrap items-start justify-between gap-6">
          <div>
            <p className="text-xs font-bold uppercase tracking-[0.18em] text-on-dark-muted">
              Risk review
            </p>
            <h1 className={cx('mt-2 text-display-md text-on-dark sm:text-display-lg', display)}>
              Anomaly review
            </h1>
            <p className="mt-3 max-w-xl text-sm text-on-dark-muted">
              Transactions the rules flagged for a human decision. Closing a flag records who
              reviewed it and when.
            </p>
          </div>
          <Button
            variant="onDarkOutline"
            size="sm"
            onClick={() => void load(filter, page)}
            disabled={loading}
          >
            <IconRefresh className="h-4 w-4" />
            Refresh
          </Button>
        </div>

        <div className="mt-8 flex flex-wrap items-center gap-2" role="group" aria-label="Filter flags">
          {FILTERS.map((option) => (
            <button
              key={option.value}
              type="button"
              aria-pressed={filter === option.value}
              onClick={() => setFilter(option.value)}
              className={cx(
                'press inline-flex min-h-9 items-center rounded-full px-3 text-xs font-semibold',
                'focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-on-dark',
                filter === option.value
                  ? 'bg-on-dark text-ink-900'
                  : 'text-on-dark ring-1 ring-on-dark/50 hover:bg-depth-scrim/40',
              )}
            >
              {option.label}
            </button>
          ))}
        </div>
      </section>

      <section aria-labelledby="flags-heading" className="space-y-4">
        <div className="flex flex-wrap items-end justify-between gap-2">
          <div>
            <p className={eyebrow}>{filter === 'OPEN' ? 'Waiting on you' : 'Flags'}</p>
            <h2 id="flags-heading" className="mt-1 text-display-sm font-bold text-text">
              {totalElements === 0
                ? 'No flags'
                : `${totalElements} flag${totalElements === 1 ? '' : 's'}`}
            </h2>
          </div>
          <p className="text-xs text-text-subtle">Newest first</p>
        </div>

        {loading && result === null ? (
          <div className="space-y-3" role="status">
            <span className="sr-only">Loading flags…</span>
            {[0, 1, 2].map((row) => (
              <Skeleton key={row} className="h-32 rounded-xl" />
            ))}
          </div>
        ) : failure !== null ? (
          <Alert tone="error" title="The review queue could not be loaded">
            <p>{failure}</p>
            <Button variant="secondary" size="sm" className="mt-3" onClick={() => void load(filter, 0)}>
              Try again
            </Button>
          </Alert>
        ) : flags.length === 0 ? (
          <div className={card}>
            <EmptyState
              title={filter === 'OPEN' ? 'Nothing waiting for review' : 'No flags in this view'}
              description={
                filter === 'OPEN'
                  ? 'Every flagged transaction has a decision. New flags appear here as they are raised.'
                  : 'Try another status filter.'
              }
              icon={<IconInbox className="h-6 w-6" />}
            />
          </div>
        ) : (
          <ul className="space-y-3">
            {flags.map((flag) => (
              <li key={flag.id} className={card}>
                <div className="flex flex-wrap items-start justify-between gap-4">
                  <div className="min-w-0">
                    <div className="flex flex-wrap items-center gap-2">
                      <span className="rounded-full bg-warning-soft px-2.5 py-0.5 text-xs font-semibold text-warning-strong ring-1 ring-warning/20">
                        {RULE_LABEL[flag.rule] ?? flag.rule}
                      </span>
                      <StatusBadge status={flag.status} />
                    </div>
                    <p className="mt-3 text-sm text-text">
                      <Link
                        to={`/accounts/${encodeURIComponent(flag.accountNumber)}`}
                        className="font-semibold text-action hover:text-action-strong"
                      >
                        Account {flag.accountNumber}
                      </Link>{' '}
                      · {flag.transactionType === 'WITHDRAW' ? 'Withdrawal' : 'Transfer out'} ·{' '}
                      <span className="font-mono text-xs text-text-subtle">
                        {flag.transactionReference}
                      </span>
                    </p>
                    <p className="mt-2 max-w-2xl text-sm text-text-muted">{flag.details}</p>
                    <p className="mt-2 text-xs text-text-subtle">
                      Flagged {formatDateTime(flag.createdAt)}
                      {flag.status !== 'OPEN' && flag.reviewedAt
                        ? ` · ${flag.status === 'REVIEWED' ? 'Reviewed' : 'Dismissed'} by ${flag.reviewedBy ?? 'an admin'} on ${formatDateTime(flag.reviewedAt)}`
                        : null}
                    </p>
                  </div>
                  <div className="flex flex-col items-end gap-3">
                    <p className={cx('text-lg font-bold text-text', money)}>
                      {formatPesos(flag.amount)}
                    </p>
                    {flag.status === 'OPEN' ? (
                      <div className="flex flex-wrap justify-end gap-2">
                        <Button
                          size="sm"
                          variant="secondary"
                          onClick={() => setPending({ flag, status: 'DISMISSED' })}
                        >
                          Dismiss
                        </Button>
                        <Button
                          size="sm"
                          onClick={() => setPending({ flag, status: 'REVIEWED' })}
                        >
                          Mark reviewed
                        </Button>
                      </div>
                    ) : (
                      <Link
                        to={`/accounts/${encodeURIComponent(flag.accountNumber)}`}
                        className={buttonClasses('ghost', 'sm')}
                      >
                        View account
                      </Link>
                    )}
                  </div>
                </div>
              </li>
            ))}
          </ul>
        )}

        {totalPages > 1 ? (
          <div className="flex flex-wrap items-center justify-between gap-3">
            <Button
              variant="secondary"
              size="sm"
              disabled={loading || page <= 0}
              onClick={() => void load(filter, page - 1)}
            >
              Previous
            </Button>
            <span className="text-sm text-text-muted tabular-nums" aria-live="polite">
              Page {page + 1} of {totalPages}
            </span>
            <Button
              variant="secondary"
              size="sm"
              disabled={loading || page >= totalPages - 1}
              onClick={() => void load(filter, page + 1)}
            >
              Next
            </Button>
          </div>
        ) : null}
      </section>

      <ConfirmDialog
        open={pending !== null}
        title={pending?.status === 'DISMISSED' ? 'Dismiss this flag?' : 'Mark this flag as reviewed?'}
        description={
          pending?.status === 'DISMISSED'
            ? 'Dismissing records that the transaction needs no further action.'
            : 'Reviewing records that someone has looked into the transaction.'
        }
        confirmLabel={pending?.status === 'DISMISSED' ? 'Dismiss flag' : 'Mark reviewed'}
        busyLabel={pending?.status === 'DISMISSED' ? 'Dismissing…' : 'Saving…'}
        busy={deciding}
        onConfirm={() => void decide()}
        onCancel={() => setPending(null)}
      >
        <p className="text-sm text-text-muted">
          This decision cannot be changed. The flag stays in the audit trail either way.
        </p>
      </ConfirmDialog>

      <Toast
        open={toast !== null}
        tone={toast?.tone}
        message={toast?.message ?? ''}
        onDismiss={() => setToast(null)}
      />
    </div>
  )
}
