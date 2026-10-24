import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import type { Transaction } from '../api/types'
import { TransactionTable } from './TransactionTable'

const NOW = new Date(2026, 9, 23, 12)

function localIso(day: number, hour: number): string {
  return new Date(2026, 9, day, hour).toISOString()
}

function transaction(overrides: Partial<Transaction> = {}): Transaction {
  return {
    id: 1,
    reference: 'TXN-1',
    accountNumber: '200001',
    type: 'DEPOSIT',
    amount: '50.00',
    balanceAfter: '1050.00',
    referenceAccount: '',
    description: '',
    category: '',
    createdAt: localIso(23, 9),
    ...overrides,
  }
}

describe('TransactionTable', () => {
  it('shows a useful empty state', () => {
    render(<TransactionTable transactions={[]} now={NOW} />)

    expect(screen.getByRole('heading', { name: 'No transactions yet' })).toBeInTheDocument()
    expect(screen.getByText(/Deposits, withdrawals, and transfers/)).toBeInTheDocument()
  })

  it('groups local dates as Today, Yesterday, and a calendar date', () => {
    const olderDate = new Date(2026, 9, 20, 9)
    render(
      <TransactionTable
        now={NOW}
        transactions={[
          transaction({ id: 1, createdAt: localIso(23, 9) }),
          transaction({ id: 2, createdAt: localIso(22, 9) }),
          transaction({ id: 3, createdAt: olderDate.toISOString() }),
        ]}
      />,
    )

    expect(screen.getByRole('heading', { name: 'Today' })).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Yesterday' })).toBeInTheDocument()
    expect(
      screen.getByRole('heading', {
        name: olderDate.toLocaleDateString(undefined, {
          month: 'long',
          day: 'numeric',
          year: 'numeric',
        }),
      }),
    ).toBeInTheDocument()
  })

  it('sorts each page newest first without changing the input array', () => {
    const transactions = [
      transaction({ id: 1, reference: 'TXN-early', createdAt: localIso(23, 8) }),
      transaction({
        id: 2,
        reference: 'TXN-late',
        type: 'TRANSFER_OUT',
        referenceAccount: '200002',
        description: '',
        category: '',
        createdAt: localIso(23, 11),
      }),
    ]

    render(<TransactionTable transactions={transactions} now={NOW} />)

    const newest = screen.getByText('Sent to 200002')
    const oldest = screen.getByText('Deposit')
    expect(newest.compareDocumentPosition(oldest) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy()
    expect(transactions.map((item) => item.id)).toEqual([1, 2])
  })

  it('labels every direction and signs incoming and outgoing amounts', () => {
    render(
      <TransactionTable
        now={NOW}
        transactions={[
          transaction({ id: 1, type: 'DEPOSIT', amount: '100.00' }),
          transaction({ id: 2, type: 'WITHDRAW', amount: '20.00', createdAt: localIso(23, 10) }),
          transaction({
            id: 3,
            type: 'TRANSFER_IN',
            amount: '30.00',
            referenceAccount: '200003',
            description: '',
            category: '',
            createdAt: localIso(23, 11),
          }),
          transaction({
            id: 4,
            type: 'TRANSFER_OUT',
            amount: '40.00',
            referenceAccount: '200004',
            description: '',
            category: '',
            createdAt: localIso(23, 12),
          }),
        ]}
      />,
    )

    expect(screen.getByText('Deposit')).toBeInTheDocument()
    expect(screen.getByText('Withdrawal')).toBeInTheDocument()
    expect(screen.getByText('Received from 200003')).toBeInTheDocument()
    expect(screen.getByText('Sent to 200004')).toBeInTheDocument()
    expect(screen.getByText('+₱100.00')).toHaveClass('text-success', 'tabular-nums')
    expect(screen.getByText('+₱30.00')).toHaveClass('text-success', 'tabular-nums')
    expect(screen.getByText('−₱20.00')).toHaveClass('text-danger', 'tabular-nums')
    expect(screen.getByText('−₱40.00')).toHaveClass('text-danger', 'tabular-nums')
  })

  it('renders rows whose referenceAccount is null, as the API sends it', () => {
    const noReference = null as unknown as string
    render(
      <TransactionTable
        now={NOW}
        transactions={[
          transaction({ id: 1, type: 'DEPOSIT', referenceAccount: noReference }),
          transaction({
            id: 2,
            type: 'WITHDRAW',
            referenceAccount: noReference,
            description: '',
            category: '',
            createdAt: localIso(23, 10),
          }),
          transaction({
            id: 3,
            type: 'TRANSFER_OUT',
            referenceAccount: noReference,
            description: '',
            category: '',
            createdAt: localIso(23, 11),
          }),
        ]}
      />,
    )

    expect(screen.getByText('Deposit')).toBeInTheDocument()
    expect(screen.getByText('Withdrawal')).toBeInTheDocument()
    expect(screen.getByText('Sent to another account')).toBeInTheDocument()
  })
})
