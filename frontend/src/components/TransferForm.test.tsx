import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { TransferForm } from './TransferForm'
import type { Transaction } from '../api/types'
import { ApiError } from '../api/errors'

const SOURCE = '200001'
const DESTINATION = '200002'
const BALANCE = '1000.00'

function transaction(overrides: Partial<Transaction> = {}): Transaction {
  return {
    id: 2,
    reference: 'TXN-test-2',
    accountNumber: SOURCE,
    type: 'TRANSFER_OUT',
    amount: '5.00',
    balanceAfter: '995.00',
    referenceAccount: DESTINATION,
    createdAt: '2026-10-22T10:05:00Z',
    ...overrides,
  }
}

function renderTransfer(overrides: Partial<React.ComponentProps<typeof TransferForm>> = {}) {
  const run = vi.fn(
    async (
      _input: { sourceAccountNumber: string; destinationAccountNumber: string; amount: string },
      _key: string,
    ): Promise<Transaction> => transaction(),
  )
  const onDone = vi.fn()
  const view = render(
    <TransferForm
      sourceAccountNumber={SOURCE}
      availableBalance={BALANCE}
      run={run}
      onDone={onDone}
      {...overrides}
    />,
  )
  return { ...view, run, onDone }
}

async function fillAndOpenDialog(destination: string, amount: string) {
  await userEvent.type(screen.getByLabelText('To account number'), destination)
  await userEvent.type(screen.getByLabelText('Amount'), amount)
  await userEvent.click(screen.getByRole('button', { name: 'Transfer' }))
  return screen.findByRole('dialog')
}

describe('TransferForm', () => {
  it('shows the fixed source account, read-only', () => {
    renderTransfer()

    const source = screen.getByLabelText('From account')
    expect(source).toHaveValue(SOURCE)
    expect(source).toBeDisabled()
  })

  it('rejects an empty destination without calling the API', async () => {
    const { run } = renderTransfer()
    await userEvent.type(screen.getByLabelText('Amount'), '5')
    await userEvent.click(screen.getByRole('button', { name: 'Transfer' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('Enter an account number.')
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    expect(run).not.toHaveBeenCalled()
  })

  it('rejects a transfer to the account being viewed', async () => {
    const { run } = renderTransfer()
    await userEvent.type(screen.getByLabelText('To account number'), SOURCE)
    await userEvent.type(screen.getByLabelText('Amount'), '5')

    await userEvent.click(screen.getByRole('button', { name: 'Transfer' }))

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Source and destination must be different accounts.',
    )
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    expect(run).not.toHaveBeenCalled()
  })

  it('rejects a transfer above the balance before any API call', async () => {
    const { run } = renderTransfer()
    await userEvent.type(screen.getByLabelText('To account number'), DESTINATION)
    await userEvent.type(screen.getByLabelText('Amount'), '1000.01')

    await userEvent.click(screen.getByRole('button', { name: 'Transfer' }))

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Amount exceeds the available balance of ₱1,000.00.',
    )
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    expect(run).not.toHaveBeenCalled()
  })

  it('spells out the destination and amount in the confirmation dialog', async () => {
    const { run } = renderTransfer()

    const dialog = await fillAndOpenDialog(`  ${DESTINATION}  `, '5')

    expect(dialog).toHaveAccessibleName('Confirm transfer')
    expect(within(dialog).getByText('₱5.00')).toBeInTheDocument()
    expect(within(dialog).getByText('From account')).toBeInTheDocument()
    expect(within(dialog).getByText(SOURCE)).toBeInTheDocument()
    expect(within(dialog).getByText('To account')).toBeInTheDocument()
    expect(within(dialog).getByText(DESTINATION)).toBeInTheDocument()
    expect(within(dialog).getByText('Current balance')).toBeInTheDocument()
    expect(run).not.toHaveBeenCalled()
  })

  it('sends the trimmed destination and normalized amount with a key after Confirm', async () => {
    const { run, onDone } = renderTransfer()
    const dialog = await fillAndOpenDialog(` ${DESTINATION} `, '5.5')

    await userEvent.click(within(dialog).getByRole('button', { name: 'Transfer ₱5.50' }))

    expect(run).toHaveBeenCalledTimes(1)
    expect(run.mock.calls[0][0]).toEqual({
      sourceAccountNumber: SOURCE,
      destinationAccountNumber: DESTINATION,
      amount: '5.50',
    })
    expect(typeof run.mock.calls[0][1]).toBe('string')
    expect(run.mock.calls[0][1].length).toBeGreaterThanOrEqual(1)
    expect(onDone).toHaveBeenCalledTimes(1)
  })

  it('reports success, clears both fields, and refreshes via onDone', async () => {
    const { onDone } = renderTransfer()
    const dialog = await fillAndOpenDialog(DESTINATION, '5')

    await userEvent.click(within(dialog).getByRole('button', { name: 'Transfer ₱5.00' }))

    expect(
      await screen.findByText(
        /₱5\.00 sent to 200002\. Reference TXN-test-2\. New balance ₱995\.00\./,
      ),
    ).toBeInTheDocument()
    expect(screen.getByLabelText('To account number')).toHaveValue('')
    expect(screen.getByLabelText('Amount')).toHaveValue('')
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    expect(onDone).toHaveBeenCalledWith(expect.objectContaining({ reference: 'TXN-test-2' }))
  })

  it('keeps the key across a failed attempt so a retry cannot send twice', async () => {
    const run = vi
      .fn<
        (
          input: { sourceAccountNumber: string; destinationAccountNumber: string; amount: string },
          key: string,
        ) => Promise<Transaction>
      >()
      .mockRejectedValueOnce(new ApiError(422, 'Unprocessable Entity', 'Insufficient balance.'))
      .mockResolvedValueOnce(transaction())
    renderTransfer({ run })

    await userEvent.type(screen.getByLabelText('To account number'), DESTINATION)
    await userEvent.type(screen.getByLabelText('Amount'), '5')
    await userEvent.click(screen.getByRole('button', { name: 'Transfer' }))
    await userEvent.click(
      within(await screen.findByRole('dialog')).getByRole('button', { name: 'Transfer ₱5.00' }),
    )
    expect(await screen.findByRole('alert')).toHaveTextContent('Insufficient balance.')

    await userEvent.click(screen.getByRole('button', { name: 'Transfer' }))
    await userEvent.click(
      within(await screen.findByRole('dialog')).getByRole('button', { name: 'Transfer ₱5.00' }),
    )

    expect(run).toHaveBeenCalledTimes(2)
    expect(run.mock.calls[1][1]).toBe(run.mock.calls[0][1])
  })

  it('gets a fresh key when the destination is edited after a failure', async () => {
    const run = vi
      .fn<
        (
          input: { sourceAccountNumber: string; destinationAccountNumber: string; amount: string },
          key: string,
        ) => Promise<Transaction>
      >()
      .mockRejectedValueOnce(new ApiError(422, 'Unprocessable Entity', 'Insufficient balance.'))
      .mockResolvedValueOnce(transaction())
    renderTransfer({ run })

    await userEvent.type(screen.getByLabelText('To account number'), DESTINATION)
    await userEvent.type(screen.getByLabelText('Amount'), '5')
    await userEvent.click(screen.getByRole('button', { name: 'Transfer' }))
    await userEvent.click(
      within(await screen.findByRole('dialog')).getByRole('button', { name: 'Transfer ₱5.00' }),
    )
    await screen.findByRole('alert')

    const destination = screen.getByLabelText('To account number')
    await userEvent.clear(destination)
    await userEvent.type(destination, '200003')
    await userEvent.click(screen.getByRole('button', { name: 'Transfer' }))
    await userEvent.click(
      within(await screen.findByRole('dialog')).getByRole('button', { name: 'Transfer ₱5.00' }),
    )

    expect(run).toHaveBeenCalledTimes(2)
    expect(run.mock.calls[1][0].destinationAccountNumber).toBe('200003')
    expect(run.mock.calls[1][1]).not.toBe(run.mock.calls[0][1])
  })
})
