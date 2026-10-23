/**
 * Decodes a JWT WITHOUT verifying its signature.
 *
 * This is safe here only because the token comes from our own backend and the
 * browser never trusts these values for an authorization decision — Spring
 * Security re-verifies the signature and the role on every request. The decode
 * exists purely so the UI can show "Signed in as alice" and hide admin-only
 * affordances without an extra round trip.
 */

export type Role = 'CUSTOMER' | 'ADMIN'

export interface AccessTokenClaims {
  /** Username, set by JwtService as the token subject. */
  sub: string
  /** Internal user id. */
  uid: number
  role: Role
  /** "access" or "refresh". */
  typ: string
  /** Expiry as seconds since the epoch. */
  exp: number
}

function base64UrlToJson(segment: string): string {
  const base64 = segment.replace(/-/g, '+').replace(/_/g, '/')
  const padded = base64 + '='.repeat((4 - (base64.length % 4)) % 4)
  const binary = atob(padded)
  // atob gives a latin1 string; go through bytes so a non-ASCII claim survives.
  const bytes = Uint8Array.from(binary, (character) => character.charCodeAt(0))
  return new TextDecoder().decode(bytes)
}

/** Returns the claims, or null when the token is not a readable JWT. */
export function decodeAccessToken(token: string): AccessTokenClaims | null {
  const parts = token.split('.')
  if (parts.length !== 3) {
    return null
  }
  try {
    const claims = JSON.parse(base64UrlToJson(parts[1])) as Partial<AccessTokenClaims>
    if (typeof claims.sub !== 'string' || claims.sub === '') {
      return null
    }
    return {
      sub: claims.sub,
      uid: typeof claims.uid === 'number' ? claims.uid : 0,
      role: claims.role === 'ADMIN' ? 'ADMIN' : 'CUSTOMER',
      typ: typeof claims.typ === 'string' ? claims.typ : 'access',
      exp: typeof claims.exp === 'number' ? claims.exp : 0,
    }
  } catch {
    return null
  }
}
