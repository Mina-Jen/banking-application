import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { AccountPage } from './AccountPage'
import { bankingApi, type Account, type TransactionPage } from '../api/banking'
import { ApiError } from '../api/errors'

vi.mock('../api/banking', () => ({
  bankingApi: {
    getAccount: vi.fn(),
    transactionHistory: vi.fn(),
    deposit: vi.fn(),
    withdraw: vi.fn(),
    transfer: vi.fn(),
  },
}))

const getAccount = vi.mocked(bankingApi.getAccount)
const transactionHistory = vi.mocked(bankingApi.transactionHistory)

const ACCOUNT: Account = {
  accountNumber: '200001',
  accountName: 'Alice Santos',
  balance: '1000.00',
  createdAt: '2026-10-20T08:00:00Z',
}

function page(pageNumber: number, totalPages: number, totalElements: number): TransactionPage {
  return {
    transactions: [
      {
        id: pageNumber + 1,
        reference: `TXN-page-${pageNumber}`,
        accountNumber: ACCOUNT.accountNumber,
        type: 'DEPOSIT',
        amount: '10.00',
        balanceAfter: '1000.00',
        referenceAccount: '',
        createdAt: '2026-10-20T09:00:00Z',
      },
    ],
    page: pageNumber,
    size: 10,
    totalElements,
    totalPages,
  }
}

function deferred<T>() {
  let resolve!: (value: T) => void
  const promise = new Promise<T>((res) => {
    resolve = res
  })
  return { promise, resolve }
}

function renderAccountPage(accountNumber = '200001') {
  return render(
    <MemoryRouter initialEntries={[`/accounts/${accountNumber}`]}>
      <Routes>
        <Route path="/accounts/:accountNumber" element={<AccountPage />} />
      </Routes>
    </MemoryRouter>,
  )
}

beforeEach(() => {
  vi.clearAllMocks()
})

describe('AccountPage states', () => {
  it('shows a loading state while the account is fetched', () => {
    const pending = deferred<Account>()
    getAccount.mockReturnValueOnce(pending.promise)
    transactionHistory.mockResolvedValue(page(0, 0, 0))

    renderAccountPage()

    expect(screen.getByText(/Loading account 200001…/)).toBeInTheDocument()
  })

  it('shows a fatal error instead of the forms when the account is not available', async () => {
    // 404 is what the backend answers for a foreign account and for a missing
    // one, and the message deliberately leaves out the number.
    getAccount.mockRejectedValueOnce(new ApiError(404, 'Not Found', 'Account not found.'))
    transactionHistory.mockResolvedValue(page(0, 0, 0))

    renderAccountPage()

    expect(await screen.findByText('This account is not available')).toBeInTheDocument()
    expect(screen.getByText('Account not found.')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: /Back to your accounts/ })).toHaveAttribute('href', '/')
    expect(screen.queryByRole('heading', { name: 'Deposit' })).not.toBeInTheDocument()
  })

  it('renders the balance, the three money forms, and an empty history', async () => {
    getAccount.mockResolvedValue(ACCOUNT)
    transactionHistory.mockResolvedValue({ transactions: [], page: 0, size: 10, totalElements: 0, totalPages: 0 })

    renderAccountPage()

    expect(await screen.findByRole('heading', { name: 'Alice Santos' })).toBeInTheDocument()
    expect(screen.getByText('200001')).toBeInTheDocument()
    expect(screen.getByText('₱1,000.00')).toBeInTheDocument()

    expect(screen.getByRole('heading', { name: 'Deposit' })).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Withdraw' })).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Transfer' })).toBeInTheDocument()

    expect(await screen.findByText('0 transactions, oldest first')).toBeInTheDocument()
    expect(screen.getByText('No transactions on this page yet.')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Previous' })).not.toBeInTheDocument()
  })

  it('opens the most recent page and pages backwards through history', async () => {
    getAccount.mockResolvedValue(ACCOUNT)
    transactionHistory.mockImplementation(async (_accountNumber, pageNumber) =>
      page(pageNumber ?? 0, 3, 25),
    )

    renderAccountPage()

    // History is oldest-first, so the newest transactions are on the last page.
    expect(await screen.findByText('Page 3 of 3')).toBeInTheDocument()
    expect(screen.getByText('25 transactions, oldest first')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Next' })).toBeDisabled()
    expect(screen.getByRole('button', { name: 'Previous' })).toBeEnabled()

    await userEvent.click(screen.getByRole('button', { name: 'Previous' }))

    expect(await screen.findByText('Page 2 of 3')).toBeInTheDocument()
    expect(transactionHistory).toHaveBeenLastCalledWith('200001', 1, 10)
  })

  it('keeps the balance usable when history cannot be loaded', async () => {
    getAccount.mockResolvedValue(ACCOUNT)
    transactionHistory.mockRejectedValue(new Error('history is down'))

    renderAccountPage()

    expect(await screen.findByRole('heading', { name: 'Alice Santos' })).toBeInTheDocument()
    expect(screen.getByText('History is unavailable right now.')).toBeInTheDocument()
    expect(screen.getByText('₱1,000.00')).toBeInTheDocument()
  })
})
