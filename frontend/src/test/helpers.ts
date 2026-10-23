/**
 * Test helpers.
 *
 * The tokens built here are UNSIGNED fakes. That is fine because these tests
 * exercise the browser side, which only decodes tokens; signature verification
 * is the backend's job and is covered by JwtServiceTest.java.
 */

interface AccessTokenOptions {
  username?: string
  role?: 'CUSTOMER' | 'ADMIN'
  uid?: number
  /** Seconds since the epoch; defaults to one hour from now. */
  exp?: number
}

function base64Url(value: unknown): string {
  return btoa(JSON.stringify(value)).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '')
}

export function fakeJwt(payload: Record<string, unknown>): string {
  return `${base64Url({ alg: 'HS256', typ: 'JWT' })}.${base64Url(payload)}.not-a-real-signature`
}

export function fakeAccessToken(options: AccessTokenOptions = {}): string {
  return fakeJwt({
    sub: options.username ?? 'alice',
    uid: options.uid ?? 1,
    role: options.role ?? 'CUSTOMER',
    typ: 'access',
    exp: options.exp ?? Math.floor(Date.now() / 1000) + 3600,
  })
}

/** A Response with a JSON body, the way the backend answers. */
export function jsonResponse(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}

/** The backend's error body, as written by ErrorResponse.of(...). */
export function errorBody(status: number, error: string, message: string) {
  return { status, error, message }
}
