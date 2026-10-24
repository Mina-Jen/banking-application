import { cleanup, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { ActionTile } from './ActionTile'
import { IconDeposit } from './Icon'

afterEach(() => cleanup())

function renderTile(props: Partial<React.ComponentProps<typeof ActionTile>> = {}) {
  const view = render(
    <MemoryRouter>
      <ActionTile icon={<IconDeposit />} label="Deposit" {...props} />
    </MemoryRouter>,
  )
  return view
}

describe('ActionTile', () => {
  it('navigates as a link when it is given a destination', () => {
    renderTile({ to: '/accounts/1/deposit' })

    // A link, not a div with a click handler: it can be opened in a new tab and
    // keyboard users get the same affordance.
    expect(screen.getByRole('link', { name: 'Deposit' })).toHaveAttribute(
      'href',
      '/accounts/1/deposit',
    )
  })

  it('acts as a button when it is given a handler', async () => {
    const onClick = vi.fn()
    renderTile({ onClick })

    const tile = screen.getByRole('button', { name: 'Deposit' })
    expect(tile).toHaveAttribute('type', 'button')

    await userEvent.click(tile)
    expect(onClick).toHaveBeenCalledTimes(1)
  })

  it('stays inert when disabled', async () => {
    const onClick = vi.fn()
    renderTile({ onClick, disabled: true })

    const tile = screen.getByRole('button', { name: 'Deposit' })
    expect(tile).toBeDisabled()

    await userEvent.click(tile)
    expect(onClick).not.toHaveBeenCalled()
  })

  it('is named by its label alone, with the hint as the description', () => {
    const { container } = renderTile({ hint: 'Into savings' })

    const badge = container.querySelector('[aria-hidden="true"]')
    expect(badge).toContainElement(container.querySelector('svg'))

    const tile = screen.getByRole('button', { name: 'Deposit' })
    const hintId = tile.getAttribute('aria-describedby')
    expect(hintId).not.toBeNull()
    expect(screen.getByText('Into savings')).toHaveAttribute('id', hintId as string)
  })

  it('prefers the destination when both are given, so a tile never does two things', () => {
    const onClick = vi.fn()
    renderTile({ to: '/deposit', onClick })

    expect(screen.getByRole('link')).toBeInTheDocument()
    expect(screen.queryByRole('button')).not.toBeInTheDocument()
  })

  it('darkens the tile without changing what it is', () => {
    renderTile({ tone: 'onDark', to: '/transfer' })

    const tile = screen.getByRole('link', { name: 'Deposit' })
    expect(tile.classList.contains('bg-depth-scrim/45')).toBe(true)
  })
})
