import { useCallback, useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { Alert } from '../components/Alert'
import { AmountActionForm } from '../components/AmountActionForm'
import { Button } from '../components/Button'
import { IconArrowLeft } from '../components/Icon'
import { Skeleton } from '../components/Skeleton'
import { SegmentedPanels } from '../components/SegmentedPanels'
import { TransactionTable } from '../components/TransactionTable'
import { TransferForm } from '../components/TransferForm'
import {
  accountNumber as accountNumberStyle,
  card,
  cardRaised,
  cx,
  display,
  eyebrowOnDark,
  linkOnDark,
  moneyDisplay,
} from '../components/ui'
import { bankingApi, type Account, type TransactionPage } from '../api/banking'
import { errorMessage } from '../api/errors'
import { useAuth } from '../auth/AuthContext'
import { formatPesos } from '../lib/money'

const PAGE_SIZE = 10

/**
 * One account: its balance, the three ways to move money out of or into it, and
 * its transaction history.
 *
 * An ADMIN sees the same page without the money forms: the backend refuses an
 * admin deposit, withdrawal or transfer, so offering the controls would only
 * produce a 403.
 *
 * `accountNumber` comes from the URL. React Router has already percent-decoded
 * it, so it is used as-is — decoding again would corrupt a number containing a
 * literal '%' or '+'.
 */
export function AccountPage() {
  const { accountNumber = '' } = useParams()
  const { isAdmin } = useAuth()

  const [account, setAccount] = useState<Account | null>(null)
  const [history, setHistory] = useState<TransactionPage | null>(null)
  const [page, setPage] = useState(0)
  const [loading, setLoading] = useState(true)
  const [fatal, setFatal] = useState<string | null>(null)
  const [historyBusy, setHistoryBusy] = useState(false)

  const loadAccount = useCallback(async () => {
    try {
      setAccount(await bankingApi.getAccount(accountNumber))
      setFatal(null)
    } catch (cause) {
      // 404 means "not yours" and "does not exist" alike, on purpose. Either
      // way there is nothing here to manage, and the backend message is safe to
      // show because it never names the account number.
      setFatal(errorMessage(cause))
    }
  }, [accountNumber])

  useEffect(() => {
    let active = true
    setLoading(true)

    void (async () => {
      await loadAccount()
      try {
        const first = await bankingApi.transactionHistory(accountNumber, 0, PAGE_SIZE)
        if (!active) {
          return
        }
        setHistory(first)
        // History is oldest-first, so start on the most recent page.
        setPage(Math.max(0, first.totalPages - 1))
      } catch {
        // The balance above is still usable without history; leave it empty.
      } finally {
        if (active) {
          setLoading(false)
        }
      }
    })()

    return () => {
      active = false
    }
  }, [loadAccount, accountNumber])

  // Fetches whenever the page number changes, but not on the first render —
  // the effect above already loaded the initial page.
  useEffect(() => {
    if (history === null || loading) {
      return
    }
    let active = true
    setHistoryBusy(true)
    void bankingApi
      .transactionHistory(accountNumber, page, PAGE_SIZE)
      .then((result) => {
        if (active) {
          setHistory(result)
        }
      })
      .catch(() => {
        if (active) {
          setHistory(null)
        }
      })
      .finally(() => {
        if (active) {
          setHistoryBusy(false)
        }
      })
    return () => {
      active = false
    }
    // `history` is intentionally not a dependency: reading it here is only a
    // "has the first load happened" test, and depending on it would loop.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [page, accountNumber, loading])

  /**
   * After money moves: refresh the balance and jump to the newest page of the
   * history, so the transaction the user just made is the one they see.
   */
  const afterMoneyMoved = useCallback(async () => {
    await loadAccount()
    try {
      const first = await bankingApi.transactionHistory(accountNumber, 0, PAGE_SIZE)
      const lastPage = Math.max(0, first.totalPages - 1)
      setHistory(lastPage === 0 ? first : await bankingApi.transactionHistory(accountNumber, lastPage, PAGE_SIZE))
      setPage(lastPage)
    } catch {
      // Keep the old history rather than blanking the page.
    }
  }, [loadAccount, accountNumber])

  if (loading) {
    return (
      <div>
        <section className="hero-gradient overflow-hidden rounded-card px-5 pb-20 pt-6 shadow-elevate-3 sm:px-8 sm:pb-24 sm:pt-8">
          <p className={eyebrowOnDark}>Loading account {accountNumber}…</p>
          <Skeleton className="mt-5 h-8 w-48 bg-on-dark/20" />
          <Skeleton className="mt-8 h-14 w-64 bg-on-dark/20" />
        </section>
        <section
          aria-label="Loading transaction history"
          className={cx(cardRaised, 'relative z-10 mx-3 -mt-12 rounded-card p-4 sm:mx-8 sm:-mt-16 sm:p-6')}
        >
          <Skeleton className="h-7 w-48" />
          <div className="mt-6 space-y-4">
            {[0, 1, 2].map((row) => (
              <div key={row} className="flex items-center gap-3">
                <Skeleton className="h-10 w-10 shrink-0 rounded-full" />
                <Skeleton className="h-10 flex-1" />
                <Skeleton className="h-6 w-20" />
              </div>
            ))}
          </div>
        </section>
      </div>
    )
  }

  if (fatal !== null || account === null) {
    return (
      <div className="space-y-4">
        <Link to="/" className="text-sm font-medium text-action hover:text-action-strong">
          &larr; {isAdmin ? 'Back to the overview' : 'Back to your accounts'}
        </Link>
        <Alert tone="error" title="This account is not available">
          {fatal ?? 'The account could not be loaded.'}
        </Alert>
      </div>
    )
  }

  const totalPages = history?.totalPages ?? 0
  const currentPage = history?.page ?? page

  return (
    <div>
      <section className="hero-gradient overflow-hidden rounded-card px-5 pb-20 pt-5 shadow-elevate-3 sm:px-8 sm:pb-24 sm:pt-8">
        <Link to="/" className={cx(linkOnDark, 'inline-flex min-h-11 items-center gap-2 text-sm')}>
          <IconArrowLeft className="h-4 w-4" />
          {isAdmin ? 'Back to the overview' : 'Back to your accounts'}
        </Link>

        <div className="mt-8 flex flex-wrap items-end justify-between gap-8">
          <div>
            <p className={eyebrowOnDark}>Account</p>
            <h1 className={cx('mt-2 text-display-md text-on-dark sm:text-display-lg', display)}>
              {account.accountName}
            </h1>
            <p className={cx(accountNumberStyle, 'mt-3 font-mono text-sm text-on-dark-muted')}>
              {account.accountNumber}
            </p>
          </div>
          <div className="sm:text-right">
            <p className={eyebrowOnDark}>Balance</p>
            <p className={cx(moneyDisplay, 'mt-2 text-display-lg font-bold text-on-dark')}>
              {formatPesos(account.balance)}
            </p>
          </div>
        </div>
      </section>

      <section
        className={cx(
          cardRaised,
          'relative z-10 mx-3 -mt-12 rounded-card p-4 sm:mx-8 sm:-mt-16 sm:p-6',
        )}
        aria-labelledby="history-heading"
      >
        <div className="mb-6">
          <h2 id="history-heading" className="text-display-sm font-bold text-text sm:text-display-md">
            Transaction history
          </h2>
          <p className="mt-1 text-sm text-text-muted">
            {history
              ? `${history.totalElements} transaction${history.totalElements === 1 ? '' : 's'}, newest first`
              : 'History is unavailable right now.'}
          </p>
        </div>

        {historyBusy ? (
          <div role="status" className="space-y-4 py-2">
            <span className="sr-only">Loading transaction history…</span>
            {[0, 1, 2].map((row) => (
              <div key={row} className="flex items-center gap-3">
                <Skeleton className="h-10 w-10 shrink-0 rounded-full" />
                <Skeleton className="h-10 flex-1" />
                <Skeleton className="h-6 w-20" />
              </div>
            ))}
          </div>
        ) : history ? (
          <TransactionTable transactions={history.transactions} />
        ) : null}

        {totalPages > 1 ? (
          <div className="mt-6 flex flex-wrap items-center justify-between gap-3 border-t border-line pt-4">
            <Button
              variant="secondary"
              size="sm"
              disabled={historyBusy || currentPage <= 0}
              onClick={() => setPage(currentPage - 1)}
            >
              Older
            </Button>
            <span className="text-sm text-text-muted tabular-nums" aria-live="polite">
              Page {currentPage + 1} of {totalPages}
            </span>
            <Button
              variant="secondary"
              size="sm"
              disabled={historyBusy || currentPage >= totalPages - 1}
              onClick={() => setPage(currentPage + 1)}
            >
              Newer
            </Button>
          </div>
        ) : null}
      </section>

      <div className="mt-8">
        {isAdmin ? (
          <section className={card} aria-labelledby="admin-note-heading">
            <h2 id="admin-note-heading" className="mb-2 text-lg font-semibold text-text">
              Read-only view
            </h2>
            <p className="text-sm text-text-muted">
              Administrators can see every account but cannot deposit, withdraw or transfer. Only
              the account holder moves money.
            </p>
          </section>
        ) : (
          <section className={cx(card, 'max-w-2xl')} aria-labelledby="move-money-heading">
            <h2 id="move-money-heading" className="text-display-sm font-bold text-text">
              Move money
            </h2>
            <p className="mt-1 text-sm text-text-muted">
              Every action asks you to confirm the amount before the money moves.
            </p>
            <div className="mt-5">
              <SegmentedPanels
                label="Money action"
                segments={[
                  {
                    id: 'deposit',
                    label: 'Deposit',
                    content: (
                      <AmountActionForm
                        kind="deposit"
                        accountNumber={account.accountNumber}
                        availableBalance={account.balance}
                        fieldLabel="Amount to deposit"
                        submitLabel="Deposit"
                        busyLabel="Depositing…"
                        run={(amount, key) => bankingApi.deposit(account.accountNumber, amount, key)}
                        onDone={() => void afterMoneyMoved()}
                      />
                    ),
                  },
                  {
                    id: 'withdraw',
                    label: 'Withdraw',
                    content: (
                      <AmountActionForm
                        kind="withdraw"
                        accountNumber={account.accountNumber}
                        availableBalance={account.balance}
                        fieldLabel="Amount to withdraw"
                        submitLabel="Withdraw"
                        busyLabel="Withdrawing…"
                        run={(amount, key) =>
                          bankingApi.withdraw(account.accountNumber, amount, key)
                        }
                        onDone={() => void afterMoneyMoved()}
                      />
                    ),
                  },
                  {
                    id: 'transfer',
                    label: 'Transfer',
                    content: (
                      <TransferForm
                        sourceAccountNumber={account.accountNumber}
                        availableBalance={account.balance}
                        run={(input, key) => bankingApi.transfer(input, key)}
                        onDone={() => void afterMoneyMoved()}
                      />
                    ),
                  },
                ]}
              />
            </div>
          </section>
        )}
      </div>
    </div>
  )
}
