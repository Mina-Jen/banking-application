import { cleanup, render, screen } from '@testing-library/react'
import { afterEach, describe, expect, it } from 'vitest'
import { Input } from './Input'

afterEach(() => cleanup())

describe('Input', () => {
  it('wires the hint to the input with aria-describedby and no aria-invalid by default', () => {
    render(<Input label="Amount" hint="Greater than zero." />)

    const input = screen.getByLabelText('Amount')
    expect(input).not.toHaveAttribute('aria-invalid')

    const hintId = input.getAttribute('aria-describedby')
    expect(hintId).not.toBeNull()
    expect(screen.getByText('Greater than zero.')).toHaveAttribute('id', hintId)
  })

  it('marks a neutral hint as guidance, not an alert, so it never interrupts', () => {
    render(<Input label="Amount" hint="Greater than zero." />)

    expect(screen.getByText('Greater than zero.')).not.toHaveAttribute('role')
  })

  it('announces an error hint as an alert and flags the input invalid', () => {
    render(<Input label="Amount" hint="At most two decimals." invalid />)

    const input = screen.getByLabelText('Amount')
    expect(input).toHaveAttribute('aria-invalid', 'true')
    expect(screen.getByText('At most two decimals.')).toHaveAttribute('role', 'alert')
  })

  it('still associates the error hint with the input', () => {
    render(<Input label="Amount" hint="At most two decimals." invalid />)

    const hintId = screen.getByLabelText('Amount').getAttribute('aria-describedby')
    expect(screen.getByText('At most two decimals.')).toHaveAttribute('id', hintId)
  })

  it('renders no hint element at all when there is nothing to say', () => {
    const { container } = render(<Input label="Amount" />)

    expect(container.querySelector('p')).toBeNull()
    expect(screen.getByLabelText('Amount')).not.toHaveAttribute('aria-describedby')
  })

  it('gives two fields with the same label distinct ids', () => {
    render(
      <>
        <Input label="Account number" hint="Source" />
        <Input label="Account number" hint="Destination" />
      </>,
    )

    const [first, second] = screen.getAllByLabelText('Account number')
    expect(first.id).not.toBe(second.id)
    expect(first.getAttribute('aria-describedby')).not.toBe(second.getAttribute('aria-describedby'))
  })

  // Tailwind resolves two same-property utilities by theme order, not by the
  // order they were written, so a calm `ring-field-border` stacked under
  // `ring-danger` wins and the invalid field looks valid. The colour is chosen,
  // never layered — these two tests are what keep it that way.
  it('swaps the border colour instead of stacking it over the idle one', () => {
    const { rerender } = render(<Input label="Amount" />)
    const input = screen.getByLabelText<HTMLInputElement>('Amount')
    expect(input.classList.contains('ring-field-border')).toBe(true)
    expect(input.classList.contains('ring-danger')).toBe(false)

    rerender(<Input label="Amount" invalid />)
    expect(input.classList.contains('ring-danger')).toBe(true)
    expect(input.classList.contains('ring-field-border')).toBe(false)
  })

  it('swaps the hint colour the same way', () => {
    const { rerender } = render(<Input label="Amount" hint="At most two decimals." />)
    const hintEl = screen.getByText('At most two decimals.')
    expect(hintEl.classList.contains('text-text-subtle')).toBe(true)

    rerender(<Input label="Amount" hint="At most two decimals." invalid />)
    expect(hintEl.classList.contains('text-danger')).toBe(true)
    expect(hintEl.classList.contains('text-text-subtle')).toBe(false)
  })

  it('on the gradient, uses only the on-dark surface, ring and hint colours', () => {
    const { rerender } = render(<Input label="Username" hint="3 to 50 characters." tone="onDark" />)
    const input = screen.getByLabelText<HTMLInputElement>('Username')
    const hintEl = screen.getByText('3 to 50 characters.')

    expect(input.classList.contains('text-on-dark')).toBe(true)
    expect(input.classList.contains('ring-on-dark/50')).toBe(true)
    for (const light of ['bg-surface', 'text-text', 'ring-field-border']) {
      expect(input.classList.contains(light)).toBe(false)
    }
    expect(hintEl.classList.contains('text-on-dark-muted')).toBe(true)
    expect(screen.getByText('Username').classList.contains('text-on-dark')).toBe(true)

    rerender(<Input label="Username" hint="Too short." tone="onDark" invalid />)
    expect(input.classList.contains('ring-danger-on-dark')).toBe(true)
    expect(input.classList.contains('ring-on-dark/50')).toBe(false)
    expect(screen.getByText('Too short.').classList.contains('text-danger-on-dark')).toBe(true)
  })

  // In Tailwind v4 `focus:outline-none` sets the outline style the focus-visible
  // ring inherits, which removed every field's keyboard ring once already.
  it('keeps the keyboard focus ring', () => {
    render(<Input label="Amount" />)
    const input = screen.getByLabelText('Amount')
    expect(input.classList.contains('focus-visible:outline-2')).toBe(true)
    expect(input.classList.contains('focus:outline-none')).toBe(false)
  })

  it('renders a suffix control and leaves room for it', () => {
    render(<Input label="Code" suffix={<button type="button">Paste</button>} />)
    expect(screen.getByRole('button', { name: 'Paste' })).toBeInTheDocument()
    expect(screen.getByLabelText('Code').classList.contains('pr-20')).toBe(true)
  })
})
