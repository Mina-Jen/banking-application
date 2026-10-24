import { useCallback, useEffect, useRef, useState, type FormEvent } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { Alert } from '../components/Alert'
import { Button } from '../components/Button'
import { EmptyState } from '../components/EmptyState'
import {
  IconAlert,
  IconCheck,
  IconChevronRight,
  IconInbox,
  IconRefresh,
} from '../components/Icon'
import { Input } from '../components/Input'
import { Skeleton } from '../components/Skeleton'
import { Toast } from '../components/Toast'
import {
  buttonClasses,
  card,
  cx,
  display,
  eyebrow,
  eyebrowOnDark,
  money,
  tableCell,
  tableHead,
} from '../components/ui'
import {
  bankingApi,
  type Account,
  type AnomalyFlagPage,
  type ReconciliationReport,
} from '../api/banking'
import { errorMessage } from '../api/errors'
import { formatDateTime } from '../lib/dates'
import { formatPesos } from '../lib/money'
import { MAX_ACCOUNT_NUMBER_LENGTH, accountNumberError } from '../lib/validation'

const ACCOUNTS_PAGE_SIZE = 10

type ToastState = { tone: 'success' | 'error'; message: string }

/** A one-line KPI in the hero. `value` is null while the figure is loading. */
function Kpi({ label, value }: { label: string; value: string | null }) {
  return (
    <div>
      <dt className={eyebrowOnDark}>{label}</dt>
      <dd className="mt-2">
        {value === null ? (
          <Skeleton className="h-7 w-24 bg-on-dark/20" />
        ) : (
          <span className={cx('text-2xl font-bold text-on-dark', money)}>{value}</span>
        )}
      </dd>
    </div>
  )
}

function LedgerStatus({ report }: { report: ReconciliationReport | null }) {
  if (report === null) {
    return null
  }
  const balanced = report.reconciled
  return (
    <span
      className={cx(
        'inline-flex items-center gap-1.5 rounded-full px-3 py-1 text-xs font-semibold ring-1',
        balanced
          ? 'bg-success-soft text-success-strong ring-success/20'
          : 'bg-danger-soft text-danger-strong ring-danger/20',
      )}
    >
      {balanced ? <IconCheck className="h-3.5 w-3.5" /> : <IconAlert className="h-3.5 w-3.5" />}
      {balanced ? 'Balanced' : 'Discrepancies found'}
    </span>
  )
}

/**
 * The ADMIN landing page.
 *
 * It is a read-only operations view on purpose: the bank's own staff can inspect
 * every account, run the ledger check and work the anomaly queue, but the money
 * forms live only on the account holder's side. The backend enforces the same
 * split with a 403, so hiding the controls here is presentation, not security.
 */
export function AdminDashboardPage() {
  const navigate = useNavigate()

  const [accounts, setAccounts] = useState<Account[]>([])
  const [page, setPage] = useState(0)
  const [totalElements, setTotalElements] = useState(0)
  const [totalPages, setTotalPages] = useState(1)
  const [accountsLoading, setAccountsLoading] = useState(true)
  const [accountsFailure, setAccountsFailure] = useState<string | null>(null)

  const [report, setReport] = useState<ReconciliationReport | null>(null)
  const [reportLoading, setReportLoading] = useState(true)
  const [reportFailure, setReportFailure] = useState<string | null>(null)
  const [checking, setChecking] = useState(false)

  const [openFlags, setOpenFlags] = useState<number | null>(null)
  const [toast, setToast] = useState<ToastState | null>(null)

  const [lookup, setLookup] = useState('')
  const [lookupBusy, setLookupBusy] = useState(false)
  const [lookupError, setLookupError] = useState<string | null>(null)

  /** Guards against an older accounts response replacing a newer one. */
  const accountsSequence = useRef(0)

  const loadAccounts = useCallback(async (targetPage: number) => {
    const sequence = ++accountsSequence.current
    setAccountsLoading(true)
    setAccountsFailure(null)
    try {
      const result = await bankingApi.accounts(targetPage, ACCOUNTS_PAGE_SIZE)
      if (sequence !== accountsSequence.current) {
        return
      }
      setAccounts(result.accounts)
      setPage(result.page)
      setTotalElements(result.totalElements)
      setTotalPages(Math.max(1, result.totalPages))
    } catch (cause) {
      if (sequence === accountsSequence.current) {
        setAccountsFailure(errorMessage(cause))
      }
    } finally {
      if (sequence === accountsSequence.current) {
        setAccountsLoading(false)
      }
    }
  }, [])

  const loadReport = useCallback(async () => {
    setReportLoading(true)
    setReportFailure(null)
    try {
      setReport(await bankingApi.reconciliation())
    } catch (cause) {
      setReportFailure(errorMessage(cause))
    } finally {
      setReportLoading(false)
    }
  }, [])

  const loadOpenFlagCount = useCallback(async () => {
    try {
      const open: AnomalyFlagPage = await bankingApi.anomalyFlags('OPEN', 0, 1)
      setOpenFlags(open.totalElements)
    } catch {
      // The count is a convenience; the queue page reports its own errors.
      setOpenFlags(null)
    }
  }, [])

  useEffect(() => {
    void loadAccounts(0)
    void loadReport()
    void loadOpenFlagCount()
  }, [loadAccounts, loadReport, loadOpenFlagCount])

  function refreshAll() {
    void loadAccounts(page)
    void loadReport()
    void loadOpenFlagCount()
  }

  async function runCheck() {
    setChecking(true)
    try {
      const result = await bankingApi.reconciliation()
      setReport(result)
      setReportFailure(null)
      setToast({
        tone: 'success',
        message: result.reconciled
          ? `Ledger check finished: all ${result.checkedAccounts} accounts balance.`
          : `Ledger check finished: ${result.discrepancies.length} account${result.discrepancies.length === 1 ? '' : 's'} do not balance.`,
      })
    } catch (cause) {
      setToast({ tone: 'error', message: errorMessage(cause) })
    } finally {
      setChecking(false)
    }
  }

  async function onLookup(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const accountNumber = lookup.trim()
    if (accountNumberError(accountNumber) !== null || lookupBusy) {
      return
    }

    setLookupBusy(true)
    setLookupError(null)
    try {
      await bankingApi.getAccount(accountNumber)
      navigate(`/accounts/${encodeURIComponent(accountNumber)}`)
    } catch (cause) {
      setLookupError(errorMessage(cause))
      setLookup('')
    } finally {
      setLookupBusy(false)
    }
  }

  const discrepancies = report?.discrepancies ?? []
  const accountsLoadingFirstTime = accountsLoading && accounts.length === 0

  return (
    <div className="space-y-8">
      <section className="hero-gradient overflow-hidden rounded-2xl p-5 shadow-lg sm:p-8">
        <div className="flex flex-wrap items-start justify-between gap-6">
          <div>
            <p className={eyebrowOnDark}>Bank operations</p>
            <h1 className={cx('mt-2 text-display-md text-on-dark sm:text-display-lg', display)}>
              System overview
            </h1>
            <p className="mt-3 max-w-xl text-sm text-on-dark-muted">
              Every account, the ledger check and the review queue. Money movement stays with the
              account holder.
            </p>
          </div>
          <div className="flex flex-wrap gap-2">
            <Button variant="onDarkOutline" size="sm" onClick={refreshAll} disabled={accountsLoading}>
              <IconRefresh className="h-4 w-4" />
              Refresh
            </Button>
            <Link to="/review" className={buttonClasses('onDark', 'sm')}>
              Review queue
              {openFlags !== null && openFlags > 0 ? (
                <span className="rounded-full bg-ink-900/10 px-2 py-0.5 text-xs tabular-nums">
                  {openFlags}
                </span>
              ) : null}
            </Link>
          </div>
        </div>

        <dl className="mt-10 grid gap-6 border-t border-on-dark/15 pt-6 sm:grid-cols-3">
          <Kpi
            label="Accounts monitored"
            value={accountsLoadingFirstTime ? null : String(totalElements)}
          />
          <Kpi
            label="Funds on deposit"
            value={reportLoading && report === null ? null : formatPesos(report?.storedTotal ?? '0.00')}
          />
          <Kpi label="Open alerts" value={openFlags === null ? null : String(openFlags)} />
        </dl>
      </section>

      <div className="grid gap-6 lg:grid-cols-3">
        <section className={cx(card, 'lg:col-span-2')} aria-labelledby="ledger-heading">
          <div className="flex flex-wrap items-start justify-between gap-3">
            <div>
              <h2 id="ledger-heading" className="text-lg font-semibold text-text">
                Ledger integrity
              </h2>
              <p className="mt-1 text-sm text-text-muted">
                Every stored balance compared with the sum of its ledger rows.
              </p>
            </div>
            <LedgerStatus report={report} />
          </div>

          {reportFailure !== null ? (
            <Alert tone="error" title="The ledger check could not run">
              <p>{reportFailure}</p>
              <Button variant="secondary" size="sm" className="mt-3" onClick={() => void loadReport()}>
                Try again
              </Button>
            </Alert>
          ) : reportLoading && report === null ? (
            <div className="mt-6 grid gap-4 sm:grid-cols-3">
              <Skeleton className="h-16" />
              <Skeleton className="h-16" />
              <Skeleton className="h-16" />
            </div>
          ) : report !== null ? (
            <>
              <dl className="mt-6 grid gap-4 sm:grid-cols-3">
                <div>
                  <dt className="text-xs text-text-subtle">Accounts checked</dt>
                  <dd className={cx('mt-1 text-lg font-semibold text-text', money)}>
                    {report.checkedAccounts}
                  </dd>
                </div>
                <div>
                  <dt className="text-xs text-text-subtle">Ledger total</dt>
                  <dd className={cx('mt-1 text-lg font-semibold text-text', money)}>
                    {formatPesos(report.ledgerTotal)}
                  </dd>
                </div>
                <div>
                  <dt className="text-xs text-text-subtle">Stored total</dt>
                  <dd className={cx('mt-1 text-lg font-semibold text-text', money)}>
                    {formatPesos(report.storedTotal)}
                  </dd>
                </div>
              </dl>

              {report.reconciled ? (
                <p className="mt-6 flex items-center gap-2 rounded-lg bg-success-soft px-4 py-3 text-sm text-success-strong ring-1 ring-success/20">
                  <IconCheck className="h-4 w-4 shrink-0" />
                  Stored balances match the ledger.
                </p>
              ) : (
                <div className="mt-6 overflow-x-auto">
                  <table className="w-full min-w-[34rem] border-collapse text-left">
                    <caption className="sr-only">
                      Accounts whose stored balance does not match the ledger
                    </caption>
                    <thead>
                      <tr className="border-b border-line">
                        <th scope="col" className={tableHead}>
                          Account
                        </th>
                        <th scope="col" className={tableHead}>
                          Ledger
                        </th>
                        <th scope="col" className={tableHead}>
                          Stored
                        </th>
                        <th scope="col" className={tableHead}>
                          Difference
                        </th>
                      </tr>
                    </thead>
                    <tbody>
                      {discrepancies.map((item) => (
                        <tr key={item.accountNumber} className="border-b border-line last:border-0">
                          <td className={tableCell}>
                            <Link
                              to={`/accounts/${encodeURIComponent(item.accountNumber)}`}
                              className="font-semibold text-action hover:text-action-strong"
                            >
                              {item.accountNumber}
                            </Link>
                            <span className="block text-xs text-text-subtle">{item.accountName}</span>
                          </td>
                          <td className={cx(tableCell, money)}>{formatPesos(item.ledgerBalance)}</td>
                          <td className={cx(tableCell, money)}>{formatPesos(item.storedBalance)}</td>
                          <td className={cx(tableCell, money, 'font-semibold text-danger-strong')}>
                            {formatPesos(item.difference)}
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                  {report.discrepanciesTruncated ? (
                    <p className="mt-2 text-xs text-text-subtle">
                      More accounts mismatched than are listed here.
                    </p>
                  ) : null}
                </div>
              )}
            </>
          ) : null}

          <div className="mt-6 flex flex-wrap items-center justify-between gap-3 border-t border-line pt-4">
            <p className="text-xs text-text-subtle">
              {report !== null ? `Last checked ${formatDateTime(report.checkedAt)}` : 'Not checked yet'}
            </p>
            <Button variant="secondary" size="sm" onClick={() => void runCheck()} loading={checking}>
              {checking ? 'Checking ledger…' : 'Re-run check'}
            </Button>
          </div>
        </section>

        <section className={card} aria-labelledby="review-heading">
          <div className="flex items-start gap-3">
            <span
              aria-hidden="true"
              className="flex h-10 w-10 shrink-0 items-center justify-center rounded-full bg-warning-soft text-warning-strong"
            >
              <IconInbox className="h-5 w-5" />
            </span>
            <div>
              <h2 id="review-heading" className="text-lg font-semibold text-text">
                Anomaly review
              </h2>
              <p className="mt-1 text-sm text-text-muted">
                {openFlags === null
                  ? 'Could not count open flags right now.'
                  : openFlags === 0
                    ? 'No flags are waiting for a decision.'
                    : `${openFlags} flag${openFlags === 1 ? '' : 's'} waiting for a decision.`}
              </p>
            </div>
          </div>
          <Link to="/review" className={cx(buttonClasses('secondary', 'sm'), 'mt-5')}>
            Open review queue
          </Link>
        </section>
      </div>

      <section aria-labelledby="accounts-heading" className="space-y-4">
        <div className="flex flex-wrap items-end justify-between gap-4">
          <div>
            <p className={eyebrow}>Customer accounts</p>
            <h2 id="accounts-heading" className="mt-1 text-display-md font-bold text-text">
              All accounts
            </h2>
            <p className="mt-1 text-sm text-text-muted">
              Balances are read live on every visit.
            </p>
          </div>
          <form onSubmit={onLookup} noValidate className="flex items-start gap-2">
            <div className="w-52">
              <Input
                label="Open an account by number"
                name="lookup-account-number"
                value={lookup}
                placeholder="200001"
                maxLength={MAX_ACCOUNT_NUMBER_LENGTH}
                onChange={(event) => setLookup(event.target.value)}
                invalid={lookupError !== null}
                hint={lookupError ?? 'Any account in the system.'}
              />
            </div>
            <Button
              type="submit"
              className="mt-6"
              loading={lookupBusy}
              disabled={lookup.trim() === ''}
            >
              {lookupBusy ? 'Checking…' : 'Open'}
            </Button>
          </form>
        </div>

        <div className={cx(card, 'p-0')}>
          {accountsLoading && accounts.length === 0 ? (
            <div className="space-y-4 p-6" role="status">
              <span className="sr-only">Loading accounts…</span>
              {[0, 1, 2].map((row) => (
                <div key={row} className="flex items-center gap-3">
                  <Skeleton className="h-10 flex-1" />
                  <Skeleton className="h-6 w-24" />
                </div>
              ))}
            </div>
          ) : accountsFailure !== null ? (
            <div className="p-6">
              <Alert tone="error" title="The account list could not be loaded">
                <p>{accountsFailure}</p>
                <Button variant="secondary" size="sm" className="mt-3" onClick={() => void loadAccounts(page)}>
                  Try again
                </Button>
              </Alert>
            </div>
          ) : accounts.length === 0 ? (
            <div className="p-6">
              <EmptyState
                title="No accounts in the system yet"
                description="Accounts opened by customers appear here."
                icon={<IconInbox className="h-6 w-6" />}
              />
            </div>
          ) : (
            <ul className="divide-y divide-line">
              {accounts.map((account) => (
                <li
                  key={account.accountNumber}
                  className="flex flex-wrap items-center gap-3 px-4 py-3 sm:px-6"
                >
                  <div className="min-w-0 flex-1 basis-44">
                    <p className={cx('text-sm font-semibold text-text', money)}>
                      {account.accountNumber}
                    </p>
                    <p className="truncate text-xs text-text-subtle">{account.accountName}</p>
                  </div>
                  <div className="ml-auto flex items-center gap-3">
                    <p className={cx('text-sm font-semibold text-text', money)}>
                      {formatPesos(account.balance)}
                    </p>
                    <Link
                      to={`/accounts/${encodeURIComponent(account.accountNumber)}`}
                      className={buttonClasses('secondary', 'sm')}
                    >
                      View
                      <IconChevronRight className="h-4 w-4" />
                    </Link>
                  </div>
                </li>
              ))}
            </ul>
          )}
        </div>

        {totalPages > 1 ? (
          <div className="flex flex-wrap items-center justify-between gap-3">
            <Button
              variant="secondary"
              size="sm"
              disabled={accountsLoading || page <= 0}
              onClick={() => void loadAccounts(page - 1)}
            >
              Previous
            </Button>
            <span className="text-sm text-text-muted tabular-nums" aria-live="polite">
              Page {page + 1} of {totalPages}
            </span>
            <Button
              variant="secondary"
              size="sm"
              disabled={accountsLoading || page >= totalPages - 1}
              onClick={() => void loadAccounts(page + 1)}
            >
              Next
            </Button>
          </div>
        ) : null}
      </section>

      <Toast
        open={toast !== null}
        tone={toast?.tone}
        message={toast?.message ?? ''}
        onDismiss={() => setToast(null)}
      />
    </div>
  )
}
