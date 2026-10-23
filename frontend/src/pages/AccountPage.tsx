import { useCallback, useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { Alert } from '../components/Alert'
import { AmountActionForm } from '../components/AmountActionForm'
import { Spinner } from '../components/Spinner'
import { TransactionTable } from '../components/TransactionTable'
import { TransferForm } from '../components/TransferForm'
import { buttonSecondary, card, cx } from '../components/ui'
import { bankingApi, type Account, type TransactionPage } from '../api/banking'
import { errorMessage } from '../api/errors'
import { formatPesos } from '../lib/money'

const PAGE_SIZE = 10

/**
 * One account: its balance, the three ways to move money out of or into it, and
 * its transaction history.
 *
 * `accountNumber` comes from the URL. React Router has already percent-decoded
 * it, so it is used as-is — decoding again would corrupt a number containing a
 * literal '%' or '+'.
 */
export function AccountPage() {
  const { accountNumber = '' } = useParams()

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
      <p className="flex items-center gap-2 py-16 text-sm text-slate-500">
        <Spinner className="text-slate-400" /> Loading account {accountNumber}…
      </p>
    )
  }

  if (fatal !== null || account === null) {
    return (
      <div className="space-y-4">
        <Link to="/" className="text-sm font-medium text-emerald-700 hover:text-emerald-800">
          &larr; Back to your accounts
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
    <div className="space-y-8">
      <Link to="/" className="text-sm font-medium text-emerald-700 hover:text-emerald-800">
        &larr; Back to your accounts
      </Link>

      <div className={cx(card, 'flex flex-wrap items-end justify-between gap-6')}>
        <div>
          <p className="text-sm text-slate-500">Account</p>
          <h1 className="text-2xl font-semibold tracking-tight text-slate-900">
            {account.accountName}
          </h1>
          <p className="tabular-money mt-1 font-mono text-sm text-slate-500">{account.accountNumber}</p>
        </div>
        <div className="text-right">
          <p className="text-sm text-slate-500">Balance</p>
          <p className="tabular-money text-3xl font-semibold text-slate-900">
            {formatPesos(account.balance)}
          </p>
        </div>
      </div>

      <div className="grid gap-6 lg:grid-cols-3">
        <section className={card} aria-labelledby="deposit-heading">
          <h2 id="deposit-heading" className="mb-4 text-lg font-semibold text-slate-900">
            Deposit
          </h2>
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
        </section>

        <section className={card} aria-labelledby="withdraw-heading">
          <h2 id="withdraw-heading" className="mb-4 text-lg font-semibold text-slate-900">
            Withdraw
          </h2>
          <AmountActionForm
            kind="withdraw"
            accountNumber={account.accountNumber}
            availableBalance={account.balance}
            fieldLabel="Amount to withdraw"
            submitLabel="Withdraw"
            busyLabel="Withdrawing…"
            run={(amount, key) => bankingApi.withdraw(account.accountNumber, amount, key)}
            onDone={() => void afterMoneyMoved()}
          />
        </section>

        <section className={card} aria-labelledby="transfer-heading">
          <h2 id="transfer-heading" className="mb-4 text-lg font-semibold text-slate-900">
            Transfer
          </h2>
          <TransferForm
            sourceAccountNumber={account.accountNumber}
            availableBalance={account.balance}
            run={(input, key) => bankingApi.transfer(input, key)}
            onDone={() => void afterMoneyMoved()}
          />
        </section>
      </div>

      <section className={card} aria-labelledby="history-heading">
        <div className="mb-4 flex flex-wrap items-center justify-between gap-3">
          <div>
            <h2 id="history-heading" className="text-lg font-semibold text-slate-900">
              Transaction history
            </h2>
            <p className="text-sm text-slate-600">
              {history
                ? `${history.totalElements} transaction${history.totalElements === 1 ? '' : 's'}, oldest first`
                : 'History is unavailable right now.'}
            </p>
          </div>

          {totalPages > 1 ? (
            <div className="flex items-center gap-2">
              <button
                type="button"
                className={buttonSecondary}
                disabled={historyBusy || currentPage <= 0}
                onClick={() => setPage(currentPage - 1)}
              >
                Previous
              </button>
              <span className="tabular-money text-sm text-slate-600" aria-live="polite">
                Page {currentPage + 1} of {totalPages}
              </span>
              <button
                type="button"
                className={buttonSecondary}
                disabled={historyBusy || currentPage >= totalPages - 1}
                onClick={() => setPage(currentPage + 1)}
              >
                Next
              </button>
            </div>
          ) : null}
        </div>

        {historyBusy ? (
          <p className="flex items-center gap-2 py-8 text-sm text-slate-500">
            <Spinner className="text-slate-400" /> Loading…
          </p>
        ) : history ? (
          <TransactionTable transactions={history.transactions} />
        ) : null}
      </section>
    </div>
  )
}
