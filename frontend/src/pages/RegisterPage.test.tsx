import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { RegisterPage } from './RegisterPage'
import { ApiError } from '../api/errors'

type SignUpResult = { outcome: 'signed-in' } | { outcome: 'registered-only' }

const auth = vi.hoisted(() => ({
  isAuthenticated: false,
  signUp: vi.fn<(username: string, password: string) => Promise<SignUpResult>>(),
}))

vi.mock('../auth/AuthContext', () => ({ useAuth: () => auth }))

function LoginStandIn() {
  const notice = (useLocation().state as { notice?: string } | null)?.notice
  return <p>Login page: {notice}</p>
}

function renderRegister() {
  render(
    <MemoryRouter initialEntries={['/register']}>
      <Routes>
        <Route path="/register" element={<RegisterPage />} />
        <Route path="/" element={<p>Dashboard</p>} />
        <Route path="/login" element={<LoginStandIn />} />
      </Routes>
    </MemoryRouter>,
  )
  return {
    username: screen.getByLabelText('Username'),
    password: screen.getByLabelText('Password'),
    confirm: screen.getByLabelText('Repeat password'),
    submit: () => screen.getByRole('button', { name: /^(Register|Creating…)$/ }),
  }
}

describe('RegisterPage', () => {
  beforeEach(() => {
    auth.isAuthenticated = false
    auth.signUp.mockReset()
  })

  it('keeps the names the browser tests rely on', () => {
    renderRegister()
    expect(screen.getByRole('heading', { name: 'Create your login' })).toBeInTheDocument()
    // Playwright matches the button name as a substring: only one may contain "Register".
    expect(screen.getAllByRole('button', { name: /register/i })).toHaveLength(1)
    expect(screen.getByRole('link', { name: 'Sign in' })).toHaveAttribute('href', '/login')
    expect(screen.getAllByLabelText('Password')).toHaveLength(1)
  })

  it('shows the format guidance before anything is typed', () => {
    const { username, password } = renderRegister()
    expect(username).toHaveAccessibleDescription(
      '3 to 50 characters: lowercase letters, digits, dot, underscore or hyphen.',
    )
    expect(password).toHaveAccessibleDescription('8 to 72 characters.')
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
  })

  it('flags a field once the user types in it and moves on', async () => {
    const user = userEvent.setup()
    const { username, password } = renderRegister()

    await user.type(username, 'ab')
    expect(username).not.toHaveAttribute('aria-invalid')

    await user.click(password)
    expect(username).toHaveAttribute('aria-invalid', 'true')
    expect(username).toHaveAccessibleDescription('Username must be at least 3 characters.')
  })

  it('re-checks a flagged field on every keystroke, so a fix clears it at once', async () => {
    const user = userEvent.setup()
    const { username, password } = renderRegister()

    await user.type(username, 'ab')
    await user.click(password)
    await user.type(username, 'c')

    expect(username).not.toHaveAttribute('aria-invalid')
    expect(username).toHaveAccessibleDescription(
      '3 to 50 characters: lowercase letters, digits, dot, underscore or hyphen.',
    )
  })

  it('does not judge a field the user has only passed through', async () => {
    const user = userEvent.setup()
    renderRegister()

    await user.tab()
    await user.tab()
    await user.tab()
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
  })

  it('flags mismatched passwords after leaving the repeat field', async () => {
    const user = userEvent.setup()
    const { password, confirm, username } = renderRegister()

    await user.type(password, 'long-enough-1')
    await user.type(confirm, 'long-enough-2')
    await user.click(username)

    expect(confirm).toHaveAccessibleDescription('The two passwords differ.')
  })

  it('on an invalid submit flags every field, focuses the first, and sends nothing', async () => {
    const user = userEvent.setup()
    const { username, password, confirm, submit } = renderRegister()

    await user.type(password, 'long-enough-1')
    await user.click(submit())

    expect(auth.signUp).not.toHaveBeenCalled()
    expect(username).toHaveAccessibleDescription('Choose a username.')
    expect(confirm).toHaveAccessibleDescription('Repeat the password.')
    expect(password).not.toHaveAttribute('aria-invalid')
    expect(username).toHaveFocus()
  })

  it('registers with the normalised username and lands on the dashboard', async () => {
    const user = userEvent.setup()
    auth.signUp.mockResolvedValue({ outcome: 'signed-in' })
    const { username, password, confirm, submit } = renderRegister()

    await user.type(username, ' New.User ')
    await user.type(password, 'long-enough-1')
    await user.type(confirm, 'long-enough-1')
    await user.click(submit())

    expect(auth.signUp).toHaveBeenCalledWith('new.user', 'long-enough-1')
    expect(await screen.findByText('Dashboard')).toBeInTheDocument()
  })

  it('shows a busy "Creating…" button while the request runs', async () => {
    const user = userEvent.setup()
    auth.signUp.mockReturnValue(new Promise(() => {}))
    const { username, password, confirm, submit } = renderRegister()

    await user.type(username, 'new.user')
    await user.type(password, 'long-enough-1')
    await user.type(confirm, 'long-enough-1')
    await user.click(submit())

    const busy = screen.getByRole('button', { name: 'Creating…' })
    expect(busy).toBeDisabled()
    expect(busy).toHaveAttribute('aria-busy', 'true')
  })

  it('sends a created-but-not-signed-in user to the login page with a notice', async () => {
    const user = userEvent.setup()
    auth.signUp.mockResolvedValue({ outcome: 'registered-only' })
    const { username, password, confirm, submit } = renderRegister()

    await user.type(username, 'new.user')
    await user.type(password, 'long-enough-1')
    await user.type(confirm, 'long-enough-1')
    await user.click(submit())

    expect(
      await screen.findByText('Login page: Your account was created. Please sign in.'),
    ).toBeInTheDocument()
  })

  it('shows the backend message when the username is taken', async () => {
    const user = userEvent.setup()
    auth.signUp.mockRejectedValue(new ApiError(409, 'Conflict', 'Username is already taken.'))
    const { username, password, confirm, submit } = renderRegister()

    await user.type(username, 'alice')
    await user.type(password, 'long-enough-1')
    await user.type(confirm, 'long-enough-1')
    await user.click(submit())

    expect(await screen.findByRole('alert')).toHaveTextContent('Username is already taken.')
  })

  it('lets each password field be revealed on its own', async () => {
    const user = userEvent.setup()
    const { password, confirm } = renderRegister()
    const [showPassword] = screen.getAllByRole('button', { name: 'Show password' })

    await user.click(showPassword)
    expect(password).toHaveAttribute('type', 'text')
    expect(confirm).toHaveAttribute('type', 'password')
  })
})
