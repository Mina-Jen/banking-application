import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { LoginPage } from './LoginPage'
import { ApiError } from '../api/errors'

const auth = vi.hoisted(() => ({
  isAuthenticated: false,
  signIn: vi.fn<(username: string, password: string) => Promise<void>>(),
}))

vi.mock('../auth/AuthContext', () => ({ useAuth: () => auth }))

function renderLogin(state?: { from?: string; notice?: string }) {
  render(
    <MemoryRouter initialEntries={[{ pathname: '/login', state }]}>
      <Routes>
        <Route path="/login" element={<LoginPage />} />
        <Route path="/" element={<p>Dashboard</p>} />
        <Route path="/accounts/123" element={<p>Account 123</p>} />
        <Route path="/register" element={<p>Register page</p>} />
      </Routes>
    </MemoryRouter>,
  )
  return {
    username: screen.getByLabelText('Username'),
    password: screen.getByLabelText('Password') as HTMLInputElement,
    submit: () => screen.getByRole('button', { name: /^Sign(ing)? in/ }),
  }
}

function deferred() {
  let resolve!: () => void
  let reject!: (reason: unknown) => void
  const promise = new Promise<void>((res, rej) => {
    resolve = res
    reject = rej
  })
  return { promise, resolve, reject }
}

describe('LoginPage', () => {
  beforeEach(() => {
    auth.isAuthenticated = false
    auth.signIn.mockReset()
  })

  it('keeps the names the browser tests rely on', () => {
    renderLogin()
    expect(screen.getByRole('heading', { name: 'Sign in' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Sign in' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Register' })).toHaveAttribute('href', '/register')
    expect(screen.getAllByRole('heading')).toHaveLength(1)
  })

  it('flags both empty fields on submit, focuses the first, and sends nothing', async () => {
    const user = userEvent.setup()
    const { username, password, submit } = renderLogin()

    await user.click(submit())

    expect(auth.signIn).not.toHaveBeenCalled()
    expect(username).toHaveAccessibleDescription('Enter your username.')
    expect(password).toHaveAccessibleDescription('Enter your password.')
    expect(username).toHaveFocus()
  })

  it('does not judge a field the user has only passed through', async () => {
    const user = userEvent.setup()
    renderLogin()

    await user.tab()
    await user.tab()
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
  })

  it('clears an inline error as soon as the field is filled', async () => {
    const user = userEvent.setup()
    const { username, submit } = renderLogin()

    await user.click(submit())
    await user.type(username, 'alice')
    expect(username).not.toHaveAttribute('aria-invalid')
  })

  it('signs in with the trimmed username and returns to where the user was caught', async () => {
    const user = userEvent.setup()
    auth.signIn.mockResolvedValue()
    const { username, password, submit } = renderLogin({ from: '/accounts/123' })

    await user.type(username, '  alice ')
    await user.type(password, 'alice-pass-123')
    await user.click(submit())

    expect(auth.signIn).toHaveBeenCalledWith('alice', 'alice-pass-123')
    expect(await screen.findByText('Account 123')).toBeInTheDocument()
  })

  it('shows a busy "Signing in…" button while the request runs', async () => {
    const user = userEvent.setup()
    const pending = deferred()
    auth.signIn.mockReturnValue(pending.promise)
    const { username, password, submit } = renderLogin()

    await user.type(username, 'alice')
    await user.type(password, 'alice-pass-123')
    await user.click(submit())

    const busy = screen.getByRole('button', { name: 'Signing in…' })
    expect(busy).toBeDisabled()
    expect(busy).toHaveAttribute('aria-busy', 'true')

    pending.resolve()
    expect(await screen.findByText('Dashboard')).toBeInTheDocument()
  })

  it('reports a rejected login, clears the password, and does not flag the cleared field', async () => {
    const user = userEvent.setup()
    auth.signIn.mockRejectedValue(new ApiError(401, 'Unauthorized', 'Invalid username or password.'))
    const { username, password, submit } = renderLogin()

    await user.type(username, 'alice')
    await user.type(password, 'wrong-password')
    await user.click(submit())

    expect(await screen.findByRole('alert')).toHaveTextContent('Invalid username or password.')
    expect(password.value).toBe('')
    expect(password).not.toHaveAttribute('aria-invalid')
    expect(screen.getAllByRole('alert')).toHaveLength(1)
  })

  it('adds the wait time to a 429', async () => {
    const user = userEvent.setup()
    auth.signIn.mockRejectedValue(
      new ApiError(429, 'Too Many Requests', 'Too many login attempts.', 300),
    )
    const { username, password, submit } = renderLogin()

    await user.type(username, 'alice')
    await user.type(password, 'alice-pass-123')
    await user.click(submit())

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Too many login attempts. Try again in 5 minutes.',
    )
  })

  it('shows the notice handed over by the register page', () => {
    renderLogin({ notice: 'Your account was created. Please sign in.' })
    expect(screen.getByRole('status')).toHaveTextContent('Your account was created. Please sign in.')
  })

  it('lets the password be revealed', async () => {
    const user = userEvent.setup()
    const { password } = renderLogin()

    await user.click(screen.getByRole('button', { name: 'Show password' }))
    expect(password.type).toBe('text')
  })

  it('sends a signed-in user straight on', () => {
    auth.isAuthenticated = true
    renderLoginWithoutQueries()
    expect(screen.getByText('Dashboard')).toBeInTheDocument()
  })
})

function renderLoginWithoutQueries() {
  render(
    <MemoryRouter initialEntries={['/login']}>
      <Routes>
        <Route path="/login" element={<LoginPage />} />
        <Route path="/" element={<p>Dashboard</p>} />
      </Routes>
    </MemoryRouter>,
  )
}
