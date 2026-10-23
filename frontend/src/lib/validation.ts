/**
 * Login-format rules, copied from AuthService.java so the form can reject a bad
 * username or password before spending a round trip.
 *
 * These mirror the backend exactly:
 *   USERNAME_PATTERN   = "[a-z0-9._-]{3,50}"   (applied after trim + lowercase)
 *   MIN_PASSWORD_LENGTH = 8
 *   MAX_PASSWORD_LENGTH = 72   (BCrypt ignores bytes past the 72nd)
 *
 * The backend remains the authority. If these drift, the worst case is a 422
 * whose message is shown verbatim — never an invalid account being created.
 */

export const USERNAME_PATTERN = /^[a-z0-9._-]{3,50}$/
export const MIN_PASSWORD_LENGTH = 8
export const MAX_PASSWORD_LENGTH = 72

/** Usernames are stored lowercase, so validate the lowercased form. */
export function normalizeUsername(raw: string): string {
  return raw.trim().toLowerCase()
}

export function usernameError(raw: string): string | null {
  const username = normalizeUsername(raw)
  if (username === '') {
    return 'Choose a username.'
  }
  if (username.length < 3) {
    return 'Username must be at least 3 characters.'
  }
  if (username.length > 50) {
    return 'Username must be at most 50 characters.'
  }
  if (!USERNAME_PATTERN.test(username)) {
    return 'Only lowercase letters, digits, dot, underscore and hyphen.'
  }
  return null
}

export function passwordError(raw: string): string | null {
  if (raw === '') {
    return 'Choose a password.'
  }
  if (raw.length < MIN_PASSWORD_LENGTH) {
    return `Password must be at least ${MIN_PASSWORD_LENGTH} characters.`
  }
  if (raw.length > MAX_PASSWORD_LENGTH) {
    return `Password must be at most ${MAX_PASSWORD_LENGTH} characters.`
  }
  return null
}

/**
 * Account field limits, from BankingService.java. They exist because of the
 * column widths: account_number is VARCHAR(20) and account_name is VARCHAR(100).
 * The backend applies no character pattern to an account number, so neither
 * does this.
 */
export const MAX_ACCOUNT_NUMBER_LENGTH = 20
export const MAX_ACCOUNT_NAME_LENGTH = 100

export function accountNumberError(raw: string): string | null {
  const value = raw.trim()
  if (value === '') {
    return 'Enter an account number.'
  }
  if (value.length > MAX_ACCOUNT_NUMBER_LENGTH) {
    return `Account number must be at most ${MAX_ACCOUNT_NUMBER_LENGTH} characters.`
  }
  return null
}

export function accountNameError(raw: string): string | null {
  const value = raw.trim()
  if (value === '') {
    return 'Enter the account holder name.'
  }
  if (value.length > MAX_ACCOUNT_NAME_LENGTH) {
    return `Account holder name must be at most ${MAX_ACCOUNT_NAME_LENGTH} characters.`
  }
  return null
}
