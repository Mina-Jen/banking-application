import { useState, type FocusEvent, type FormEvent } from 'react'
import { Link, Navigate, useLocation, useNavigate } from 'react-router-dom'
import { Alert } from '../components/Alert'
import { AuthLayout } from '../components/AuthLayout'
import { Button } from '../components/Button'
import { Input } from '../components/Input'
import { PasswordInput } from '../components/PasswordInput'
import { linkOnDark } from '../components/ui'
import { ApiError, errorMessage } from '../api/errors'
import { useAuth } from '../auth/AuthContext'

interface LocationState {
  /** Set by RequireAuth: where to go back to after signing in. */
  from?: string
  /** Set by the register page when the account was created but sign-in failed. */
  notice?: string
}

type Field = 'username' | 'password'

/** Turns the backend's Retry-After seconds into a human wait time. */
function retryAfterText(seconds: number): string {
  if (seconds <= 60) {
    return `Try again in ${seconds} seconds.`
  }
  return `Try again in ${Math.ceil(seconds / 60)} minutes.`
}

/**
 * Sign in. The only client check is that both fields are filled: a login that
 * predates today's username rules must still be able to sign in, so the format
 * is left to the backend, which answers every wrong pair with the same 401.
 */
export function LoginPage() {
  const { isAuthenticated, signIn } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const state = (location.state ?? {}) as LocationState

  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [submitted, setSubmitted] = useState(false)
  // A field is judged once the user has typed in it and moved on, never while
  // they are still on their first pass through the form.
  const [touched, setTouched] = useState<Record<Field, boolean>>({ username: false, password: false })
  const [error, setError] = useState<string | null>(null)

  // Already signed in: the login page has nothing to offer.
  if (isAuthenticated) {
    return <Navigate to={state.from ?? '/'} replace />
  }

  const problems: Record<Field, string | null> = {
    username: username.trim() === '' ? 'Enter your username.' : null,
    password: password === '' ? 'Enter your password.' : null,
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
    const firstInvalid = (['username', 'password'] as const).find((field) => problems[field] !== null)
    if (firstInvalid) {
      ;(event.currentTarget.elements.namedItem(firstInvalid) as HTMLInputElement | null)?.focus()
      return
    }

    setError(null)
    setSubmitting(true)
    try {
      await signIn(username.trim(), password)
      // signIn already stored the tokens, so the re-render above has happened;
      // send the user where RequireAuth caught them, else to the dashboard.
      navigate(state.from ?? '/', { replace: true })
    } catch (cause) {
      if (cause instanceof ApiError && cause.status === 429 && cause.retryAfterSeconds) {
        setError(`${cause.message} ${retryAfterText(cause.retryAfterSeconds)}`)
      } else {
        setError(errorMessage(cause))
      }
      // The password is cleared for the retry; that is not a mistake to flag.
      setPassword('')
      setSubmitted(false)
      setTouched((current) => ({ ...current, password: false }))
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <AuthLayout
      title="Sign in"
      subtitle="Use the login you registered with."
      footer={
        <>
          No account yet?{' '}
          <Link to="/register" className={linkOnDark}>
            Register
          </Link>
        </>
      }
    >
      <form onSubmit={onSubmit} noValidate aria-busy={submitting || undefined} className="space-y-5">
        {state.notice ? <Alert tone="success">{state.notice}</Alert> : null}
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
          hint={shown('username') ? problems.username : undefined}
        />

        <PasswordInput
          tone="onDark"
          label="Password"
          name="password"
          value={password}
          autoComplete="current-password"
          required
          onChange={(event) => setPassword(event.target.value)}
          onBlur={onBlur}
          invalid={shown('password')}
          hint={shown('password') ? problems.password : undefined}
        />

        <Button type="submit" variant="onDark" loading={submitting} className="w-full">
          {submitting ? 'Signing in…' : 'Sign in'}
        </Button>
      </form>
    </AuthLayout>
  )
}
