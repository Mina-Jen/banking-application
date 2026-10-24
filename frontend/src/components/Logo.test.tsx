import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { Logo, LogoWordmark } from './Logo'

describe('Logo', () => {
  it('draws the sunrise as paths and hides it from assistive tech', () => {
    const { container } = render(<Logo />)
    const svg = container.querySelector('svg')

    expect(svg).toHaveAttribute('aria-hidden', 'true')
    expect(svg?.querySelectorAll('path')).toHaveLength(3)
    expect(svg?.querySelector('path')).toHaveAttribute('stroke', 'currentColor')
  })

  it('renders the wordmark as text and colours it for the surface', () => {
    const { rerender } = render(<LogoWordmark variant="dark" />)
    expect(screen.getByText('SINAG')).toHaveClass('text-on-dark')

    rerender(<LogoWordmark variant="light" />)
    expect(screen.getByText('SINAG')).toHaveClass('text-text')
  })
})
