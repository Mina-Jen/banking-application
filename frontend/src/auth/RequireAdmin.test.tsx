import { render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { describe, expect, it, vi } from 'vitest'
import { RequireAdmin } from './RequireAdmin'

const auth = vi.hoisted(() => ({ isAdmin: false }))

vi.mock('./AuthContext', () => ({ useAuth: () => auth }))

function renderGuard() {
  render(
    <MemoryRouter initialEntries={['/review']}>
      <Routes>
        <Route path="/" element={<p>Customer home</p>} />
        <Route
          path="/review"
          element={
            <RequireAdmin>
              <p>Admin queue</p>
            </RequireAdmin>
          }
        />
      </Routes>
    </MemoryRouter>,
  )
}

describe('RequireAdmin', () => {
  it('sends a customer back to their own dashboard', () => {
    auth.isAdmin = false
    renderGuard()

    expect(screen.getByText('Customer home')).toBeInTheDocument()
    expect(screen.queryByText('Admin queue')).not.toBeInTheDocument()
  })

  it('lets an admin through', () => {
    auth.isAdmin = true
    renderGuard()

    expect(screen.getByText('Admin queue')).toBeInTheDocument()
  })
})
