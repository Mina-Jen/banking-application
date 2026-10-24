import { cleanup, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { Button } from './Button'
import { buttonClasses } from './ui'

afterEach(() => cleanup())

describe('Button', () => {
  it('is type="button" unless told otherwise, so a stray press cannot submit the form around it', () => {
    render(<Button>Deposit</Button>)

    expect(screen.getByRole('button', { name: 'Deposit' })).toHaveAttribute('type', 'button')
  })

  it('keeps an explicit submit type', () => {
    render(<Button type="submit">Sign in</Button>)

    expect(screen.getByRole('button', { name: 'Sign in' })).toHaveAttribute('type', 'submit')
  })

  it('disables itself and flags busy while loading, and swallows the click', async () => {
    const onClick = vi.fn()
    render(
      <Button loading onClick={onClick}>
        Depositing…
      </Button>,
    )

    const button = screen.getByRole('button', { name: 'Depositing…' })
    expect(button).toBeDisabled()
    expect(button).toHaveAttribute('aria-busy', 'true')

    await userEvent.click(button)
    expect(onClick).not.toHaveBeenCalled()
  })

  it('keeps the words as the only thing a screen reader reads', () => {
    const { container } = render(
      <Button loading>Working</Button>,
    )

    const spinner = container.querySelector('svg')
    expect(spinner).not.toBeNull()
    expect(spinner).toHaveAttribute('aria-hidden', 'true')
    expect(screen.getByRole('button', { name: 'Working' })).toBeInTheDocument()
  })

  it('clears the busy state and becomes clickable again when loading ends', () => {
    const { rerender } = render(<Button loading>Save</Button>)
    rerender(<Button>Save</Button>)

    const button = screen.getByRole('button', { name: 'Save' })
    expect(button).toBeEnabled()
    expect(button).not.toHaveAttribute('aria-busy')
  })

  it('disables on its own, without claiming anything is busy', () => {
    render(
      <Button disabled>Transfer</Button>,
    )

    const button = screen.getByRole('button', { name: 'Transfer' })
    expect(button).toBeDisabled()
    expect(button).not.toHaveAttribute('aria-busy')
  })

  it('fires onClick when it is idle', async () => {
    const onClick = vi.fn()
    render(<Button onClick={onClick}>Withdraw</Button>)

    await userEvent.click(screen.getByRole('button', { name: 'Withdraw' }))
    expect(onClick).toHaveBeenCalledTimes(1)
  })

  it('paints a link with exactly the classes a button of that variant uses', () => {
    render(
      <Button variant="danger">Withdraw</Button>,
    )

    // The shared source of truth: `buttonClasses` is what a <Link> gets, so the
    // two cannot drift into different-looking "primary" actions.
    expect(screen.getByRole('button', { name: 'Withdraw' }).className).toBe(
      buttonClasses('danger'),
    )
  })

  it('shrinks to the small size instead of restyling it', () => {
    render(
      <Button size="sm">Refresh</Button>,
    )

    const button = screen.getByRole('button', { name: 'Refresh' })
    expect(button.classList.contains('min-h-9')).toBe(true)
    expect(button.classList.contains('text-xs')).toBe(true)
  })

  it('passes an extra class through without replacing the variant', () => {
    render(
      <Button className="w-full">Open an account</Button>,
    )

    const button = screen.getByRole('button', { name: 'Open an account' })
    expect(button.classList.contains('w-full')).toBe(true)
    expect(button.className.startsWith(buttonClasses('primary'))).toBe(true)
  })

  // A busy button is working, not unavailable: only a plain disabled one fades.
  it('fades only when disabled without being busy', () => {
    const classes = buttonClasses('onDark').split(' ')
    expect(classes).toContain('disabled:not-aria-busy:opacity-50')
    expect(classes).toContain('aria-busy:cursor-progress')
    expect(classes).not.toContain('disabled:opacity-50')
  })
})
