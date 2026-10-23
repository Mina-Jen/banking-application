import { describe, expect, it } from 'vitest'
import type { ZodType } from 'zod'
import {
  depositSchema,
  openAccountSchema,
  transferSchema,
  withdrawalSchema,
} from './schemas'

/**
 * The first issue of a failed parse. Throws when the value is accepted, so a
 * test that expects a rejection fails loudly instead of comparing against null.
 */
function firstIssue(schema: ZodType, value: unknown) {
  const result = schema.safeParse(value)
  if (result.success) throw new Error(`expected ${JSON.stringify(value)} to be rejected`)
  return result.error.issues[0]
}

describe('depositSchema', () => {
  it('accepts a well-formed positive amount and keeps it a string', () => {
    const result = depositSchema.safeParse({ amount: '1250.75' })
    expect(result.success).toBe(true)
    // The project rule: money never becomes a JS number.
    expect(typeof result.data?.amount).toBe('string')
  })

  it('accepts an amount without decimals', () => {
    expect(depositSchema.safeParse({ amount: '2000' }).success).toBe(true)
  })

  it.each([
    ['', /Enter an amount/],
    ['0', /greater than zero/],
    ['0.00', /greater than zero/],
    ['abc', /must be a number/],
    ['1.234', /two decimal places/],
    ['-5.00', /must be a number/],
    ['12345678901234.00', /two decimal places/],
  ])('rejects %j', (amount, message) => {
    const issue = firstIssue(depositSchema, { amount })
    expect(issue).not.toBeNull()
    expect(issue.message).toMatch(message)
    expect(issue.path).toEqual(['amount'])
  })
})

describe('withdrawalSchema', () => {
  const balance = '100.00'

  it('accepts an amount below the balance', () => {
    expect(withdrawalSchema(balance).safeParse({ amount: '99.99' }).success).toBe(true)
  })

  it('accepts the exact balance, because the backend does', () => {
    expect(withdrawalSchema(balance).safeParse({ amount: '100.00' }).success).toBe(true)
    expect(withdrawalSchema(balance).safeParse({ amount: '100' }).success).toBe(true)
  })

  it('rejects one centavo more than the balance', () => {
    const issue = firstIssue(withdrawalSchema(balance), { amount: '100.01' })
    expect(issue?.message).toBe('Amount exceeds the available balance of ₱100.00.')
    expect(issue?.path).toEqual(['amount'])
  })

  it('reports only the format problem when the amount is malformed', () => {
    const result = withdrawalSchema(balance).safeParse({ amount: 'abc' })
    expect(result.success).toBe(false)
    expect(result.error?.issues).toHaveLength(1)
  })

  it('treats a zero balance as allowing nothing', () => {
    const issue = firstIssue(withdrawalSchema('0.00'), { amount: '0.01' })
    expect(issue?.message).toMatch(/exceeds the available balance/)
  })
})

describe('transferSchema', () => {
  const source = '100001'

  it('accepts a different destination within the balance', () => {
    const result = transferSchema(source, '500.00').safeParse({
      destinationAccountNumber: '100002',
      amount: '250.00',
    })
    expect(result.success).toBe(true)
  })

  it('rejects the source account as the destination', () => {
    const issue = firstIssue(transferSchema(source, '500.00'), {
      destinationAccountNumber: '100001',
      amount: '10.00',
    })
    expect(issue?.message).toBe('Source and destination must be different accounts.')
    expect(issue?.path).toEqual(['destinationAccountNumber'])
  })

  it('rejects a destination that differs only by surrounding spaces', () => {
    const issue = firstIssue(transferSchema(source, '500.00'), {
      destinationAccountNumber: '  100001  ',
      amount: '10.00',
    })
    expect(issue?.path).toEqual(['destinationAccountNumber'])
  })

  it('rejects an amount over the balance', () => {
    const issue = firstIssue(transferSchema(source, '500.00'), {
      destinationAccountNumber: '100002',
      amount: '500.01',
    })
    expect(issue?.message).toBe('Amount exceeds the available balance of ₱500.00.')
    expect(issue?.path).toEqual(['amount'])
  })

  it('reports both problems when both are wrong', () => {
    const result = transferSchema(source, '500.00').safeParse({
      destinationAccountNumber: '',
      amount: 'abc',
    })
    expect(result.success).toBe(false)
    expect(result.error?.issues.map((issue) => issue.path[0])).toEqual([
      'destinationAccountNumber',
      'amount',
    ])
  })
})

describe('openAccountSchema', () => {
  it('accepts zero as an opening deposit', () => {
    const result = openAccountSchema.safeParse({
      accountName: 'Juan Dela Cruz',
      initialDeposit: '0',
    })
    expect(result.success).toBe(true)
  })

  it('accepts a normal opening deposit', () => {
    const result = openAccountSchema.safeParse({
      accountName: 'Juan Dela Cruz',
      initialDeposit: '5000.00',
    })
    expect(result.success).toBe(true)
    expect(typeof result.data?.initialDeposit).toBe('string')
  })

  it('requires a name and a deposit', () => {
    const result = openAccountSchema.safeParse({
      accountName: '',
      initialDeposit: '',
    })
    expect(result.success).toBe(false)
    const paths = result.error?.issues.map((issue) => issue.path[0])
    expect(paths).toEqual(['accountName', 'initialDeposit'])
  })

  /**
   * The bank assigns the number, so a form that still sends one must not be
   * able to use it: Zod strips the unknown key and nothing reaches the request.
   */
  it('has no account number to validate and drops one it is sent', () => {
    const result = openAccountSchema.safeParse({
      accountNumber: 'x'.repeat(50),
      accountName: 'Juan',
      initialDeposit: '1.00',
    })

    expect(result.success).toBe(true)
    expect(Object.keys(result.data ?? {})).toEqual(['accountName', 'initialDeposit'])
  })

  it('rejects a name longer than the column', () => {
    const issue = firstIssue(openAccountSchema, {
      accountName: 'x'.repeat(101),
      initialDeposit: '1.00',
    })
    expect(issue?.message).toBe('Account holder name must be at most 100 characters.')
  })
})
