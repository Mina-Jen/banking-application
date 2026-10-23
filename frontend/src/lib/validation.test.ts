import { describe, expect, it } from 'vitest'
import {
  MAX_ACCOUNT_NAME_LENGTH,
  MAX_ACCOUNT_NUMBER_LENGTH,
  accountNameError,
  accountNumberError,
  normalizeUsername,
  passwordError,
  usernameError,
} from './validation'

describe('normalizeUsername', () => {
  it('trims and lowercases, exactly like AuthService.normalizeUsername', () => {
    expect(normalizeUsername('  Alice.Smith ')).toBe('alice.smith')
  })
})

describe('usernameError', () => {
  it.each(['alice', 'alice.smith', 'a_b-c', 'user123', 'abc'])(
    'accepts %s',
    (value) => {
      expect(usernameError(value)).toBeNull()
    },
  )

  it('accepts uppercase by normalizing it first, as the backend does', () => {
    expect(usernameError('ALICE')).toBeNull()
  })

  it('rejects fewer than 3 characters', () => {
    expect(usernameError('ab')).toMatch(/at least 3/)
  })

  it('rejects more than 50 characters', () => {
    expect(usernameError('a'.repeat(51))).toMatch(/at most 50/)
    expect(usernameError('a'.repeat(50))).toBeNull()
  })

  it.each([
    ['a space', /lowercase letters/],
    ['alice!', /lowercase letters/],
    ['aliçe', /lowercase letters/],
  ])('rejects %s', (value, expected) => {
    expect(usernameError(value)).toMatch(expected)
  })

  it('rejects an empty username', () => {
    expect(usernameError('   ')).toMatch(/choose a username/i)
  })
})

describe('passwordError', () => {
  it('accepts 8 to 72 characters', () => {
    expect(passwordError('a'.repeat(8))).toBeNull()
    expect(passwordError('a'.repeat(72))).toBeNull()
  })

  it('rejects 7 characters', () => {
    expect(passwordError('a'.repeat(7))).toMatch(/at least 8/)
  })

  it('rejects 73 characters, because BCrypt ignores bytes past the 72nd', () => {
    expect(passwordError('a'.repeat(73))).toMatch(/at most 72/)
  })
})

describe('account number and name limits', () => {
  it('match the VARCHAR widths in BankingService', () => {
    expect(MAX_ACCOUNT_NUMBER_LENGTH).toBe(20)
    expect(MAX_ACCOUNT_NAME_LENGTH).toBe(100)
  })

  it('accepts any non-blank account number up to 20 characters', () => {
    // The backend applies no character pattern, so neither does this.
    expect(accountNumberError('100001')).toBeNull()
    expect(accountNumberError('ACC-01/X')).toBeNull()
    expect(accountNumberError('a'.repeat(20))).toBeNull()
    expect(accountNumberError('a'.repeat(21))).toMatch(/at most 20/)
    expect(accountNumberError('  ')).toMatch(/enter an account number/i)
  })

  it('trims the account name before measuring it', () => {
    expect(accountNameError('  Juan Dela Cruz  ')).toBeNull()
    expect(accountNameError('a'.repeat(100))).toBeNull()
    expect(accountNameError('a'.repeat(101))).toMatch(/at most 100/)
  })
})
