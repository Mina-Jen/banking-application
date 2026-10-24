import { useState } from 'react'
import { cleanup, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { MoneyInput } from './MoneyInput'

afterEach(() => cleanup())

/** The component under test is controlled, so typing needs real state behind it. */
function Controlled({ onAmount }: { onAmount(value: string): void }) {
  const [value, setValue] = useState('')
  return (
    <MoneyInput
      label="Amount"
      value={value}
      onChange={(next) => {
        onAmount(next)
        setValue(next)
      }}
    />
  )
}

describe('MoneyInput', () => {
  it('keeps the field a text input with a decimal keyboard, never type="number"', () => {
    render(<MoneyInput label="Amount" value="" onChange={() => {}} />)

    const input = screen.getByLabelText<HTMLInputElement>('Amount')
    // type="number" returns a JS number in some browsers and accepts "1e5",
    // both of which break the rule that money is never a float.
    expect(input).toHaveAttribute('type', 'text')
    expect(input).toHaveAttribute('inputmode', 'decimal')
  })

  it('hands the typed string through untouched, so exponent junk cannot be silently parsed', async () => {
    const onAmount = vi.fn()
    render(<Controlled onAmount={onAmount} />)

    await userEvent.type(screen.getByLabelText('Amount'), '1e5')

    expect(onAmount).toHaveBeenLastCalledWith('1e5')
    expect(screen.getByLabelText<HTMLInputElement>('Amount').value).toBe('1e5')
  })

  it('shows the peso sign as decoration, so it is not read out on every field', () => {
    const { container } = render(<MoneyInput label="Amount" value="" onChange={() => {}} />)

    const prefix = Array.from(container.querySelectorAll('span')).find(
      (node) => node.textContent === '₱',
    )
    expect(prefix).toBeDefined()
    expect(prefix).toHaveAttribute('aria-hidden', 'true')
  })

  it('echoes a well-formed amount with separators before the user submits', () => {
    render(<MoneyInput label="Amount" value="1234.5" onChange={() => {}} />)

    expect(screen.getByText('₱1,234.5')).toBeInTheDocument()
  })

  it('does not preview a blank field', () => {
    render(<MoneyInput label="Amount" value="   " onChange={() => {}} />)

    // A preview would have replaced the guidance; whitespace is not an amount.
    expect(screen.getByText('Greater than zero, at most two decimals.')).toBeInTheDocument()
  })

  it('prefers the error over the preview, and says it as an alert', () => {
    render(
      <MoneyInput
        label="Amount"
        value="12.999"
        error="Amount may have at most two decimal places."
        onChange={() => {}}
      />,
    )

    expect(screen.getByRole('alert')).toHaveTextContent('Amount may have at most two decimal places.')
    expect(screen.queryByText('₱12.999')).not.toBeInTheDocument()
    expect(screen.getByLabelText('Amount')).toHaveAttribute('aria-invalid', 'true')
  })

  it('falls back to the caller hint, then to its own guidance', () => {
    const { rerender } = render(
      <MoneyInput label="Amount" value="" hint="Zero is allowed here." onChange={() => {}} />,
    )
    expect(screen.getByText('Zero is allowed here.')).toBeInTheDocument()

    rerender(<MoneyInput label="Amount" value="" onChange={() => {}} />)
    expect(screen.getByText('Greater than zero, at most two decimals.')).toBeInTheDocument()
  })

  it('links the preview or error to the input so it is announced with the field', () => {
    render(<MoneyInput label="Amount" value="25" error="Amount must be greater than zero." onChange={() => {}} />)

    const describedBy = screen.getByLabelText('Amount').getAttribute('aria-describedby')
    expect(describedBy).not.toBeNull()
    expect(screen.getByRole('alert')).toHaveAttribute('id', describedBy as string)
  })
})
