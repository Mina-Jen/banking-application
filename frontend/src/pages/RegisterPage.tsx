import { useState, type FocusEvent, type FormEvent } from 'react'
import { Link, Navigate, useNavigate } from 'react-router-dom'
import { Alert } from '../components/Alert'
import { AuthLayout } from '../components/AuthLayout'
import { Button } from '../components/Button'
import { Input } from '../components/Input'
import { PasswordInput } from '../components/PasswordInput'
import { linkOnDark } from '../components/ui'
import { errorMessage } from '../api/errors'
import { useAuth } from '../auth/AuthContext'
import {
  MAX_PASSWORD_LENGTH,
  MIN_PASSWORD_LENGTH,
  normalizeUsername,
  passwordError,
  usernameError,
} from '../lib/validation'

type Field = 'username' | 'password' | 'confirm-password'

const FIELDS: readonly Field[] = ['username', 'password', 'confirm-password']

/**
 * Registration.
 *
 * The role is never sent: POST /api/auth/register always creates a CUSTOMER,
 * and an ADMIN can only be created by the operator through the ADMIN_PASSWORD
 * environment variable at backend startup. There is no way to ask for admin
 * from a browser, which is the point.
 */
export function RegisterPage() {
  const { isAuthenticated, signUp } = useAuth()
  const navigate = useNavigate()

  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [confirm, setConfirm] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [submitted, setSubmitted] = useState(false)
  // A field is judged once the user has typed in it and moved on; after that it
  // re-checks on every keystroke, so a fixed mistake clears immediately.
  const [touched, setTouched] = useState<Record<Field, boolean>>({
    username: false,
    password: false,
    'confirm-password': false,
  })
  const [error, setError] = useState<string | null>(null)

  if (isAuthenticated) {
    return <Navigate to="/" replace />
  }

  const problems: Record<Field, string | null> = {
    username: usernameError(username),
    password: passwordError(password),
    'confirm-password':
      confirm === '' ? 'Repeat the password.' : confirm === password ? null : 'The two passwords differ.',
  }
  const shown = (field: Field) => (submitted || touched[field]) && problems[field] !== null

  function onBlur(event: FocusEvent<HTMLInputElement>) {
    const field = event.target.name as Field
    if (event.target.value !== '') {
      setTouched((current) => ({ ...current, [field]: true }))
    }
  }

  async function onSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (submitting) {
      return
    }

    setSubmitted(true)
    const firstInvalid = FIELDS.find((field) => problems[field] !== null)
    if (firstInvalid) {
      ;(event.currentTarget.elements.namedItem(firstInvalid) as HTMLInputElement | null)?.focus()
      return
    }

    setError(null)
    setSubmitting(true)
    try {
      const result = await signUp(normalizeUsername(username), password)
      if (result.outcome === 'signed-in') {
        navigate('/', { replace: true })
      } else {
        // The login exists but signing into it right away failed. Going to the
        // login page is correct; re-registering would return 409.
        navigate('/login', {
          replace: true,
          state: { notice: 'Your account was created. Please sign in.' },
        })
      }
    } catch (cause) {
      // 409 username taken, 422 format rejected by the backend.
      setError(errorMessage(cause))
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <AuthLayout
      eyebrow="Create your login"
      headline="BUKAS NA ABOT-KAMAY."
      subtitle="Open an account in minutes — no branch visit, no paperwork."
      footer={
        <>
          Already registered?{' '}
          <Link to="/login" className={linkOnDark}>
            Sign in
          </Link>
        </>
      }
    >
      <form onSubmit={onSubmit} noValidate aria-busy={submitting || undefined} className="space-y-5">
        {error ? <Alert tone="error">{error}</Alert> : null}

        <Input
          tone="onDark"
          label="Username"
          name="username"
          value={username}
          autoComplete="username"
          autoCapitalize="none"
          spellCheck={false}
          autoFocus
          required
          onChange={(event) => setUsername(event.target.value)}
          onBlur={onBlur}
          invalid={shown('username')}
          hint={
            shown('username')
              ? problems.username
              : '3 to 50 characters: lowercase letters, digits, dot, underscore or hyphen.'
          }
        />

        <PasswordInput
          tone="onDark"
          label="Password"
          name="password"
          value={password}
          autoComplete="new-password"
          required
          onChange={(event) => setPassword(event.target.value)}
          onBlur={onBlur}
          invalid={shown('password')}
          hint={
            shown('password')
              ? problems.password
              : `${MIN_PASSWORD_LENGTH} to ${MAX_PASSWORD_LENGTH} characters.`
          }
        />

        <PasswordInput
          tone="onDark"
          label="Repeat password"
          name="confirm-password"
          value={confirm}
          autoComplete="new-password"
          required
          onChange={(event) => setConfirm(event.target.value)}
          onBlur={onBlur}
          invalid={shown('confirm-password')}
          hint={shown('confirm-password') ? problems['confirm-password'] : undefined}
        />

        <Button type="submit" variant="onDark" loading={submitting} className="w-full">
          {submitting ? 'Creating…' : 'Register'}
        </Button>
      </form>
    </AuthLayout>
  )
}
