import { expect, test } from '@playwright/test'
import { PASSWORD, uniqueUser } from './helpers'

/**
 * The per-user API budget lives in the backend, so this goes straight at the
 * HTTP layer: 60 requests in one minute are served, the 61st is a 429 whose
 * Retry-After says when to come back, and the body is the usual
 * {status, error, message} shape. A browser test that clicked 61 times would
 * only prove the UI can be slow.
 */
test('the 61st account request in a minute is a 429 with Retry-After', async ({ request }) => {
  const username = uniqueUser()
  const registered = await request.post('/api/auth/register', {
    data: { username, password: PASSWORD },
  })
  expect(registered.status()).toBe(201)

  const login = await request.post('/api/auth/login', {
    data: { username, password: PASSWORD },
  })
  expect(login.status()).toBe(200)
  const { accessToken } = await login.json()
  const headers = { Authorization: `Bearer ${accessToken}` }

  for (let i = 1; i <= 60; i++) {
    const allowed = await request.get('/api/accounts', { headers })
    expect(allowed.status(), `request ${i} of the minute`).toBe(200)
  }

  const limited = await request.get('/api/accounts', { headers })
  expect(limited.status()).toBe(429)

  const retryAfter = Number(limited.headers()['retry-after'])
  expect(retryAfter).toBeGreaterThan(0)
  expect(retryAfter).toBeLessThanOrEqual(60)

  const body = await limited.json()
  expect(body).toMatchObject({
    status: 429,
    error: 'Too Many Requests',
  })
  // The wait is in the body too, and it agrees with the header.
  expect(body.message).toContain(`Try again in ${retryAfter} seconds.`)

  // A second customer has a budget of their own; the limit is per user, not
  // per address.
  const otherName = uniqueUser()
  await request.post('/api/auth/register', { data: { username: otherName, password: PASSWORD } })
  const other = await request.post('/api/auth/login', {
    data: { username: otherName, password: PASSWORD },
  })
  const otherToken = (await other.json()).accessToken

  const unaffected = await request.get('/api/accounts', {
    headers: { Authorization: `Bearer ${otherToken}` },
  })
  expect(unaffected.status()).toBe(200)
})
