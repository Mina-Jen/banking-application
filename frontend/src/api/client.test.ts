/**
 * The token-refresh behaviour of the API client.
 *
 * The property that matters most is the replay one: when an access token expires
 * in the middle of a deposit, the client refreshes and replays the request with
 * the SAME Idempotency-Key and the SAME body. That is what stops a silent
 * refresh from turning one deposit into two.
 */

import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { errorBody, fakeAccessToken, jsonResponse } from '../test/helpers'

const OLD_TOKEN = fakeAccessToken({ uid: 1 })
const NEW_TOKEN = fakeAccessToken({ uid: 1, exp: Math.floor(Date.now() / 1000) + 7200 })

const DEPOSIT_PATH = '/api/accounts/100001/deposits'
const DEPOSIT_RESULT = {
  id: 7,
  reference: 'TXN-test00000001',
  accountNumber: '100001',
  type: 'DEPOSIT',
  amount: '250.00',
  balanceAfter: '1250.00',
  referenceAccount: null,
  createdAt: '2026-10-22T09:00:00Z',
}

/**
 * The modules under test are imported INSIDE beforeEach, not at the top.
 *
 * openapi-fetch captures `globalThis.fetch` once, when `createClient()` runs
 * during the import of client.ts (its default is `fetch: baseFetch =
 * globalThis.fetch`). Stubbing fetch after a static import would be ignored,
 * and the test would try to reach a real server on the jsdom origin. Resetting
 * the module registry and importing again makes `createClient()` run while the
 * stub is already installed.
 *
 * Everything the client touches is taken from that same fresh registry, so the
 * session singleton and `instanceof ApiError` still line up with what the client
 * actually throws.
 */
let client: typeof import('./client').client
let bankingApi: typeof import('./banking').bankingApi
let sessionStore: typeof import('../auth/tokenStorage').sessionStore
let ApiError: typeof import('./errors').ApiError

interface SeenRequest {
  path: string
  auth: string | null
  idempotencyKey: string | null
  body: string
}

let seen: SeenRequest[]
let refreshCalls: number
/** Set to 401 to simulate an expired or revoked refresh token. */
let refreshStatus: number
/** Set to false to make the server reject even a freshly refreshed token. */
let acceptNewToken: boolean

function depositCalls(): SeenRequest[] {
  return seen.filter((request) => request.path === DEPOSIT_PATH)
}

beforeEach(async () => {
  seen = []
  refreshCalls = 0
  refreshStatus = 200
  acceptNewToken = true
  localStorage.clear()

  vi.resetModules()
  vi.stubGlobal(
    'fetch',
    vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const request = input instanceof Request ? input : new Request(String(input), init)
      const path = new URL(request.url).pathname
      const auth = request.headers.get('Authorization')
      // clone() so reading the body here does not disturb the real request.
      const body =
        request.method === 'GET' || request.method === 'HEAD'
          ? ''
          : await request.clone().text()

      seen.push({ path, auth, idempotencyKey: request.headers.get('Idempotency-Key'), body })

      if (path === '/api/auth/refresh') {
        refreshCalls += 1
        if (refreshStatus !== 200) {
          return jsonResponse(
            refreshStatus,
            errorBody(refreshStatus, 'Unauthorized', 'Refresh token is invalid or expired.'),
          )
        }
        return jsonResponse(200, {
          accessToken: NEW_TOKEN,
          refreshToken: 'refresh-token-2',
          tokenType: 'Bearer',
          expiresInSeconds: 900,
        })
      }

      if (path === DEPOSIT_PATH) {
        // Only the replay carries the new token, which is what distinguishes the
        // first attempt from the retry.
        return auth === `Bearer ${NEW_TOKEN}` && acceptNewToken
          ? jsonResponse(200, DEPOSIT_RESULT)
          : jsonResponse(
              401,
              errorBody(
                401,
                'Unauthorized',
                "Authentication required: send 'Authorization: Bearer <access token>'.",
              ),
            )
      }

      if (path === '/api/auth/login') {
        return jsonResponse(401, errorBody(401, 'Unauthorized', 'Invalid username or password.'))
      }

      return jsonResponse(404, errorBody(404, 'Not Found', `Unexpected test call to ${path}`))
    }),
  )

  ;({ client } = await import('./client'))
  ;({ bankingApi } = await import('./banking'))
  ;({ sessionStore } = await import('../auth/tokenStorage'))
  ;({ ApiError } = await import('./errors'))

  sessionStore.save({ accessToken: OLD_TOKEN, refreshToken: 'refresh-token-1' })
})

afterEach(() => {
  vi.unstubAllGlobals()
  vi.resetModules()
  localStorage.clear()
})

async function postDeposit(key: string) {
  return client.POST('/api/accounts/{accountNumber}/deposits', {
    params: { path: { accountNumber: '100001' }, header: { 'Idempotency-Key': key } },
    body: { amount: '250.00' },
  })
}

describe('silent token refresh', () => {
  it('refreshes and replays a request that was rejected with 401', async () => {
    const result = await postDeposit('key-abc')

    expect(result.response.status).toBe(200)
    expect(refreshCalls).toBe(1)

    const deposits = depositCalls()
    expect(deposits).toHaveLength(2)
    expect(deposits[0].auth).toBe(`Bearer ${OLD_TOKEN}`)
    expect(deposits[1].auth).toBe(`Bearer ${NEW_TOKEN}`)
  })

  it('replays with the SAME idempotency key and the SAME body', async () => {
    await postDeposit('key-abc')

    const [first, replay] = depositCalls()
    // A different key here would mean the backend records a second deposit.
    expect(replay.idempotencyKey).toBe('key-abc')
    expect(replay.idempotencyKey).toBe(first.idempotencyKey)
    expect(replay.body).toBe(first.body)
    expect(replay.body).toContain('250.00')
  })

  it('stores the new token pair for later calls', async () => {
    await postDeposit('key-abc')

    expect(sessionStore.load()?.accessToken).toBe(NEW_TOKEN)
    expect(sessionStore.load()?.refreshToken).toBe('refresh-token-2')
  })

  it('collapses parallel 401s into a single refresh call', async () => {
    await Promise.all([postDeposit('key-1'), postDeposit('key-2'), postDeposit('key-3')])

    expect(refreshCalls).toBe(1)
    expect(depositCalls()).toHaveLength(6)
  })

  it('gives up after ONE replay when the server still rejects the new token', async () => {
    acceptNewToken = false

    const result = await postDeposit('key-abc')

    expect(result.response.status).toBe(401)
    // Exactly two attempts: the original and one replay. The replay goes through
    // plain fetch rather than the client, so it cannot re-enter the middleware
    // and loop forever.
    expect(depositCalls()).toHaveLength(2)
    expect(refreshCalls).toBe(1)
  })

  it('passes a non-401 error straight through without refreshing', async () => {
    const result = await client.GET('/api/accounts/{accountNumber}', {
      params: { path: { accountNumber: '100001' } },
    })

    // The mock answers 404 for any path it does not know.
    expect(result.response.status).toBe(404)
    expect(refreshCalls).toBe(0)
  })
})

describe('refresh failure', () => {
  it('clears the session and surfaces the original 401', async () => {
    refreshStatus = 401

    const result = await postDeposit('key-abc')

    expect(result.response.status).toBe(401)
    expect(refreshCalls).toBe(1)
    // Only one deposit attempt reached the server: no retry without a token.
    expect(depositCalls()).toHaveLength(1)
    // Clearing is what makes RequireAuth redirect to /login.
    expect(sessionStore.load()).toBeNull()
  })
})

describe('endpoints that must not trigger a refresh', () => {
  it('reports a wrong password as 401 without trying to refresh', async () => {
    const cause = await bankingApi.login('alice', 'wrong-password').catch((error: unknown) => error)

    expect(cause).toBeInstanceOf(ApiError)
    expect(cause).toMatchObject({ status: 401, message: 'Invalid username or password.' })
    expect(refreshCalls).toBe(0)
    // The session must survive a failed login: the user is still signed in.
    expect(sessionStore.load()?.accessToken).toBe(OLD_TOKEN)
  })

  it('never sends an Authorization header to the auth endpoints', async () => {
    await bankingApi.login('alice', 'wrong-password').catch(() => undefined)

    const login = seen.find((request) => request.path === '/api/auth/login')
    expect(login?.auth).toBeNull()
  })
})
