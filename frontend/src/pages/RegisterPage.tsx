import { useState, type FormEvent } from 'react'
import { Link, Navigate, useNavigate } from 'react-router-dom'
import { Alert } from '../components/Alert'
import { AuthLayout } from '../components/AuthLayout'
import { Field } from '../components/Field'
import { Spinner } from '../components/Spinner'
import { buttonPrimary } from '../components/ui'
import { errorMessage } from '../api/errors'
import { useAuth } from '../auth/AuthContext'
import {
  MAX_PASSWORD_LENGTH,
  MIN_PASSWORD_LENGTH,
  normalizeUsername,
  passwordError,
  usernameError,
} from '../lib/validation'

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
  const [error, setError] = useState<string | null>(null)

  if (isAuthenticated) {
    return <Navigate to="/" replace />
  }

  const userProblem = usernameError(username)
  const passProblem = passwordError(password)
  const confirmProblem =
    confirm === '' ? 'Repeat the password.' : confirm === password ? null : 'The two passwords differ.'
  const valid = userProblem === null && passProblem === null && confirmProblem === null

  async function onSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setSubmitted(true)
    if (!valid || submitting) {
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
      title="Create your login"
      subtitle="Registers a customer account. An administrator creates admin logins on the server."
      footer={
        <>
          Already registered?{' '}
          <Link to="/login" className="font-medium text-emerald-700 hover:text-emerald-800">
            Sign in
          </Link>
        </>
      }
    >
      <form onSubmit={onSubmit} noValidate className="space-y-4">
        {error ? <Alert tone="error">{error}</Alert> : null}

        <Field
          label="Username"
          name="username"
          value={username}
          autoComplete="username"
          autoFocus
          required
          onChange={(event) => setUsername(event.target.value)}
          invalid={submitted && userProblem !== null}
          hint={
            submitted && userProblem
              ? userProblem
              : '3 to 50 characters: lowercase letters, digits, dot, underscore or hyphen.'
          }
        />

        <Field
          label="Password"
          name="password"
          type="password"
          value={password}
          autoComplete="new-password"
          required
          onChange={(event) => setPassword(event.target.value)}
          invalid={submitted && passProblem !== null}
          hint={
            submitted && passProblem
              ? passProblem
              : `${MIN_PASSWORD_LENGTH} to ${MAX_PASSWORD_LENGTH} characters.`
          }
        />

        <Field
          label="Repeat password"
          name="confirm-password"
          type="password"
          value={confirm}
          autoComplete="new-password"
          required
          onChange={(event) => setConfirm(event.target.value)}
          invalid={submitted && confirmProblem !== null}
          hint={submitted && confirmProblem ? confirmProblem : undefined}
        />

        <button type="submit" className={buttonPrimary} disabled={submitting}>
          {submitting ? <Spinner /> : null}
          {submitting ? 'Creating…' : 'Register'}
        </button>
      </form>
    </AuthLayout>
  )
}
