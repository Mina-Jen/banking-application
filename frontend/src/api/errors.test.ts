import { describe, expect, it } from 'vitest'
import { ApiError, errorMessage } from './errors'

const BODY = {
  status: 422,
  error: 'Unprocessable Entity',
  message: 'Deposit amount must be greater than zero.',
}

function jsonResponse(status: number, body: unknown, headers: Record<string, string> = {}) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json', ...headers },
  })
}

describe('ApiError.from', () => {
  /**
   * Regression test. openapi-fetch parses the body and hands it over as its
   * `error` field, leaving the Response consumed. Reading the Response anyway
   * throws, and an earlier version swallowed that and reported a useless
   * "Request failed with status 422" for EVERY backend rejection — hiding the
   * message the backend had already written for the user.
   */
  it('uses the parsed body when the response has already been consumed', async () => {
    const response = jsonResponse(422, BODY)
    await response.json()
    expect(response.bodyUsed).toBe(true)

    const error = await ApiError.from(response, BODY)

    expect(error.message).toBe('Deposit amount must be greater than zero.')
    expect(error.status).toBe(422)
    expect(error.error).toBe('Unprocessable Entity')
  })

  it('reads the response itself when no parsed body is supplied', async () => {
    const error = await ApiError.from(jsonResponse(422, BODY))

    expect(error.message).toBe('Deposit amount must be greater than zero.')
  })

  it('keeps Retry-After from the headers, which the body does not carry', async () => {
    const response = jsonResponse(429, {
      status: 429,
      error: 'Too Many Requests',
      message: 'Too many failed login attempts.',
    })
    // The header has to be set at construction; Response headers are immutable
    // for a non-Response-init header list in some runtimes.
    const withHeader = new Response(JSON.stringify({
      status: 429,
      error: 'Too Many Requests',
      message: 'Too many failed login attempts.',
    }), {
      status: 429,
      headers: { 'Content-Type': 'application/json', 'Retry-After': '300' },
    })

    expect((await ApiError.from(withHeader, undefined)).retryAfterSeconds).toBe(300)
    expect((await ApiError.from(response, undefined)).retryAfterSeconds).toBeNull()
  })

  it('falls back to the status when the body is not JSON', async () => {
    const error = await ApiError.from(new Response('<html>bad gateway</html>', { status: 502 }))

    expect(error.status).toBe(502)
    expect(error.message).toBe('Request failed with status 502')
  })

  it('falls back to the status when the body is JSON but has no message', async () => {
    const error = await ApiError.from(jsonResponse(400, { unexpected: true }), { unexpected: true })

    expect(error.status).toBe(400)
    expect(error.message).toBe('Request failed with status 400')
  })
})

describe('errorMessage', () => {
  it('passes an ApiError message straight through', () => {
    expect(errorMessage(new ApiError(409, 'Conflict', 'Username alice is already taken.'))).toBe(
      'Username alice is already taken.',
    )
  })

  it('explains an unreachable backend, which fetch reports as a TypeError', () => {
    expect(errorMessage(new TypeError('Failed to fetch'))).toMatch(/cannot reach the backend/i)
  })

  it('uses the message of an ordinary Error', () => {
    expect(errorMessage(new Error('boom'))).toBe('boom')
  })

  it('never renders an empty or non-Error value as blank', () => {
    expect(errorMessage(undefined)).toMatch(/something went wrong/i)
    expect(errorMessage('a string')).toMatch(/something went wrong/i)
    expect(errorMessage(new Error(''))).toMatch(/something went wrong/i)
  })
})
