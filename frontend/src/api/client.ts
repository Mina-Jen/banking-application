/**
 * The HTTP transport for the whole app.
 *
 * `client` is generated from the backend's own OpenAPI document (see
 * src/api/schema.d.ts), so a wrong path, a missing path parameter, a missing
 * Idempotency-Key header or a mistyped request body is a COMPILE error rather
 * than a runtime surprise.
 *
 * On top of that this module adds the two things the generated client cannot
 * know about:
 *
 *   1. every request carries the current access token;
 *   2. a 401 triggers one silent token refresh and one replay of the original
 *      request, so a token that expires mid-session (access tokens live only
 *      15 minutes) does not throw the user out of the page they are on.
 */

import createClient, { type Middleware } from 'openapi-fetch'
import type { paths } from './schema'
import type { TokenPair } from './types'
import { sessionStore } from '../auth/tokenStorage'

/**
 * The origin the API is served from.
 *
 * In development `VITE_API_BASE_URL` is unset and this resolves to the page's
 * own origin, so a call to /api/... reaches the Vite dev server, which proxies
 * it to Spring Boot on 18080. Same origin means no CORS preflight and no
 * credentials leaving the machine.
 *
 * Resolving to an ABSOLUTE origin rather than leaving the base empty matters:
 * `new Request('/api/...')` only works when a document base URL exists, so a
 * relative base silently breaks in any non-browser environment (tests, SSR,
 * scripts). Set VITE_API_BASE_URL to point a build at a different host.
 */
export const API_BASE_URL: string =
  import.meta.env.VITE_API_BASE_URL ??
  (typeof window === 'undefined' ? '' : window.location.origin)

/**
 * Endpoints that must never trigger a token refresh. A 401 here means "wrong
 * password" or "bad refresh token", not "your access token expired", and
 * retrying would just loop.
 */
const AUTH_PATHS = new Set(['/api/auth/login', '/api/auth/register', '/api/auth/refresh'])

/**
 * Bodies of in-flight requests, keyed by their Request. fetch() disturbs a
 * body once it is sent, so a replay needs a copy taken beforehand. Replaying
 * the IDENTICAL bytes also matters for idempotency: the backend hashes the
 * request body to decide whether a repeated key is a replay or a conflict.
 */
const stashedBodies = new WeakMap<Request, string>()

/** One refresh at a time: parallel 401s share a single /auth/refresh call. */
let refreshInFlight: Promise<string | null> | null = null

function isAuthPath(url: string): boolean {
  const { pathname } = new URL(url, 'http://localhost')
  return AUTH_PATHS.has(pathname)
}

async function refreshAccessToken(): Promise<string | null> {
  refreshInFlight ??= performRefresh().finally(() => {
    refreshInFlight = null
  })
  return refreshInFlight
}

async function performRefresh(): Promise<string | null> {
  const session = sessionStore.load()
  if (!session) {
    return null
  }

  let response: Response
  try {
    response = await fetch(`${API_BASE_URL}/api/auth/refresh`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ refreshToken: session.refreshToken } satisfies { refreshToken: string }),
    })
  } catch {
    // The backend is unreachable. That is not the user's fault and not a
    // reason to sign them out, so keep the session and report failure.
    return null
  }

  if (response.status === 401) {
    // Refresh token expired, revoked, or the login was disabled. Nothing left
    // to try — clearing the session makes RequireAuth redirect to /login.
    sessionStore.clear()
    return null
  }

  if (!response.ok) {
    return null
  }

  const tokens = (await response.json()) as TokenPair
  sessionStore.save({ accessToken: tokens.accessToken, refreshToken: tokens.refreshToken })
  return tokens.accessToken
}

const authMiddleware: Middleware = {
  async onRequest({ request }) {
    const replayable = request.method !== 'GET' && request.method !== 'HEAD'
    if (replayable) {
      stashedBodies.set(request, await request.clone().text())
    }

    if (!isAuthPath(request.url)) {
      const session = sessionStore.load()
      if (session) {
        request.headers.set('Authorization', `Bearer ${session.accessToken}`)
      }
    }
    return request
  },

  async onResponse({ request, response }) {
    if (response.status !== 401 || isAuthPath(request.url)) {
      return
    }

    const accessToken = await refreshAccessToken()
    if (!accessToken) {
      // Let the original 401 reach the caller, which turns it into a redirect.
      return
    }

    const headers = new Headers(request.headers)
    headers.set('Authorization', `Bearer ${accessToken}`)

    const replay = new Request(request.url, {
      method: request.method,
      headers,
      body: stashedBodies.get(request),
    })

    // Plain fetch on purpose, NOT client.POST: going through the client would
    // re-enter this middleware and could loop forever on a persistent 401.
    return fetch(replay)
  },
}

export const client = createClient<paths>({ baseUrl: API_BASE_URL })
client.use(authMiddleware)
