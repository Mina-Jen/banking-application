import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { AmountActionForm } from './AmountActionForm'
import type { Transaction } from '../api/types'
import { ApiError } from '../api/errors'

const ACCOUNT = '200001'
const BALANCE = '1000.00'

function transaction(overrides: Partial<Transaction> = {}): Transaction {
  return {
    id: 1,
    reference: 'TXN-test-1',
    accountNumber: ACCOUNT,
    type: 'DEPOSIT',
    amount: '25.50',
    balanceAfter: '1025.50',
    referenceAccount: '',
    createdAt: '2026-10-22T10:00:00Z',
    ...overrides,
  }
}

function renderDeposit(overrides: Partial<React.ComponentProps<typeof AmountActionForm>> = {}) {
  const run = vi.fn(async (_amount: string, _key: string): Promise<Transaction> => transaction())
  const onDone = vi.fn()
  const view = render(
    <AmountActionForm
      kind="deposit"
      accountNumber={ACCOUNT}
      availableBalance={BALANCE}
      fieldLabel="Amount to deposit"
      submitLabel="Deposit"
      busyLabel="Depositing…"
      run={run}
      onDone={onDone}
      {...overrides}
    />,
  )
  return { ...view, run, onDone }
}

function renderWithdrawal(
  overrides: Partial<React.ComponentProps<typeof AmountActionForm>> = {},
) {
  const run = vi.fn(async (_amount: string, _key: string): Promise<Transaction> =>
    transaction({ type: 'WITHDRAW', amount: '40.00', balanceAfter: '960.00' }),
  )
  const onDone = vi.fn()
  const view = render(
    <AmountActionForm
      kind="withdraw"
      accountNumber={ACCOUNT}
      availableBalance={BALANCE}
      fieldLabel="Amount to withdraw"
      submitLabel="Withdraw"
      busyLabel="Withdrawing…"
      run={run}
      onDone={onDone}
      {...overrides}
    />,
  )
  return { ...view, run, onDone }
}

describe('AmountActionForm', () => {
  it('rejects an empty amount without opening the dialog or calling the API', async () => {
    const { run } = renderDeposit()

    await userEvent.click(screen.getByRole('button', { name: 'Deposit' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('Enter an amount.')
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    expect(run).not.toHaveBeenCalled()
  })

  it('rejects a malformed amount and announces it', async () => {
    const { run } = renderDeposit()
    await userEvent.type(screen.getByLabelText('Amount to deposit'), '12.345')

    await userEvent.click(screen.getByRole('button', { name: 'Deposit' }))

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Amount may have at most two decimal places.',
    )
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    expect(run).not.toHaveBeenCalled()
  })

  it('shows the amount and account in a confirmation dialog before any money moves', async () => {
    const { run } = renderDeposit()
    await userEvent.type(screen.getByLabelText('Amount to deposit'), '25.5')

    await userEvent.click(screen.getByRole('button', { name: 'Deposit' }))

    const dialog = await screen.findByRole('dialog')
    expect(dialog).toHaveAccessibleName('Confirm deposit')
    expect(within(dialog).getByText('₱25.50')).toBeInTheDocument()
    expect(within(dialog).getByText('To account')).toBeInTheDocument()
    expect(within(dialog).getByText(ACCOUNT)).toBeInTheDocument()
    expect(within(dialog).getByText('Current balance')).toBeInTheDocument()
    expect(within(dialog).getByText('₱1,000.00')).toBeInTheDocument()
    // Merely pressing the form button must not move money.
    expect(run).not.toHaveBeenCalled()
  })

  it('sends the normalized amount with a non-empty key only after Confirm', async () => {
    const { run, onDone } = renderDeposit()
    await userEvent.type(screen.getByLabelText('Amount to deposit'), '25.5')
    await userEvent.click(screen.getByRole('button', { name: 'Deposit' }))

    const dialog = await screen.findByRole('dialog')
    await userEvent.click(within(dialog).getByRole('button', { name: 'Deposit ₱25.50' }))

    expect(run).toHaveBeenCalledTimes(1)
    const [amount, key] = run.mock.calls[0]
    expect(amount).toBe('25.50')
    expect(typeof key).toBe('string')
    expect(key.length).toBeGreaterThanOrEqual(1)
    expect(onDone).toHaveBeenCalledTimes(1)
  })

  it('reports success, clears the amount, and refreshes via onDone', async () => {
    const { onDone } = renderDeposit()
    await userEvent.type(screen.getByLabelText('Amount to deposit'), '25.50')
    await userEvent.click(screen.getByRole('button', { name: 'Deposit' }))
    await userEvent.click(
      within(await screen.findByRole('dialog')).getByRole('button', { name: 'Deposit ₱25.50' }),
    )

    expect(await screen.findByText(/₱25\.50 recorded\. Reference TXN-test-1\./)).toBeInTheDocument()
    expect(screen.getByText(/New balance ₱1,025\.50\./)).toBeInTheDocument()
    expect(screen.getByLabelText('Amount to deposit')).toHaveValue('')
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    expect(onDone).toHaveBeenCalledWith(expect.objectContaining({ reference: 'TXN-test-1' }))
  })

  it('shows the backend message on failure and reuses the same key on retry', async () => {
    const failure = new ApiError(422, 'Unprocessable Entity', 'Insufficient balance for withdrawal.')
    const run = vi
      .fn<(amount: string, key: string) => Promise<Transaction>>()
      .mockRejectedValueOnce(failure)
      .mockResolvedValueOnce(transaction({ type: 'WITHDRAW', amount: '40.00', balanceAfter: '960.00' }))
    renderWithdrawal({ run })

    await userEvent.type(screen.getByLabelText('Amount to withdraw'), '40')
    await userEvent.click(screen.getByRole('button', { name: 'Withdraw' }))
    await userEvent.click(
      within(await screen.findByRole('dialog')).getByRole('button', { name: 'Withdraw ₱40.00' }),
    )

    expect(await screen.findByRole('alert')).toHaveTextContent('Insufficient balance for withdrawal.')
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()

    // The amount is still in the field; the user simply presses Withdraw again.
    await userEvent.click(screen.getByRole('button', { name: 'Withdraw' }))
    await userEvent.click(
      within(await screen.findByRole('dialog')).getByRole('button', { name: 'Withdraw ₱40.00' }),
    )

    expect(run).toHaveBeenCalledTimes(2)
    expect(run.mock.calls[1][1]).toBe(run.mock.calls[0][1])
  })

  it('gets a fresh key after the amount is edited', async () => {
    const run = vi
      .fn<(amount: string, key: string) => Promise<Transaction>>()
      .mockRejectedValueOnce(new ApiError(409, 'Conflict', 'That key was already used.'))
      .mockResolvedValueOnce(transaction())
    const { onDone } = renderDeposit({ run })

    await userEvent.type(screen.getByLabelText('Amount to deposit'), '10')
    await userEvent.click(screen.getByRole('button', { name: 'Deposit' }))
    await userEvent.click(
      within(await screen.findByRole('dialog')).getByRole('button', { name: 'Deposit ₱10.00' }),
    )
    await screen.findByRole('alert')

    const amount = screen.getByLabelText('Amount to deposit')
    await userEvent.clear(amount)
    await userEvent.type(amount, '11')
    await userEvent.click(screen.getByRole('button', { name: 'Deposit' }))
    await userEvent.click(
      within(await screen.findByRole('dialog')).getByRole('button', { name: 'Deposit ₱11.00' }),
    )

    expect(run).toHaveBeenCalledTimes(2)
    expect(run.mock.calls[1][0]).toBe('11.00')
    expect(run.mock.calls[1][1]).not.toBe(run.mock.calls[0][1])
    expect(onDone).toHaveBeenCalledTimes(1)
  })

  it('lets a withdrawal of exactly the balance through', async () => {
    const { run } = renderWithdrawal()
    await userEvent.type(screen.getByLabelText('Amount to withdraw'), '1000.00')

    await userEvent.click(screen.getByRole('button', { name: 'Withdraw' }))

    expect(await screen.findByRole('dialog')).toBeInTheDocument()
    expect(run).not.toHaveBeenCalled()
  })

  it('rejects a withdrawal above the balance before any API call', async () => {
    const { run } = renderWithdrawal()
    await userEvent.type(screen.getByLabelText('Amount to withdraw'), '1000.01')

    await userEvent.click(screen.getByRole('button', { name: 'Withdraw' }))

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Amount exceeds the available balance of ₱1,000.00.',
    )
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    expect(run).not.toHaveBeenCalled()
  })
})
