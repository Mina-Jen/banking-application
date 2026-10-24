import { act, cleanup, fireEvent, render, screen } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { MemoryRouter } from 'react-router-dom'
import { GradientAccountCard } from './GradientAccountCard'

/**
 * jsdom ships no `navigator.clipboard`, so every clipboard path has to be
 * installed by hand and removed afterwards — otherwise the "clipboard is
 * missing" test would see the stub left behind by the one before it.
 */
function stubClipboard(writeText: (text: string) => Promise<void>) {
  const clipboard = { writeText: vi.fn(writeText) }
  Object.defineProperty(navigator, 'clipboard', { configurable: true, value: clipboard })
  return clipboard
}

const NUMBER = '0001234821'

afterEach(() => {
  cleanup()
  vi.useRealTimers()
  Object.defineProperty(navigator, 'clipboard', { configurable: true, value: undefined })
})

function renderCard(props: Partial<React.ComponentProps<typeof GradientAccountCard>> = {}) {
  render(
    <GradientAccountCard
      accountNumber={NUMBER}
      accountName="Alice Reyes"
      balance="7000.00"
      {...props}
    />,
  )
}

describe('GradientAccountCard', () => {
  it('shows only the last four digits on the card face', () => {
    renderCard()

    // The masked groups are a dimmed span, so the printed string only exists as
    // the element's whole text content.
    const masked = screen.getByText('4821')
    expect(masked).toHaveTextContent('•••• 4821')
    expect(screen.queryByText(NUMBER)).not.toBeInTheDocument()
  })

  it('names the region with the mask, not the number, so a screen reader does not read it aloud either', () => {
    renderCard()

    expect(screen.getByRole('region', { name: 'Alice Reyes, Debit account ending 4821' })).toBeInTheDocument()
  })

  it('copies the full number, not what is printed', async () => {
    const clipboard = stubClipboard(async () => {})
    renderCard()

    await act(async () => {
      fireEvent.click(screen.getByRole('button', { name: 'Copy number' }))
    })

    expect(clipboard.writeText).toHaveBeenCalledWith(NUMBER)
  })

  it('says so out loud as well as on the button', async () => {
    stubClipboard(async () => {})
    renderCard()

    await act(async () => {
      fireEvent.click(screen.getByRole('button', { name: 'Copy number' }))
    })

    expect(screen.getByRole('button', { name: 'Copied' })).toBeInTheDocument()
    const announcement = screen.getByText('Account number copied.')
    expect(announcement).toHaveAttribute('aria-live', 'polite')
  })

  it('lets the confirmation expire, so the label is true for the next press', async () => {
    vi.useFakeTimers()
    stubClipboard(async () => {})
    renderCard()

    await act(async () => {
      fireEvent.click(screen.getByRole('button', { name: 'Copy number' }))
    })
    expect(screen.getByRole('button', { name: 'Copied' })).toBeInTheDocument()

    await act(async () => {
      await vi.advanceTimersByTimeAsync(2400)
    })
    expect(screen.getByRole('button', { name: 'Copy number' })).toBeInTheDocument()
    expect(screen.queryByText('Account number copied.')).not.toBeInTheDocument()
  })

  it('reveals the number when the browser has no clipboard at all', async () => {
    renderCard()

    await act(async () => {
      fireEvent.click(screen.getByRole('button', { name: 'Copy number' }))
    })

    expect(screen.getByText(NUMBER)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Number shown' })).toBeInTheDocument()
    expect(screen.getByText('Copying is unavailable, so the full number is shown instead.')).toBeInTheDocument()
  })

  it('treats a rejected copy the same way — an insecure origin and a denied permission look identical', async () => {
    stubClipboard(() => Promise.reject(new Error('NotAllowedError')))
    renderCard()

    await act(async () => {
      fireEvent.click(screen.getByRole('button', { name: 'Copy number' }))
    })

    expect(screen.getByText(NUMBER)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Number shown' })).toBeInTheDocument()
  })

  it('formats the balance as money, with the digits lined up', () => {
    renderCard()

    const balance = screen.getByText('₱7,000.00')
    expect(balance).toBeInTheDocument()
    expect(balance.classList.contains('tabular-nums')).toBe(true)
  })

  it('holds the place with an em dash while the balance loads', () => {
    renderCard({ balance: undefined })

    expect(screen.getByText('—')).toBeInTheDocument()
  })

  it('uses the product line in the corner and the label', () => {
    renderCard({ kind: 'Savings' })

    expect(screen.getByText('Sinag · Savings')).toBeInTheDocument()
    expect(screen.getByRole('region', { name: 'Alice Reyes, Savings account ending 4821' })).toBeInTheDocument()
  })

  it('offers a separate Manage link without making the copy button part of a link', () => {
    render(
      <MemoryRouter>
        <GradientAccountCard
          accountNumber={NUMBER}
          accountName="Alice Reyes"
          balance="7000.00"
          manageTo={`/accounts/${NUMBER}`}
        />
      </MemoryRouter>,
    )

    const manage = screen.getByRole('link', { name: 'Manage' })
    expect(manage).toHaveAttribute('href', `/accounts/${NUMBER}`)
    expect(manage).not.toContainElement(screen.getByRole('button', { name: 'Copy number' }))
  })
})
