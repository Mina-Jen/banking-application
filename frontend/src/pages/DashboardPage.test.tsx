import { act, render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { DashboardPage } from './DashboardPage'
import { bankingApi, type Account, type AccountPage } from '../api/banking'
import type { Transaction } from '../api/types'
import { ApiError } from '../api/errors'

/**
 * `vi.hoisted` because the mock factory below runs while the imports above are
 * still being resolved: the object has to exist before `useAuth` can close over
 * it. This page is the customer dashboard; the admin overview has its own tests.
 */
const auth = vi.hoisted(() => ({ username: 'alice' }))

vi.mock('../auth/AuthContext', () => ({ useAuth: () => auth }))

vi.mock('../api/banking', () => ({
  bankingApi: {
    accounts: vi.fn(),
    getAccount: vi.fn(),
    createAccount: vi.fn(),
    deposit: vi.fn(),
    withdraw: vi.fn(),
    transfer: vi.fn(),
  },
}))

const accounts = vi.mocked(bankingApi.accounts)
const getAccount = vi.mocked(bankingApi.getAccount)
const deposit = vi.mocked(bankingApi.deposit)
const transfer = vi.mocked(bankingApi.transfer)

function account(accountNumber: string, balance: string): Account {
  return {
    accountNumber,
    accountName: `Account ${accountNumber}`,
    balance,
    createdAt: '2026-10-20T08:00:00Z',
  }
}

function pageOf(items: Account[], overrides: Partial<AccountPage> = {}): AccountPage {
  return {
    accounts: items,
    page: 0,
    size: 100,
    totalElements: items.length,
    totalPages: 1,
    ...overrides,
  }
}

function transaction(overrides: Partial<Transaction> = {}): Transaction {
  return {
    id: 1,
    reference: 'TXN-dashboard-1',
    accountNumber: '200001',
    type: 'DEPOSIT',
    amount: '25.00',
    balanceAfter: '1525.00',
    referenceAccount: '',
    description: '',
    category: '',
    createdAt: '2026-10-23T10:00:00Z',
    ...overrides,
  }
}

function deferred<T>() {
  let resolve!: (value: T) => void
  let reject!: (reason: unknown) => void
  const promise = new Promise<T>((res, rej) => {
    resolve = res
    reject = rej
  })
  return { promise, resolve, reject }
}

function renderDashboard() {
  return render(
    <MemoryRouter>
      <DashboardPage />
    </MemoryRouter>,
  )
}

beforeEach(() => {
  auth.username = 'alice'
  vi.clearAllMocks()
  accounts.mockResolvedValue(pageOf([]))
  deposit.mockResolvedValue(transaction())
  transfer.mockResolvedValue(
    transaction({
      type: 'TRANSFER_OUT',
      referenceAccount: '200002',
      description: '',
      category: '',
      balanceAfter: '1475.00',
    }),
  )
})

describe('DashboardPage states', () => {
  it('loads one page of the caller accounts and totals it', async () => {
    const pending = deferred<AccountPage>()
    accounts.mockReturnValueOnce(pending.promise)

    renderDashboard()

    expect(screen.getByText(/Loading accounts…/)).toBeInTheDocument()
    expect(accounts).toHaveBeenCalledWith(0, 100)

    await act(async () => {
      pending.resolve(pageOf([account('200001', '1500.00'), account('200002', '250.00')]))
    })

    expect(await screen.findByText('Account 200001')).toBeInTheDocument()
    expect(screen.getByText('Account 200002')).toBeInTheDocument()
    expect(screen.queryByText(/Loading accounts…/)).not.toBeInTheDocument()
    // Each balance once in its row, once in the total: ₱1,750.00 sums the page.
    expect(screen.getByText('Total balance')).toBeInTheDocument()
    expect(screen.getAllByText('₱1,500.00')).toHaveLength(1)
    expect(screen.getByText('₱1,750.00')).toBeInTheDocument()
    // Nothing is looked up per number any more; the list is the only call.
    expect(getAccount).not.toHaveBeenCalled()
  })

  it('shows the customer empty state when the backend returns no accounts', async () => {
    renderDashboard()

    expect(await screen.findByText(/No accounts yet\. Open one to get started\./)).toBeInTheDocument()
    expect(screen.queryByText(/this browser remembers the numbers/)).not.toBeInTheDocument()
    expect(screen.queryByText(/Remove from list/)).not.toBeInTheDocument()
  })

  it('reports a failed list and recovers when the user retries', async () => {
    accounts.mockRejectedValueOnce(new ApiError(500, 'Internal Server Error', 'Backend unavailable.'))

    renderDashboard()

    const alert = await screen.findByRole('alert')
    expect(alert).toHaveTextContent('The account list could not be loaded')
    expect(alert).toHaveTextContent('Backend unavailable.')

    accounts.mockResolvedValueOnce(pageOf([account('200001', '1500.00')]))
    await userEvent.click(screen.getByRole('button', { name: 'Try again' }))

    expect(await screen.findByText('Account 200001')).toBeInTheDocument()
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
    expect(accounts).toHaveBeenCalledTimes(2)
  })

  it('shows the backend rate-limit message when the list is throttled', async () => {
    accounts.mockRejectedValueOnce(
      new ApiError(429, 'Too Many Requests', 'Too many requests. Try again in 42 seconds.', 42),
    )

    renderDashboard()

    const alert = await screen.findByRole('alert')
    expect(alert).toHaveTextContent('The account list could not be loaded')
    // The backend's own wording, including the wait it asked for, instead of a
    // generic "request failed".
    expect(alert).toHaveTextContent('Too many requests. Try again in 42 seconds.')
  })

  it('mentions the rest of the list when the page is only part of it', async () => {
    const first = Array.from({ length: 100 }, (_, index) =>
      account(String(300000 + index), '100.00'),
    )
    accounts.mockResolvedValueOnce(pageOf(first, { totalElements: 250, totalPages: 3 }))

    renderDashboard()

    expect(await screen.findByText(/Showing the first 100 of 250 accounts\./)).toBeInTheDocument()
  })
})

describe('DashboardPage roles', () => {
  it('keeps the lookup panel away from customers', async () => {
    renderDashboard()
    await screen.findByText(/No accounts yet/)

    expect(screen.queryByText('Look up an account')).not.toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Your accounts' })).toBeInTheDocument()
    // The operator wording belongs to the admin overview, which is a different page.
    expect(screen.queryByRole('heading', { name: 'All accounts' })).not.toBeInTheDocument()
  })
})

describe('DashboardPage money actions', () => {
  it('disables every money action until an account exists', async () => {
    renderDashboard()

    await screen.findByText('No accounts yet. Open one to get started.')
    for (const name of ['Deposit', 'Withdraw', 'Transfer']) {
      expect(screen.getByRole('button', { name })).toBeDisabled()
    }
  })

  it('renders masked gradient cards with separate Manage links', async () => {
    accounts.mockResolvedValueOnce(pageOf([account('200001', '1500.00')]))
    renderDashboard()

    expect(await screen.findByText('•••• 0001')).toBeInTheDocument()
    expect(screen.queryByText('200001')).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Copy number' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Manage' })).toHaveAttribute('href', '/accounts/200001')
  })

  it('opens Deposit in a dialog and makes the source account explicit', async () => {
    accounts.mockResolvedValueOnce(
      pageOf([account('200001', '1500.00'), account('200002', '250.00')]),
    )
    renderDashboard()
    await screen.findByText('Account 200001')

    await userEvent.click(screen.getByRole('button', { name: 'Deposit' }))

    const dialog = screen.getByRole('dialog', { name: 'Deposit' })
    const accountPicker = within(dialog).getByLabelText('Account to use')
    expect(accountPicker).toHaveValue('200001')
    expect(within(dialog).getByLabelText('Amount to deposit')).toBeInTheDocument()
    expect(within(dialog).getByRole('option', { name: /Account 200002.*₱250\.00/ })).toBeInTheDocument()
  })

  it('uses the account selected in the action dialog and shows success as a toast', async () => {
    accounts.mockResolvedValue(
      pageOf([account('200001', '1500.00'), account('200002', '250.00')]),
    )
    renderDashboard()
    await screen.findByText('Account 200001')

    await userEvent.click(screen.getByRole('button', { name: 'Deposit' }))
    const actionDialog = screen.getByRole('dialog', { name: 'Deposit' })
    await userEvent.selectOptions(within(actionDialog).getByLabelText('Account to use'), '200002')
    await userEvent.type(within(actionDialog).getByLabelText('Amount to deposit'), '25')
    await userEvent.click(within(actionDialog).getByRole('button', { name: 'Deposit' }))

    const confirm = await screen.findByRole('dialog', { name: 'Confirm deposit' })
    expect(within(confirm).getByText('200002')).toBeInTheDocument()
    await userEvent.click(within(confirm).getByRole('button', { name: 'Deposit ₱25.00' }))

    expect(deposit).toHaveBeenCalledWith('200002', '25.00', expect.any(String))
    const toast = await screen.findByRole('status')
    expect(toast).toHaveTextContent('₱25.00 recorded. Reference TXN-dashboard-1.')
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
  })

  it('shows dashboard action failures as a persistent error toast', async () => {
    accounts.mockResolvedValueOnce(pageOf([account('200001', '1500.00')]))
    deposit.mockRejectedValueOnce(
      new ApiError(503, 'Service Unavailable', 'Deposits are unavailable right now.'),
    )
    renderDashboard()
    await screen.findByText('Account 200001')

    await userEvent.click(screen.getByRole('button', { name: 'Deposit' }))
    const actionDialog = screen.getByRole('dialog', { name: 'Deposit' })
    await userEvent.type(within(actionDialog).getByLabelText('Amount to deposit'), '25')
    await userEvent.click(within(actionDialog).getByRole('button', { name: 'Deposit' }))
    const confirm = await screen.findByRole('dialog', { name: 'Confirm deposit' })
    await userEvent.click(within(confirm).getByRole('button', { name: 'Deposit ₱25.00' }))

    const toast = await screen.findByRole('alert')
    expect(toast).toHaveTextContent('Deposits are unavailable right now.')
    expect(within(toast).getByRole('button', { name: 'Dismiss message' })).toBeInTheDocument()
  })

  it('keeps the transfer amount and recipient in a confirmation step', async () => {
    accounts.mockResolvedValueOnce(pageOf([account('200001', '1500.00')]))
    renderDashboard()
    await screen.findByText('Account 200001')

    await userEvent.click(screen.getByRole('button', { name: 'Transfer' }))
    const actionDialog = screen.getByRole('dialog', { name: 'Transfer' })
    await userEvent.type(within(actionDialog).getByLabelText('To account number'), '200002')
    await userEvent.type(within(actionDialog).getByLabelText('Amount'), '30')
    await userEvent.click(within(actionDialog).getByRole('button', { name: 'Transfer' }))

    const confirm = await screen.findByRole('dialog', { name: 'Confirm transfer' })
    expect(within(confirm).getByText('₱30.00')).toBeInTheDocument()
    expect(within(confirm).getByText('200002')).toBeInTheDocument()
    expect(transfer).not.toHaveBeenCalled()
  })
})
