import { useCallback, useEffect, useRef, useState, type FormEvent } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { Alert } from '../components/Alert'
import { Field } from '../components/Field'
import { OpenAccountForm } from '../components/OpenAccountForm'
import { Spinner } from '../components/Spinner'
import { buttonPrimary, buttonSecondary, card, cx } from '../components/ui'
import { bankingApi, type Account } from '../api/banking'
import { errorMessage } from '../api/errors'
import { useAuth } from '../auth/AuthContext'
import { formatPesos, totalAmounts } from '../lib/money'
import { MAX_ACCOUNT_NUMBER_LENGTH, accountNumberError } from '../lib/validation'

/**
 * The largest page the backend will return (it answers 422 above 100). Asking
 * for the maximum keeps every account a caller owns on one screen, so the
 * dashboard needs no pager for the handful of accounts a person actually has.
 */
const LIST_PAGE_SIZE = 100

export function DashboardPage() {
  const { isAdmin } = useAuth()
  const navigate = useNavigate()

  const [accounts, setAccounts] = useState<Account[]>([])
  const [totalElements, setTotalElements] = useState(0)
  const [totalPages, setTotalPages] = useState(1)
  const [loading, setLoading] = useState(true)
  const [failure, setFailure] = useState<string | null>(null)
  const [opening, setOpening] = useState(false)
  const [notice, setNotice] = useState<string | null>(null)

  const [lookup, setLookup] = useState('')
  const [lookupBusy, setLookupBusy] = useState(false)
  const [lookupError, setLookupError] = useState<string | null>(null)

  /**
   * Guards against an out-of-order response overwriting a newer one: two
   * refreshes in flight can finish in either order.
   */
  const requestSequence = useRef(0)

  const loadAccounts = useCallback(async () => {
    const sequence = ++requestSequence.current
    setLoading(true)
    setFailure(null)
    try {
      const page = await bankingApi.accounts(0, LIST_PAGE_SIZE)
      if (sequence !== requestSequence.current) {
        return
      }
      setAccounts(page.accounts)
      setTotalElements(page.totalElements)
      setTotalPages(page.totalPages)
    } catch (cause) {
      if (sequence !== requestSequence.current) {
        return
      }
      setFailure(errorMessage(cause))
    } finally {
      if (sequence === requestSequence.current) {
        setLoading(false)
      }
    }
  }, [])

  useEffect(() => {
    void loadAccounts()
  }, [loadAccounts])

  const total = totalAmounts(accounts.map((account) => account.balance))

  function onCreated(account: Account) {
    setOpening(false)
    setNotice(`Account ${account.accountNumber} opened with ${formatPesos(account.balance)}.`)
    void loadAccounts()
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

  return (
    <div className="space-y-8">
      <div className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <h1 className="text-2xl font-semibold tracking-tight text-slate-900">
            {isAdmin ? 'All accounts' : 'Your accounts'}
          </h1>
          <p className="mt-1 text-sm text-slate-600">
            Balances are read live from the backend on every visit.
          </p>
        </div>
        <div className="flex gap-2">
          <button
            type="button"
            className={buttonSecondary}
            onClick={() => {
              setNotice(null)
              void loadAccounts()
            }}
          >
            Refresh
          </button>
          <button
            type="button"
            className={buttonPrimary}
            onClick={() => {
              setNotice(null)
              setOpening((open) => !open)
            }}
            aria-expanded={opening}
          >
            {opening ? 'Cancel' : 'Open an account'}
          </button>
        </div>
      </div>

      {notice ? <Alert tone="success">{notice}</Alert> : null}

      {opening ? (
        <section className={card} aria-labelledby="open-account-heading">
          <h2 id="open-account-heading" className="mb-4 text-lg font-semibold text-slate-900">
            Open a new account
          </h2>
          <OpenAccountForm onCreated={onCreated} />
        </section>
      ) : null}

      <section aria-labelledby="accounts-heading" className="space-y-3">
        <div className="flex items-baseline justify-between gap-4">
          <h2 id="accounts-heading" className="text-lg font-semibold text-slate-900">
            Accounts
          </h2>
          {accounts.length > 0 ? (
            <p className="text-sm text-slate-600">
              Total balance{' '}
              <span className="tabular-money text-base font-semibold text-slate-900">
                {formatPesos(total)}
              </span>
            </p>
          ) : null}
        </div>

        {loading ? (
          <p className="flex items-center gap-2 py-8 text-sm text-slate-500">
            <Spinner className="text-slate-400" /> Loading accounts…
          </p>
        ) : failure !== null ? (
          <Alert tone="error" title="The account list could not be loaded">
            <p>{failure}</p>
            <button type="button" className={cx(buttonSecondary, 'mt-3')} onClick={() => void loadAccounts()}>
              Try again
            </button>
          </Alert>
        ) : accounts.length === 0 ? (
          <div className={cx(card, 'py-10 text-center')}>
            <p className="text-sm text-slate-600">
              {isAdmin
                ? 'No accounts in the system yet.'
                : 'No accounts yet. Open one to get started.'}
            </p>
          </div>
        ) : (
          <>
            <ul className="space-y-3">
              {accounts.map((account) => (
                <li
                  key={account.accountNumber}
                  className={cx(card, 'flex flex-wrap items-center gap-4 p-4')}
                >
                  <div className="min-w-0 flex-1">
                    <p className="truncate font-medium text-slate-900">{account.accountName}</p>
                    <p className="tabular-money font-mono text-sm text-slate-500">
                      {account.accountNumber}
                    </p>
                  </div>
                  <p className="tabular-money text-xl font-semibold text-slate-900">
                    {formatPesos(account.balance)}
                  </p>
                  <Link
                    to={`/accounts/${encodeURIComponent(account.accountNumber)}`}
                    className={buttonSecondary}
                  >
                    Manage
                  </Link>
                </li>
              ))}
            </ul>
            {totalPages > 1 ? (
              <p className="text-xs text-slate-500">
                Showing the first {LIST_PAGE_SIZE} of {totalElements} accounts.
              </p>
            ) : null}
          </>
        )}
      </section>

      {isAdmin ? (
        <section className={card} aria-labelledby="lookup-heading">
          <h2 id="lookup-heading" className="mb-1 text-lg font-semibold text-slate-900">
            Look up an account
          </h2>
          <p className="mb-4 text-sm text-slate-600">
            Jump to any account by number. As an administrator every account in the system is open
            to you.
          </p>
          <form onSubmit={onLookup} noValidate className="flex flex-wrap items-start gap-3">
            <div className="min-w-56 flex-1">
              <Field
                label="Account number"
                name="lookup-account-number"
                value={lookup}
                placeholder="200001"
                maxLength={MAX_ACCOUNT_NUMBER_LENGTH}
                onChange={(event) => setLookup(event.target.value)}
                invalid={lookupError !== null}
                hint={lookupError ?? undefined}
              />
            </div>
            <button
              type="submit"
              className={cx(buttonPrimary, 'mt-6')}
              disabled={lookupBusy || lookup.trim() === ''}
            >
              {lookupBusy ? <Spinner /> : null}
              {lookupBusy ? 'Checking…' : 'Open'}
            </button>
          </form>
        </section>
      ) : null}
    </div>
  )
}
