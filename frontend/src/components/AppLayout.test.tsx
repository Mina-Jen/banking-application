import { render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { AppLayout } from './AppLayout'

const auth = vi.hoisted(() => ({ username: 'alice', role: 'CUSTOMER', isAdmin: false }))

vi.mock('../auth/AuthContext', () => ({
  useAuth: () => ({ ...auth, signOut: vi.fn() }),
}))

function renderAt(path: string) {
  render(
    <MemoryRouter initialEntries={[path]}>
      <Routes>
        <Route element={<AppLayout />}>
          <Route path="/" element={<p>Dashboard</p>} />
          <Route path="/accounts/:accountNumber" element={<p>Account detail</p>} />
          <Route path="/review" element={<p>Review queue</p>} />
        </Route>
      </Routes>
    </MemoryRouter>,
  )
}

beforeEach(() => {
  auth.role = 'CUSTOMER'
  auth.isAdmin = false
})

describe('AppLayout navigation', () => {
  it.each(['/', '/accounts/200001'])('marks Accounts active at %s', (path) => {
    renderAt(path)

    const accounts = screen.getByRole('link', { name: 'Accounts' })
    expect(accounts).toHaveAttribute('aria-current', 'page')
    expect(accounts.classList.contains('bg-action-soft')).toBe(true)
  })

  it('gives an admin the overview and review queue instead of Accounts', () => {
    auth.role = 'ADMIN'
    auth.isAdmin = true
    renderAt('/')

    expect(screen.getByRole('link', { name: 'Overview' })).toHaveAttribute('aria-current', 'page')
    expect(screen.getByRole('link', { name: 'Review queue' })).toBeInTheDocument()
    expect(screen.queryByRole('link', { name: 'Accounts' })).not.toBeInTheDocument()
    expect(screen.getByText('Administrator')).toBeInTheDocument()
  })

  it('marks the review queue active on its own page', () => {
    auth.role = 'ADMIN'
    auth.isAdmin = true
    renderAt('/review')

    expect(screen.getByRole('link', { name: 'Review queue' })).toHaveAttribute('aria-current', 'page')
    expect(screen.getByRole('link', { name: 'Overview' })).not.toHaveAttribute('aria-current')
  })
})
