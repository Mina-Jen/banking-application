import { expect, test } from '@playwright/test'
import { uniqueUser } from './helpers'

/**
 * A username longer than the audit trail can store used to break the login
 * failure path: the `LOGIN_FAILURE` row overflowed `audit_log.username`
 * (VARCHAR(50)), so the attempt was never recorded and the client got a 500 (or
 * the security entry point's generic text) instead of the 401 it should.
 *
 * This goes straight to the HTTP layer against the real backend and database,
 * because the overflow happened in the database, not in Java.
 */
test('a login with an overlong username is a 401, not a 500', async ({ request }) => {
  // Unique per run, so the rate limiter never counts these attempts up to 429.
  const overlong = `${uniqueUser()}x`.repeat(8).slice(0, 60)
  expect(overlong.length).toBe(60)

  const response = await request.post('/api/auth/login', {
    data: { username: overlong, password: 'not-the-password-1' },
  })

  expect(response.status()).toBe(401)
  const body = await response.json()
  expect(body).toEqual({
    status: 401,
    error: 'Unauthorized',
    message: 'Invalid username or password.',
  })

  // A well-formed username that simply does not exist answers identically, so
  // the length boundary cannot be used to learn how the API treats a login.
  const unknown = await request.post('/api/auth/login', {
    data: { username: `${uniqueUser()}-nobody`, password: 'not-the-password-1' },
  })
  expect(unknown.status()).toBe(401)
  expect(await unknown.json()).toEqual(body)
})
