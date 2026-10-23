import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { OpenAccountForm } from './OpenAccountForm'
import { bankingApi, type Account } from '../api/banking'
import { ApiError } from '../api/errors'

vi.mock('../api/banking', () => ({
  bankingApi: { createAccount: vi.fn() },
}))

const createAccount = vi.mocked(bankingApi.createAccount)

/** The number is the bank's answer, not something the form sent. */
const CREATED: Account = {
  accountNumber: '59123456782',
  accountName: 'Juan Dela Cruz',
  balance: '25.00',
  createdAt: '2026-10-22T10:00:00Z',
}

beforeEach(() => {
  vi.clearAllMocks()
})

describe('OpenAccountForm', () => {
  it('has no account number field at all', () => {
    render(<OpenAccountForm onCreated={vi.fn()} />)

    expect(screen.queryByLabelText('Account number')).toBeNull()
    expect(screen.getByText(/bank assigns the account number/i)).toBeInTheDocument()
  })

  it('rejects an empty form with a message per field and no API call', async () => {
    render(<OpenAccountForm onCreated={vi.fn()} />)

    await userEvent.click(screen.getByRole('button', { name: 'Open account' }))

    expect(await screen.findByText('Enter the account holder name.')).toBeInTheDocument()
    expect(screen.getByText('Enter 0 to open the account with no money in it.')).toBeInTheDocument()
    expect(createAccount).not.toHaveBeenCalled()
  })

  it('trims the name, pads the deposit, and reports the account it was given', async () => {
    createAccount.mockResolvedValueOnce(CREATED)
    const onCreated = vi.fn()
    render(<OpenAccountForm onCreated={onCreated} />)

    await userEvent.type(screen.getByLabelText('Account holder name'), ' Juan Dela Cruz ')
    await userEvent.type(screen.getByLabelText('Opening deposit'), '25')
    await userEvent.click(screen.getByRole('button', { name: 'Open account' }))

    expect(createAccount).toHaveBeenCalledWith({
      accountName: 'Juan Dela Cruz',
      initialDeposit: '25.00',
    })
    expect(onCreated).toHaveBeenCalledWith(CREATED)
    // The form is ready for the next account.
    expect(screen.getByLabelText('Account holder name')).toHaveValue('')
    expect(screen.getByLabelText('Opening deposit')).toHaveValue('')
  })

  it('allows a zero opening deposit', async () => {
    createAccount.mockResolvedValueOnce({ ...CREATED, balance: '0.00' })
    render(<OpenAccountForm onCreated={vi.fn()} />)

    await userEvent.type(screen.getByLabelText('Account holder name'), 'Maria Clara')
    await userEvent.type(screen.getByLabelText('Opening deposit'), '0')
    await userEvent.click(screen.getByRole('button', { name: 'Open account' }))

    expect(createAccount).toHaveBeenCalledWith(
      expect.objectContaining({ initialDeposit: '0.00' }),
    )
  })

  it('shows the backend failure and keeps the typed values', async () => {
    // 503, not 409: a customer is never told a number was already taken, so the
    // only failure this form can report is the bank not handing one out.
    createAccount.mockRejectedValueOnce(
      new ApiError(503, 'Service Unavailable', 'Could not assign an account number. Please try again.'),
    )
    render(<OpenAccountForm onCreated={vi.fn()} />)

    await userEvent.type(screen.getByLabelText('Account holder name'), 'Juan Dela Cruz')
    await userEvent.type(screen.getByLabelText('Opening deposit'), '25')
    await userEvent.click(screen.getByRole('button', { name: 'Open account' }))

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Could not assign an account number. Please try again.',
    )
    expect(screen.getByLabelText('Account holder name')).toHaveValue('Juan Dela Cruz')
    expect(screen.getByLabelText('Opening deposit')).toHaveValue('25')
  })
})
