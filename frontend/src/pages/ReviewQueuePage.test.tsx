import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ReviewQueuePage } from './ReviewQueuePage'
import { bankingApi, type AnomalyFlag, type AnomalyFlagPage } from '../api/banking'

vi.mock('../api/banking', () => ({
  bankingApi: {
    anomalyFlags: vi.fn(),
    reviewAnomalyFlag: vi.fn(),
  },
}))

const anomalyFlags = vi.mocked(bankingApi.anomalyFlags)
const reviewAnomalyFlag = vi.mocked(bankingApi.reviewAnomalyFlag)

function flag(overrides: Partial<AnomalyFlag> = {}): AnomalyFlag {
  return {
    id: 12,
    transactionId: 42,
    transactionReference: 'TXN-lz3k9x00a1b2',
    accountNumber: '200001',
    transactionType: 'WITHDRAW',
    amount: '5000.00',
    rule: 'UNUSUAL_AMOUNT',
    status: 'OPEN',
    details: '5000.00 is at least 3x the 100.00 average of the last 3 outbound transactions.',
    createdAt: '2026-10-23T10:15:00Z',
    reviewedAt: null,
    reviewedBy: null,
    ...overrides,
  }
}

function pageOf(flags: AnomalyFlag[], totalPages = 1): AnomalyFlagPage {
  return {
    flags,
    page: 0,
    size: 10,
    totalElements: flags.length,
    totalPages,
  }
}

function renderQueue() {
  return render(
    <MemoryRouter>
      <ReviewQueuePage />
    </MemoryRouter>,
  )
}

beforeEach(() => {
  vi.clearAllMocks()
  anomalyFlags.mockResolvedValue(pageOf([flag()]))
  reviewAnomalyFlag.mockResolvedValue(flag({ status: 'REVIEWED', reviewedBy: 'admin' }))
})

describe('ReviewQueuePage', () => {
  it('opens on the open queue and explains each flag', async () => {
    renderQueue()

    expect(await screen.findByText('Account 200001')).toBeInTheDocument()
    expect(screen.getByText('Unusual amount')).toBeInTheDocument()
    expect(screen.getByText('₱5,000.00')).toBeInTheDocument()
    expect(screen.getByText(/at least 3x the 100.00 average/)).toBeInTheDocument()
    expect(screen.getByText('TXN-lz3k9x00a1b2')).toBeInTheDocument()

    expect(anomalyFlags).toHaveBeenCalledWith('OPEN', 0, 10)
    expect(screen.getByRole('button', { name: 'Mark reviewed' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Dismiss' })).toBeInTheDocument()
  })

  it('asks before closing a flag, then records the decision and reloads', async () => {
    renderQueue()
    await screen.findByText('Account 200001')

    await userEvent.click(screen.getByRole('button', { name: 'Mark reviewed' }))
    const dialog = await screen.findByRole('dialog')
    expect(within(dialog).getByText('Mark this flag as reviewed?')).toBeInTheDocument()
    expect(within(dialog).getByText(/cannot be changed/)).toBeInTheDocument()

    await userEvent.click(within(dialog).getByRole('button', { name: 'Mark reviewed' }))

    expect(reviewAnomalyFlag).toHaveBeenCalledWith(12, 'REVIEWED')
    expect(await screen.findByRole('status')).toHaveTextContent(
      'Flag TXN-lz3k9x00a1b2 marked as reviewed.',
    )
    // The list is fetched again so the closed flag leaves the OPEN view.
    expect(anomalyFlags).toHaveBeenCalledTimes(2)
  })

  it('dismisses a flag only after confirmation', async () => {
    renderQueue()
    await screen.findByText('Account 200001')

    await userEvent.click(screen.getByRole('button', { name: 'Dismiss' }))
    const dialog = await screen.findByRole('dialog')
    expect(within(dialog).getByText('Dismiss this flag?')).toBeInTheDocument()
    await userEvent.click(within(dialog).getByRole('button', { name: 'Dismiss flag' }))

    expect(reviewAnomalyFlag).toHaveBeenCalledWith(12, 'DISMISSED')
  })

  it('cancelling the dialog records nothing', async () => {
    renderQueue()
    await screen.findByText('Account 200001')

    await userEvent.click(screen.getByRole('button', { name: 'Mark reviewed' }))
    const dialog = await screen.findByRole('dialog')
    await userEvent.click(within(dialog).getByRole('button', { name: 'Cancel' }))

    expect(reviewAnomalyFlag).not.toHaveBeenCalled()
    expect(screen.getByRole('button', { name: 'Mark reviewed' })).toBeInTheDocument()
  })

  it('fetches every state when the All filter is chosen', async () => {
    renderQueue()
    await screen.findByText('Account 200001')

    await userEvent.click(screen.getByRole('button', { name: 'All' }))

    expect(anomalyFlags).toHaveBeenLastCalledWith(undefined, 0, 10)
  })

  it('shows a closed flag with its reviewer and no decision buttons', async () => {
    anomalyFlags.mockResolvedValue(
      pageOf([
        flag({
          status: 'REVIEWED',
          reviewedAt: '2026-10-23T11:00:00Z',
          reviewedBy: 'admin',
        }),
      ]),
    )
    renderQueue()

    expect(await screen.findByText(/Reviewed by admin/)).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Mark reviewed' })).not.toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'View account' })).toBeInTheDocument()
  })

  it('invites work when the open queue is empty', async () => {
    anomalyFlags.mockResolvedValue(pageOf([]))
    renderQueue()

    expect(await screen.findByRole('heading', { name: 'Nothing waiting for review' })).toBeInTheDocument()
  })
})
