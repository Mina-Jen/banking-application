import { describe, expect, it } from 'vitest'
import { decodeAccessToken } from './jwt'
import { fakeAccessToken, fakeJwt } from '../test/helpers'

describe('decodeAccessToken', () => {
  it('reads the claims the backend puts in the token', () => {
    const token = fakeAccessToken({ username: 'alice.smith', role: 'ADMIN', uid: 42 })
    const claims = decodeAccessToken(token)

    expect(claims).not.toBeNull()
    expect(claims?.sub).toBe('alice.smith')
    expect(claims?.uid).toBe(42)
    expect(claims?.role).toBe('ADMIN')
    expect(claims?.typ).toBe('access')
  })

  it('treats an unrecognised role as CUSTOMER, never as ADMIN', () => {
    // Fail closed: a malformed or future role must not grant the admin UI.
    const claims = decodeAccessToken(fakeJwt({ sub: 'alice', role: 'SUPERUSER' }))
    expect(claims?.role).toBe('CUSTOMER')
  })

  it('returns null for something that is not a JWT', () => {
    expect(decodeAccessToken('not-a-token')).toBeNull()
    expect(decodeAccessToken('')).toBeNull()
    expect(decodeAccessToken('a.b')).toBeNull()
  })

  it('returns null for a token with undecodable or empty claims', () => {
    expect(decodeAccessToken('header.!!!not-base64!!!.signature')).toBeNull()
    expect(decodeAccessToken(fakeJwt({ role: 'CUSTOMER' }))).toBeNull()
    expect(decodeAccessToken(fakeJwt({ sub: '' }))).toBeNull()
  })

  it('tolerates a missing optional claim instead of throwing', () => {
    const claims = decodeAccessToken(fakeJwt({ sub: 'bob' }))

    expect(claims?.sub).toBe('bob')
    expect(claims?.uid).toBe(0)
    expect(claims?.exp).toBe(0)
  })
})
