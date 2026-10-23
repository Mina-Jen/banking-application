import { act, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { DashboardPage } from './DashboardPage'
import { bankingApi, type Account, type AccountPage } from '../api/banking'
import { ApiError } from '../api/errors'

/**
 * `vi.hoisted` because the mock factory below runs while the imports above are
 * still being resolved: the object has to exist before `useAuth` can close over
 * it. Tests flip `isAdmin` to see the dashboard as an operator.
 */
const auth = vi.hoisted(() => ({ username: 'alice', isAdmin: false }))

vi.mock('../auth/AuthContext', () => ({ useAuth: () => auth }))

vi.mock('../api/banking', () => ({
  bankingApi: {
    accounts: vi.fn(),
    getAccount: vi.fn(),
    createAccount: vi.fn(),
  },
}))

const accounts = vi.mocked(bankingApi.accounts)
const getAccount = vi.mocked(bankingApi.getAccount)

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
  auth.isAdmin = false
  vi.clearAllMocks()
  accounts.mockResolvedValue(pageOf([]))
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
  })

  it('gives admins the lookup panel and the operator wording', async () => {
    auth.isAdmin = true
    accounts.mockResolvedValueOnce(pageOf([account('200001', '1500.00')]))

    renderDashboard()

    expect(await screen.findByRole('heading', { name: 'All accounts' })).toBeInTheDocument()
    expect(screen.getByText('Look up an account')).toBeInTheDocument()

    getAccount.mockResolvedValueOnce(account('200001', '1500.00'))
    await userEvent.type(screen.getByLabelText('Account number'), '200001')
    await userEvent.click(screen.getByRole('button', { name: 'Open' }))

    expect(getAccount).toHaveBeenCalledWith('200001')
  })

  it('announces a refused lookup without navigating away', async () => {
    auth.isAdmin = true
    renderDashboard()
    await screen.findByText('Look up an account')

    // What the backend answers for both "not yours" and "does not exist".
    getAccount.mockRejectedValueOnce(new ApiError(404, 'Not Found', 'Account not found.'))
    await userEvent.type(screen.getByLabelText('Account number'), '999999')
    await userEvent.click(screen.getByRole('button', { name: 'Open' }))

    const alert = await screen.findByRole('alert')
    expect(alert).toHaveTextContent('Account not found.')
    // The backend wording never repeats the number, and neither may the page.
    expect(alert).not.toHaveTextContent('999999')
  })
})
