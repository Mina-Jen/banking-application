/**
 * Money helpers.
 *
 * The backend sends money as a decimal STRING ("7000.00") and expects amounts
 * back as strings. The project rule is that money is never a float, so every
 * function here works on strings only — no amount ever passes through a JS
 * number, which cannot represent values like 0.1 exactly.
 *
 * The limits mirror BankingService.java: DECIMAL(15,2) means at most 13 digits
 * before the decimal point and at most 2 after it.
 */

const AMOUNT_PATTERN = /^\d{1,13}(\.\d{1,2})?$/

/**
 * Any non-negative decimal with at most two places, of ANY length.
 *
 * Deliberately looser than AMOUNT_PATTERN. The 13-digit limit describes what a
 * user may submit into a DECIMAL(15,2) column, but a running TOTAL is internal
 * arithmetic: adding up a thousand maximum balances legitimately produces a
 * 16-digit figure, and that must not be rejected as invalid input.
 */
const DECIMAL_PATTERN = /^\d+(\.\d{1,2})?$/

/** Splits a decimal string into whole digits and exactly two fraction digits. */
function splitHundredths(value: string): [string, string] {
  const trimmed = value.trim()
  if (!DECIMAL_PATTERN.test(trimmed)) {
    throw new Error(`Not a valid amount: ${value}`)
  }
  const [whole, fraction = ''] = trimmed.split('.')
  return [whole, fraction.padEnd(2, '0')]
}

/** True when `raw` is a positive amount the backend will accept. */
export function isValidAmount(raw: string): boolean {
  const value = raw.trim()
  if (!AMOUNT_PATTERN.test(value)) {
    return false
  }
  // The pattern allows "0" and "0.00"; deposits, withdrawals and transfers
  // must all be greater than zero.
  return !/^0*(\.0*)?$/.test(value)
}

/**
 * Explains why an amount is rejected, or returns null when it is fine. Shown
 * in the form before the user ever reaches the backend.
 */
export function amountError(raw: string): string | null {
  const value = raw.trim()
  if (value === '') {
    return 'Enter an amount.'
  }
  if (!/^\d/.test(value)) {
    return 'Amount must be a number, for example 2000.00.'
  }
  if (!/^\d{1,13}(\.\d{1,2})?$/.test(value)) {
    return 'Amount may have at most two decimal places.'
  }
  if (!isValidAmount(value)) {
    return 'Amount must be greater than zero.'
  }
  return null
}

/**
 * True for zero or any positive amount. This is the rule for an OPENING
 * deposit, which the backend allows to be zero: `createAccount` only records a
 * transaction when the deposit is greater than zero.
 */
export function isValidOpeningDeposit(raw: string): boolean {
  return AMOUNT_PATTERN.test(raw.trim())
}

/** Opening-deposit version of `amountError`: zero is allowed, negatives are not. */
export function openingDepositError(raw: string): string | null {
  const value = raw.trim()
  if (value === '') {
    return 'Enter 0 to open the account with no money in it.'
  }
  if (!/^\d/.test(value)) {
    return 'Amount must be a number, for example 5000.00.'
  }
  if (!/^\d{1,13}(\.\d{1,2})?$/.test(value)) {
    return 'Amount may have at most two decimal places.'
  }
  return null
}

/** Pads an accepted amount to exactly two decimals: "2000" becomes "2000.00". */
export function normalizeAmount(raw: string): string {
  const value = raw.trim()
  if (!AMOUNT_PATTERN.test(value)) {
    throw new Error(`Not a valid amount: ${raw}`)
  }
  const [whole, fraction = ''] = value.split('.')
  return `${whole}.${fraction.padEnd(2, '0')}`
}

/**
 * Compares two amounts: -1 when `left` is smaller, 0 when equal, 1 when larger.
 *
 * String comparison, never subtraction. Parsing to a number would reintroduce
 * the float problem this whole module exists to avoid, and would silently
 * misjudge amounts above Number.MAX_SAFE_INTEGER — exactly the balances a
 * DECIMAL(15,2) column can hold. Whole parts are compared by digit count first
 * (shorter is smaller), then lexicographically, then the two fraction digits.
 */
export function compareAmounts(left: string, right: string): -1 | 0 | 1 {
  const [leftWhole, leftFraction] = splitHundredths(left)
  const [rightWhole, rightFraction] = splitHundredths(right)

  // Leading zeros would break the length comparison: "007" is 7, not 3 digits.
  const a = leftWhole.replace(/^0+(?=\d)/, '')
  const b = rightWhole.replace(/^0+(?=\d)/, '')

  if (a.length !== b.length) {
    return a.length < b.length ? -1 : 1
  }
  if (a !== b) {
    return a < b ? -1 : 1
  }
  if (leftFraction !== rightFraction) {
    return leftFraction < rightFraction ? -1 : 1
  }
  return 0
}

/**
 * Adds two amounts exactly, digit by digit, and returns a two-decimal string.
 *
 * This is column addition on strings rather than `a + b` on numbers. Converting
 * to a number would break the "money is never a float" rule, and converting to
 * integer cents would not save it either: DECIMAL(15,2) allows 15 significant
 * digits, so cents can reach 17 digits and pass Number.MAX_SAFE_INTEGER.
 * Single digits are the only values ever turned into numbers here, and those
 * are exact, so the result is correct at any magnitude.
 */
export function addAmounts(left: string, right: string): string {
  const [leftWhole, leftFraction] = splitHundredths(left)
  const [rightWhole, rightFraction] = splitHundredths(right)

  // Both operands as hundredths, digits only: "123.45" -> "12345".
  const leftDigits = leftWhole + leftFraction
  const rightDigits = rightWhole + rightFraction
  const length = Math.max(leftDigits.length, rightDigits.length)
  const a = leftDigits.padStart(length, '0')
  const b = rightDigits.padStart(length, '0')

  let carry = 0
  let sum = ''
  for (let index = length - 1; index >= 0; index -= 1) {
    const column = Number(a[index]) + Number(b[index]) + carry
    carry = Math.floor(column / 10)
    sum = String(column % 10) + sum
  }
  if (carry > 0) {
    sum = String(carry) + sum
  }

  // `sum` is in hundredths, so the decimal point goes two places from the end.
  const padded = sum.padStart(3, '0')
  return `${padded.slice(0, -2)}.${padded.slice(-2)}`
}

/** Sums a list of amounts. An empty list is "0.00". */
export function totalAmounts(amounts: string[]): string {
  return amounts.reduce((total, amount) => addAmounts(total, amount), '0.00')
}

/**
 * Adds peso sign and thousands separators for display: "7000.00" becomes
 * "₱7,000.00". Pure string work — the digits are regrouped, never parsed into
 * a number, so a 13-digit balance stays exact.
 */
export function formatPesos(amount: string): string {
  const negative = amount.trimStart().startsWith('-')
  const digits = amount.replace('-', '').trim()

  // A balance from the API always has two decimals; tolerate anything else so
  // a malformed value still renders instead of throwing during render.
  const [whole, fraction] = digits.includes('.') ? digits.split('.') : [digits, '']
  const grouped = whole.replace(/\B(?=(\d{3})+(?!\d))/g, ',')
  const decimals = fraction === '' ? '' : `.${fraction}`

  return `${negative ? '-' : ''}₱${grouped}${decimals}`
}
