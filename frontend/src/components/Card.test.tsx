import { cleanup, render, screen } from '@testing-library/react'
import { afterEach, describe, expect, it } from 'vitest'
import { Card } from './Card'

afterEach(() => cleanup())

describe('Card', () => {
  it('names the region with its own heading', () => {
    render(
      <Card title="Recent activity">
        <p>No transactions yet.</p>
      </Card>,
    )

    const region = screen.getByRole('region', { name: 'Recent activity' })
    expect(region).toContainElement(screen.getByRole('heading', { level: 2, name: 'Recent activity' }))
    expect(screen.getByText('No transactions yet.')).toBeInTheDocument()
  })

  it('stays an ordinary box when it has no title, instead of an unnamed region', () => {
    render(
      <Card>
        <p>Utilities</p>
      </Card>,
    )

    expect(screen.queryByRole('region')).not.toBeInTheDocument()
    expect(screen.queryByRole('heading')).not.toBeInTheDocument()
  })

  it('shows a description beside the title', () => {
    render(<Card title="Balance" description="All accounts, updated on load." />)

    expect(screen.getByText('All accounts, updated on load.')).toBeInTheDocument()
  })

  it('puts the actions in the header row where they belong', () => {
    render(
      <Card title="Recent activity" actions={<button type="button">Refresh</button>}>
        <p>Rows</p>
      </Card>,
    )

    const region = screen.getByRole('region', { name: 'Recent activity' })
    const button = screen.getByRole('button', { name: 'Refresh' })
    expect(region).toContainElement(button)
    // Before the content, so tabbing through the card reaches Refresh first.
    const focusables = Array.from(region.querySelectorAll('button'))
    expect(focusables[0]).toBe(button)
  })

  it('raises the surface without changing its meaning', () => {
    const { rerender } = render(<Card title="A" />)
    expect(
      screen.getByRole('region', { name: 'A' }).classList.contains('shadow-elevate-1'),
    ).toBe(true)

    rerender(
      <Card title="A" level="raised">
        <p>B</p>
      </Card>,
    )
    const raised = screen.getByRole('region', { name: 'A' })
    expect(raised.classList.contains('shadow-elevate-2')).toBe(true)
    expect(raised.classList.contains('shadow-elevate-1')).toBe(false)
  })
})
