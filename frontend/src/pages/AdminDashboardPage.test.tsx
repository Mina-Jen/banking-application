import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { AdminDashboardPage } from './AdminDashboardPage'
import {
  bankingApi,
  type Account,
  type AnomalyFlagPage,
  type ReconciliationReport,
} from '../api/banking'
import { ApiError } from '../api/errors'

vi.mock('../api/banking', () => ({
  bankingApi: {
    accounts: vi.fn(),
    getAccount: vi.fn(),
    reconciliation: vi.fn(),
    anomalyFlags: vi.fn(),
  },
}))

const accounts = vi.mocked(bankingApi.accounts)
const getAccount = vi.mocked(bankingApi.getAccount)
const reconciliation = vi.mocked(bankingApi.reconciliation)
const anomalyFlags = vi.mocked(bankingApi.anomalyFlags)

function account(accountNumber: string, balance: string): Account {
  return {
    accountNumber,
    accountName: `Account ${accountNumber}`,
    balance,
    createdAt: '2026-10-20T08:00:00Z',
  }
}

function balanced(total: string, checked = 2): ReconciliationReport {
  return {
    reconciled: true,
    checkedAt: '2026-10-24T09:00:00Z',
    checkedAccounts: checked,
    ledgerTotal: total,
    storedTotal: total,
    discrepancies: [],
    discrepanciesTruncated: false,
  }
}

function flagPage(totalElements: number): AnomalyFlagPage {
  return { flags: [], page: 0, size: 1, totalElements, totalPages: totalElements }
}

function renderOverview() {
  return render(
    <MemoryRouter initialEntries={['/']}>
      <Routes>
        <Route path="/" element={<AdminDashboardPage />} />
        <Route path="/accounts/:accountNumber" element={<p>Account detail</p>} />
        <Route path="/review" element={<p>Review queue page</p>} />
      </Routes>
    </MemoryRouter>,
  )
}

beforeEach(() => {
  vi.clearAllMocks()
  accounts.mockResolvedValue({
    accounts: [account('200001', '1500.00'), account('200002', '250.50')],
    page: 0,
    size: 10,
    totalElements: 2,
    totalPages: 1,
  })
  reconciliation.mockResolvedValue(balanced('1750.50'))
  anomalyFlags.mockResolvedValue(flagPage(3))
})

describe('AdminDashboardPage', () => {
  it('summarises accounts, funds and open alerts without offering money actions', async () => {
    renderOverview()

    expect(await screen.findByRole('heading', { name: 'System overview' })).toBeInTheDocument()
    expect(await screen.findByText('Balanced')).toBeInTheDocument()

    /** The hero figure under a KPI label. */
    const kpi = (label: string) => screen.getByText(label).nextElementSibling?.textContent
    expect(kpi('Accounts monitored')).toBe('2')
    expect(kpi('Funds on deposit')).toBe('₱1,750.50')
    expect(kpi('Open alerts')).toBe('3')

    // Both accounts are listed with a way into the read-only detail page.
    expect(screen.getByText('200001')).toBeInTheDocument()
    expect(screen.getByText('200002')).toBeInTheDocument()
    expect(screen.getAllByRole('link', { name: 'View' })).toHaveLength(2)

    // The bank's staff never move money from here.
    expect(screen.queryByRole('button', { name: 'Deposit' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Withdraw' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Transfer' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Open an account' })).not.toBeInTheDocument()

    expect(accounts).toHaveBeenCalledWith(0, 10)
    expect(anomalyFlags).toHaveBeenCalledWith('OPEN', 0, 1)
  })

  it('lists the mismatching accounts when the ledger does not balance', async () => {
    reconciliation.mockResolvedValue({
      reconciled: false,
      checkedAt: '2026-10-24T09:00:00Z',
      checkedAccounts: 2,
      ledgerTotal: '1760.50',
      storedTotal: '1750.50',
      discrepancies: [
        {
          accountNumber: '200001',
          accountName: 'Alice Santos',
          ledgerBalance: '1510.00',
          storedBalance: '1500.00',
          difference: '10.00',
        },
      ],
      discrepanciesTruncated: false,
    })

    renderOverview()

    expect(await screen.findByText('Discrepancies found')).toBeInTheDocument()
    const table = screen.getByRole('table', {
      name: /Accounts whose stored balance does not match the ledger/i,
    })
    expect(within(table).getByText('200001')).toBeInTheDocument()
    expect(within(table).getByText('Alice Santos')).toBeInTheDocument()
    expect(within(table).getByText('₱10.00')).toBeInTheDocument()
  })

  it('re-runs the check and reports the fresh result', async () => {
    renderOverview()
    await screen.findByText('Balanced')

    reconciliation.mockResolvedValue(balanced('1800.00', 3))
    await userEvent.click(screen.getByRole('button', { name: 'Re-run check' }))

    expect(await screen.findByRole('status')).toHaveTextContent(
      'Ledger check finished: all 3 accounts balance.',
    )
  })

  it('opens an account by number through the lookup form', async () => {
    getAccount.mockResolvedValueOnce(account('200002', '250.50'))
    renderOverview()
    await screen.findByText('200001')

    await userEvent.type(screen.getByLabelText('Open an account by number'), '200002')
    await userEvent.click(screen.getByRole('button', { name: 'Open' }))

    expect(getAccount).toHaveBeenCalledWith('200002')
    expect(await screen.findByText('Account detail')).toBeInTheDocument()
  })

  it('reports a refused lookup without leaving the overview', async () => {
    getAccount.mockRejectedValueOnce(new ApiError(404, 'Not Found', 'Account 999999 does not exist.'))
    renderOverview()
    await screen.findByText('200001')

    await userEvent.type(screen.getByLabelText('Open an account by number'), '999999')
    await userEvent.click(screen.getByRole('button', { name: 'Open' }))

    const alert = await screen.findByRole('alert')
    expect(alert).toHaveTextContent('Account 999999 does not exist.')
    expect(screen.getByRole('heading', { name: 'System overview' })).toBeInTheDocument()
  })
})
