/**
 * Idempotency keys for the money-moving endpoints.
 *
 * The backend requires an `Idempotency-Key` header on deposits, withdrawals
 * and transfers, and executes each key AT MOST ONCE. Replaying the same key
 * with the same body returns the stored response instead of moving money again.
 *
 * The rule that follows from this: one key per USER INTENT, not per HTTP
 * attempt. Generate it when the user submits the form and reuse it for every
 * retry of that submission. That is what makes a retry after a timeout, or the
 * automatic token-refresh retry in client.ts, safe — the customer can never be
 * charged twice for one click.
 *
 * A fresh key must be used for the NEXT, genuinely different payment, or the
 * backend will replay the old response and nothing will happen.
 */

/**
 * Returns a new random key. The backend accepts 1 to 100 characters
 * (IdempotencyService.MAX_KEY_LENGTH); a UUID is 36.
 */
export function newIdempotencyKey(): string {
  return crypto.randomUUID()
}
