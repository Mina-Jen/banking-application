import { act, render, screen } from '@testing-library/react'
import { MemoryRouter, Outlet, Route, Routes, useLocation } from 'react-router-dom'
import { beforeEach, describe, expect, it } from 'vitest'
import { AuthProvider } from './AuthContext'
import { RequireAuth } from './RequireAuth'
import { sessionStore } from './tokenStorage'
import { fakeAccessToken } from '../test/helpers'

/**
 * Stands in for LoginPage. The redirect target is rendered as its own element
 * so `getByText('login page')` can match exactly — Testing Library compares the
 * full text of an element, so concatenating the two would break every query.
 */
function LoginStub() {
  const location = useLocation()
  const from = (location.state as { from?: string } | null)?.from
  return (
    <div>
      <span>login page</span>
      {from ? <span data-testid="redirect-target">{from}</span> : null}
    </div>
  )
}

function renderAt(path: string) {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <AuthProvider>
        <Routes>
          <Route path="/login" element={<LoginStub />} />
          <Route
            element={
              <RequireAuth>
                <Outlet />
              </RequireAuth>
            }
          >
            <Route index element={<div>dashboard</div>} />
            <Route path="accounts/:accountNumber" element={<div>account page</div>} />
          </Route>
        </Routes>
      </AuthProvider>
    </MemoryRouter>,
  )
}

beforeEach(() => {
  localStorage.clear()
  sessionStore.clear()
})

describe('RequireAuth', () => {
  it('sends a signed-out visitor from the dashboard to the login page', () => {
    renderAt('/')

    expect(screen.getByText('login page')).toBeInTheDocument()
    expect(screen.queryByText('dashboard')).not.toBeInTheDocument()
  })

  it('sends a signed-out visitor from a deep page to the login page', () => {
    renderAt('/accounts/100001')

    expect(screen.getByText('login page')).toBeInTheDocument()
    expect(screen.queryByText('account page')).not.toBeInTheDocument()
  })

  it('remembers the deep page so login can return the user to it', () => {
    renderAt('/accounts/100001')

    expect(screen.getByTestId('redirect-target')).toHaveTextContent('/accounts/100001')
  })

  it('renders the protected page when a session exists', () => {
    sessionStore.save({
      accessToken: fakeAccessToken({ username: 'alice' }),
      refreshToken: 'refresh-1',
    })

    renderAt('/accounts/100001')

    expect(screen.getByText('account page')).toBeInTheDocument()
    expect(screen.queryByText('login page')).not.toBeInTheDocument()
  })

  /**
   * Proves the guard reacts to the store rather than reading it once. This is
   * the path taken when the API client's silent refresh fails and clears the
   * session mid-page: the user is sent to /login without a reload.
   */
  it('redirects immediately when the session is cleared elsewhere', () => {
    sessionStore.save({
      accessToken: fakeAccessToken({ username: 'alice' }),
      refreshToken: 'refresh-1',
    })

    renderAt('/')
    expect(screen.getByText('dashboard')).toBeInTheDocument()

    act(() => {
      sessionStore.clear()
    })

    expect(screen.getByText('login page')).toBeInTheDocument()
    expect(screen.queryByText('dashboard')).not.toBeInTheDocument()
  })
})
