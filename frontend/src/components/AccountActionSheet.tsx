import { useEffect, useState } from 'react'
import { bankingApi, type Account } from '../api/banking'
import type { Transaction } from '../api/types'
import { formatPesos } from '../lib/money'
import { AmountActionForm } from './AmountActionForm'
import { Sheet } from './Sheet'
import { TransferForm } from './TransferForm'
import { label, select } from './ui'

export type AccountAction = 'deposit' | 'withdraw' | 'transfer'

const COPY: Record<AccountAction, { title: string; description: string }> = {
  deposit: { title: 'Deposit', description: 'Add money to one of your accounts.' },
  withdraw: { title: 'Withdraw', description: 'Take money from one of your accounts.' },
  transfer: { title: 'Transfer', description: 'Send money from one account to another.' },
}

/**
 * Hosts the existing money forms in a bottom sheet. A dashboard can own several
 * accounts, so it always shows the source account explicitly and lets the user
 * change it before an amount is entered. Changing it remounts the form, which
 * also releases any idempotency key tied to the previous intent.
 */
export function AccountActionSheet({
  action,
  accounts,
  onDismiss,
  onDone,
  onError,
}: {
  action: AccountAction | null
  accounts: Account[]
  onDismiss(): void
  onDone(action: AccountAction, transaction: Transaction): void
  onError(message: string): void
}) {
  const [accountNumber, setAccountNumber] = useState(accounts[0]?.accountNumber ?? '')

  useEffect(() => {
    if (action !== null && !accounts.some((account) => account.accountNumber === accountNumber)) {
      setAccountNumber(accounts[0]?.accountNumber ?? '')
    }
  }, [action, accounts, accountNumber])

  if (action === null) {
    return null
  }

  const account = accounts.find((candidate) => candidate.accountNumber === accountNumber) ?? accounts[0]
  if (!account) {
    return null
  }

  const copy = COPY[action]
  const result = (transaction: Transaction) => onDone(action, transaction)

  return (
    <Sheet
      open
      variant="bottom"
      title={copy.title}
      description={copy.description}
      onDismiss={onDismiss}
    >
      <div className="mb-5">
        <label htmlFor="action-account" className={label}>
          Account to use
        </label>
        <select
          id="action-account"
          value={account.accountNumber}
          onChange={(event) => setAccountNumber(event.target.value)}
          className={select}
        >
          {accounts.map((option) => (
            <option key={option.accountNumber} value={option.accountNumber}>
              {option.accountName} · •••• {option.accountNumber.slice(-4)} · {formatPesos(option.balance)}
            </option>
          ))}
        </select>
      </div>

      <div key={`${action}-${account.accountNumber}`}>
        {action === 'transfer' ? (
          <TransferForm
            sourceAccountNumber={account.accountNumber}
            availableBalance={account.balance}
            run={(input, key) => bankingApi.transfer(input, key)}
            feedback="external"
            onError={onError}
            onDone={result}
          />
        ) : (
          <AmountActionForm
            kind={action}
            accountNumber={account.accountNumber}
            availableBalance={account.balance}
            fieldLabel={action === 'deposit' ? 'Amount to deposit' : 'Amount to withdraw'}
            submitLabel={action === 'deposit' ? 'Deposit' : 'Withdraw'}
            busyLabel={action === 'deposit' ? 'Depositing…' : 'Withdrawing…'}
            run={(amount, key) =>
              action === 'deposit'
                ? bankingApi.deposit(account.accountNumber, amount, key)
                : bankingApi.withdraw(account.accountNumber, amount, key)
            }
            feedback="external"
            onError={onError}
            onDone={result}
          />
        )}
      </div>
    </Sheet>
  )
}
