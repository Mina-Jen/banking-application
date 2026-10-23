import { cleanup, render, screen } from '@testing-library/react'
import { afterEach, describe, expect, it } from 'vitest'
import { Field } from './Field'

afterEach(() => cleanup())

describe('Field', () => {
  it('wires the hint to the input with aria-describedby and no aria-invalid by default', () => {
    render(<Field label="Amount" hint="Greater than zero." />)

    const input = screen.getByLabelText('Amount')
    expect(input).not.toHaveAttribute('aria-invalid')

    const hintId = input.getAttribute('aria-describedby')
    expect(hintId).not.toBeNull()
    expect(screen.getByText('Greater than zero.')).toHaveAttribute('id', hintId)
  })

  it('marks a neutral hint as guidance, not an alert, so it never interrupts', () => {
    render(<Field label="Amount" hint="Greater than zero." />)

    expect(screen.getByText('Greater than zero.')).not.toHaveAttribute('role')
  })

  it('announces an error hint as an alert and flags the input invalid', () => {
    render(<Field label="Amount" hint="At most two decimals." invalid />)

    const input = screen.getByLabelText('Amount')
    expect(input).toHaveAttribute('aria-invalid', 'true')
    expect(screen.getByText('At most two decimals.')).toHaveAttribute('role', 'alert')
  })

  it('still associates the error hint with the input', () => {
    render(<Field label="Amount" hint="At most two decimals." invalid />)

    const hintId = screen.getByLabelText('Amount').getAttribute('aria-describedby')
    expect(screen.getByText('At most two decimals.')).toHaveAttribute('id', hintId)
  })

  it('renders no hint element at all when there is nothing to say', () => {
    const { container } = render(<Field label="Amount" />)

    expect(container.querySelector('p')).toBeNull()
    expect(screen.getByLabelText('Amount')).not.toHaveAttribute('aria-describedby')
  })

  it('gives two fields with the same label distinct ids', () => {
    render(
      <>
        <Field label="Account number" hint="Source" />
        <Field label="Account number" hint="Destination" />
      </>,
    )

    const [first, second] = screen.getAllByLabelText('Account number')
    expect(first.id).not.toBe(second.id)
    expect(first.getAttribute('aria-describedby')).not.toBe(second.getAttribute('aria-describedby'))
  })
})
