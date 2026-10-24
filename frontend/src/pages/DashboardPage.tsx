import { useCallback, useEffect, useRef, useState } from 'react'
import { AccountActionSheet, type AccountAction } from '../components/AccountActionSheet'
import { Alert } from '../components/Alert'
import { Button } from '../components/Button'
import { EmptyState } from '../components/EmptyState'
import { GradientAccountCard } from '../components/GradientAccountCard'
import { IconCard, IconDeposit, IconRefresh, IconTransfer, IconWithdraw } from '../components/Icon'
import { OpenAccountForm } from '../components/OpenAccountForm'
import { Sheet } from '../components/Sheet'
import { Skeleton } from '../components/Skeleton'
import { Toast } from '../components/Toast'
import { card, cx, display, eyebrowOnDark, moneyDisplay, railAction } from '../components/ui'
import { bankingApi, type Account } from '../api/banking'
import type { Transaction } from '../api/types'
import { errorMessage } from '../api/errors'
import { formatPesos, totalAmounts } from '../lib/money'

/** The backend rejects list sizes above 100. */
const LIST_PAGE_SIZE = 100

type ToastState = { tone: 'success' | 'error'; message: string }

export function DashboardPage() {
  const [accounts, setAccounts] = useState<Account[]>([])
  const [totalElements, setTotalElements] = useState(0)
  const [totalPages, setTotalPages] = useState(1)
  const [loading, setLoading] = useState(true)
  const [failure, setFailure] = useState<string | null>(null)
  const [opening, setOpening] = useState(false)
  const [action, setAction] = useState<AccountAction | null>(null)
  const [toast, setToast] = useState<ToastState | null>(null)

  /** Guards against an older refresh replacing a newer response. */
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
  const actionsDisabled = loading || accounts.length === 0

  function onCreated(account: Account) {
    setOpening(false)
    setToast({
      tone: 'success',
      message: `Account ${account.accountNumber} opened with ${formatPesos(account.balance)}.`,
    })
    void loadAccounts()
  }

  function onMoneyMoved(movedBy: AccountAction, transaction: Transaction) {
    const message =
      movedBy === 'transfer'
        ? `${formatPesos(transaction.amount)} sent to ${transaction.referenceAccount ?? 'the recipient'}. Reference ${transaction.reference}. New balance ${formatPesos(transaction.balanceAfter)}.`
        : `${formatPesos(transaction.amount)} recorded. Reference ${transaction.reference}. New balance ${formatPesos(transaction.balanceAfter)}.`
    setAction(null)
    setToast({ tone: 'success', message })
    void loadAccounts()
  }

  return (
    <div className="space-y-8">
      <section className="hero-gradient overflow-hidden rounded-card p-5 shadow-elevate-3 sm:p-8">
        <div className="flex flex-wrap items-start justify-between gap-6">
          <div>
            <p className={eyebrowOnDark}>Your portfolio</p>
            <h1 className={cx('mt-2 text-display-md text-on-dark sm:text-display-lg', display)}>
              Your accounts
            </h1>
          </div>
          <div className="flex gap-2">
            <Button
              variant="onDarkOutline"
              size="sm"
              onClick={() => void loadAccounts()}
              disabled={loading}
            >
              <IconRefresh className="h-4 w-4" />
              Refresh
            </Button>
            <Button variant="onDark" size="sm" onClick={() => setOpening(true)}>
              Open an account
            </Button>
          </div>
        </div>

        <div className="mt-10 flex flex-wrap items-end justify-between gap-x-10 gap-y-6">
          <div>
            <p className={eyebrowOnDark}>Total balance</p>
            {loading ? (
              <>
                <p className="mt-3 text-sm text-on-dark-muted">Loading accounts…</p>
                <Skeleton className="mt-2 h-12 w-56 bg-on-dark/20 sm:h-16 sm:w-80" />
              </>
            ) : (
              <p className={cx(moneyDisplay, 'mt-2 text-display-xl font-bold text-on-dark')}>
                {formatPesos(total)}
              </p>
            )}
          </div>

          {/* The money rail: the three verbs, one line, on the bright end of the
              surface. Each is named by its verb alone, so the accessible name is
              the word on the pill. */}
          <div className="flex flex-wrap items-center gap-2">
            <button
              type="button"
              className={railAction}
              disabled={actionsDisabled}
              onClick={() => setAction('deposit')}
            >
              <IconDeposit className="h-4 w-4" />
              Deposit
            </button>
            <button
              type="button"
              className={railAction}
              disabled={actionsDisabled}
              onClick={() => setAction('withdraw')}
            >
              <IconWithdraw className="h-4 w-4" />
              Withdraw
            </button>
            <button
              type="button"
              className={railAction}
              disabled={actionsDisabled}
              onClick={() => setAction('transfer')}
            >
              <IconTransfer className="h-4 w-4" />
              Transfer
            </button>
          </div>
        </div>
        {!loading && accounts.length === 0 ? (
          <p className="mt-3 text-xs text-on-dark-muted">Open an account to use money actions.</p>
        ) : null}
      </section>

      <section aria-labelledby="accounts-heading" className="space-y-4">
        <div className="flex items-end justify-between gap-4">
          <div>
            <h2 id="accounts-heading" className="text-display-md font-bold text-text">
              Accounts
            </h2>
            <p className="mt-1 text-sm text-text-muted">Balances are read live on every visit.</p>
          </div>
          {!loading && accounts.length > 0 ? (
            <p className="text-sm text-text-muted">
              {accounts.length} account{accounts.length === 1 ? '' : 's'}
            </p>
          ) : null}
        </div>

        {loading ? (
          <div>
            <p className="mb-3 text-sm text-text-subtle">Loading account cards…</p>
            <div className="grid gap-4 lg:grid-cols-2">
              <Skeleton className="h-64 rounded-card" />
              <Skeleton className="h-64 rounded-card" />
            </div>
          </div>
        ) : failure !== null ? (
          <Alert tone="error" title="The account list could not be loaded">
            <p>{failure}</p>
            <Button variant="secondary" className="mt-3" onClick={() => void loadAccounts()}>
              Try again
            </Button>
          </Alert>
        ) : accounts.length === 0 ? (
          <div className={card}>
            <EmptyState
              title="Ready for your first account?"
              description="No accounts yet. Open one to get started."
              icon={<IconCard className="h-6 w-6" />}
              action={<Button onClick={() => setOpening(true)}>Open your first account</Button>}
            />
          </div>
        ) : (
          <>
            <ul className="grid gap-4 lg:grid-cols-2">
              {accounts.map((account) => (
                <li key={account.accountNumber} data-account-number={account.accountNumber}>
                  <GradientAccountCard
                    accountNumber={account.accountNumber}
                    accountName={account.accountName}
                    balance={account.balance}
                    kind="Savings"
                    manageTo={`/accounts/${encodeURIComponent(account.accountNumber)}`}
                    className="h-full"
                  />
                </li>
              ))}
            </ul>
            {totalPages > 1 ? (
              <p className="text-xs text-text-subtle">
                Showing the first {LIST_PAGE_SIZE} of {totalElements} accounts.
              </p>
            ) : null}
          </>
        )}
      </section>

      <Sheet
        open={opening}
        variant="bottom"
        title="Open a new account"
        description="The bank assigns its number automatically."
        onDismiss={() => setOpening(false)}
      >
        <section aria-labelledby="open-account-heading">
          <span id="open-account-heading" className="sr-only">
            Open a new account
          </span>
          <OpenAccountForm
            feedback="external"
            onError={(message) => setToast({ tone: 'error', message })}
            onCreated={onCreated}
          />
        </section>
      </Sheet>

      <AccountActionSheet
        action={action}
        accounts={accounts}
        onDismiss={() => setAction(null)}
        onDone={onMoneyMoved}
        onError={(message) => setToast({ tone: 'error', message })}
      />

      <Toast
        open={toast !== null}
        tone={toast?.tone}
        message={toast?.message ?? ''}
        onDismiss={() => setToast(null)}
      />
    </div>
  )
}
