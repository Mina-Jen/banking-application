import { describe, expect, it } from 'vitest'
import {
  addAmounts,
  amountError,
  compareAmounts,
  formatPesos,
  isValidAmount,
  isValidOpeningDeposit,
  normalizeAmount,
  openingDepositError,
  totalAmounts,
} from './money'

describe('isValidAmount', () => {
  it.each(['2000', '2000.00', '0.01', '1234567890123.45', ' 500 '])(
    'accepts %s',
    (value) => {
      expect(isValidAmount(value)).toBe(true)
    },
  )

  // Zero is rejected for a deposit, withdrawal or transfer.
  it.each(['0', '0.0', '0.00', '00.00'])('rejects zero written as %s', (value) => {
    expect(isValidAmount(value)).toBe(false)
  })

  it.each(['', '   ', '-5', '1.234', 'abc', '1e5', '1,000', '.5', '12345678901234.00'])(
    'rejects %s',
    (value) => {
      expect(isValidAmount(value)).toBe(false)
    },
  )

  it('rejects 14 integer digits, which DECIMAL(15,2) cannot store', () => {
    expect(isValidAmount('12345678901234')).toBe(false)
    expect(isValidAmount('1234567890123')).toBe(true)
  })
})

describe('amountError', () => {
  it('returns null for an acceptable amount', () => {
    expect(amountError('250.75')).toBeNull()
  })

  it('explains each rejection separately', () => {
    expect(amountError('')).toMatch(/enter an amount/i)
    expect(amountError('abc')).toMatch(/must be a number/i)
    expect(amountError('1.234')).toMatch(/two decimal places/i)
    expect(amountError('0')).toMatch(/greater than zero/i)
  })
})

describe('opening deposit rules', () => {
  it('allows zero, because the backend creates the account with no transaction', () => {
    expect(isValidOpeningDeposit('0')).toBe(true)
    expect(isValidOpeningDeposit('0.00')).toBe(true)
    expect(openingDepositError('0')).toBeNull()
  })

  it('still rejects three decimals and negatives', () => {
    expect(isValidOpeningDeposit('1.234')).toBe(false)
    expect(isValidOpeningDeposit('-1')).toBe(false)
    expect(openingDepositError('1.234')).toMatch(/two decimal places/i)
  })
})

describe('normalizeAmount', () => {
  it('pads to exactly two decimals', () => {
    expect(normalizeAmount('2000')).toBe('2000.00')
    expect(normalizeAmount('2000.5')).toBe('2000.50')
    expect(normalizeAmount(' 2000.05 ')).toBe('2000.05')
  })

  it('throws rather than silently sending a bad value', () => {
    expect(() => normalizeAmount('abc')).toThrow()
  })
})

describe('formatPesos', () => {
  it('groups thousands without touching the value', () => {
    expect(formatPesos('7000.00')).toBe('₱7,000.00')
    expect(formatPesos('0.05')).toBe('₱0.05')
    expect(formatPesos('1234567890123.45')).toBe('₱1,234,567,890,123.45')
  })

  it('formats the largest balance DECIMAL(15,2) can store', () => {
    expect(formatPesos('9999999999999.99')).toBe('₱9,999,999,999,999.99')
  })

  it('keeps every digit of a total that is past Number.MAX_SAFE_INTEGER', () => {
    // A dashboard total can outrun a single balance. Number() on this string
    // would lose the trailing digits; string formatting does not.
    expect(formatPesos('9999999999999990.00')).toBe('₱9,999,999,999,999,990.00')
  })
})

describe('addAmounts', () => {
  it('adds simple amounts', () => {
    expect(addAmounts('1000.00', '250.50')).toBe('1250.50')
    expect(addAmounts('0.01', '0.02')).toBe('0.03')
  })

  it('carries across the decimal point', () => {
    expect(addAmounts('0.99', '0.01')).toBe('1.00')
    expect(addAmounts('999.99', '0.01')).toBe('1000.00')
  })

  it('stays exact where floating point would not', () => {
    // 0.1 + 0.2 === 0.30000000000000004 as numbers.
    expect(addAmounts('0.10', '0.20')).toBe('0.30')
  })

  it('adds the largest storable balance without rounding', () => {
    expect(addAmounts('9999999999999.99', '0.01')).toBe('10000000000000.00')
  })

  it('accepts unpadded input', () => {
    expect(addAmounts('5', '2.5')).toBe('7.50')
  })
})

describe('totalAmounts', () => {
  it('sums a list of balances', () => {
    expect(totalAmounts(['1000.00', '250.50', '0.25'])).toBe('1250.75')
  })

  it('returns 0.00 for an empty list', () => {
    expect(totalAmounts([])).toBe('0.00')
  })

  it('stays exact past Number.MAX_SAFE_INTEGER', () => {
    // 1000 accounts, each at the largest balance DECIMAL(15,2) can store. The
    // same sum in floating point comes out as 9999999999999998 — eight units
    // wrong — which is exactly the bug this string arithmetic avoids.
    const largest = '9999999999999.99'
    const balances = Array<string>(1000).fill(largest)

    expect(totalAmounts(balances)).toBe('9999999999999990.00')
    expect(balances.reduce((sum, value) => sum + Number(value), 0)).toBe(9_999_999_999_999_998)
  })
})

describe('compareAmounts', () => {
  it.each([
    ['1.00', '2.00', -1],
    ['2.00', '1.00', 1],
    ['5.00', '5.00', 0],
    ['0.99', '1.00', -1],
    ['10.00', '9.99', 1],
    ['007.50', '7.50', 0],
    ['1234567890123.45', '1234567890123.46', -1],
  ])('compares %s with %s as %i', (left, right, expected) => {
    expect(compareAmounts(left, right)).toBe(expected)
  })

  it('agrees with numeric order for ordinary amounts', () => {
    const amounts = ['0.01', '0.10', '1.00', '9.99', '10.00', '100.00']
    for (let index = 1; index < amounts.length; index += 1) {
      expect(compareAmounts(amounts[index - 1], amounts[index])).toBe(-1)
      expect(compareAmounts(amounts[index], amounts[index - 1])).toBe(1)
    }
  })

  it('stays correct where a float comparison would lose a centavo', () => {
    // 0.1 + 0.2 !== 0.3 in binary floating point, but these are strings.
    expect(compareAmounts('0.30', '0.30')).toBe(0)
    // The largest DECIMAL(15,2) value and one centavo less.
    expect(compareAmounts('9999999999999.98', '9999999999999.99')).toBe(-1)
  })

  it('pads missing decimals before comparing the fraction', () => {
    expect(compareAmounts('5', '5.00')).toBe(0)
    expect(compareAmounts('5.5', '5.50')).toBe(0)
    expect(compareAmounts('5.5', '5.49')).toBe(1)
  })
})
