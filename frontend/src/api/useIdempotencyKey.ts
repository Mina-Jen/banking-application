/**
 * The lifecycle of one Idempotency-Key inside a form.
 *
 * The backend executes each key AT MOST ONCE, so the key has to match the user's
 * intent rather than the HTTP attempt:
 *
 *   - the FIRST submit of a payment generates a key;
 *   - a RETRY of that same payment reuses it. This is what makes a retry safe
 *     after a timeout or a dropped connection, where the browser cannot tell
 *     whether the money moved. With the same key the backend either replays the
 *     stored response or is still processing — it never pays twice;
 *   - a SUCCESS clears it, so the next payment gets a fresh key. Reusing a spent
 *     key would silently replay the old response and appear to do nothing;
 *   - EDITING the amount clears it. A different body under an already-claimed
 *     key is a 409 conflict by design, and more importantly it is a different
 *     payment, so it deserves its own key.
 *
 * Note that the automatic token-refresh retry in ./client.ts also reuses the
 * key, because it replays the identical request the caller already made.
 */

import { useCallback, useRef } from 'react'
import { newIdempotencyKey } from './idempotency'

export interface IdempotencyHandle {
  /** The key for the payment in progress, generating one on first call. */
  acquire(): string
  /** Call after a successful payment, or whenever the intent changes. */
  reset(): void
}

export function useIdempotencyKey(): IdempotencyHandle {
  const current = useRef<string | null>(null)

  const acquire = useCallback(() => {
    if (current.current === null) {
      current.current = newIdempotencyKey()
    }
    return current.current
  }, [])

  const reset = useCallback(() => {
    current.current = null
  }, [])

  return { acquire, reset }
}
