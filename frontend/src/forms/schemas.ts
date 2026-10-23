import { z } from 'zod'
import {
  amountError,
  compareAmounts,
  formatPesos,
  normalizeAmount,
  openingDepositError,
} from '../lib/money'
import { accountNameError, accountNumberError } from '../lib/validation'

/**
 * Form schemas for react-hook-form.
 *
 * Every rule DELEGATES to the helpers in lib/money.ts and lib/validation.ts
 * instead of restating a regex, so the forms cannot disagree with each other
 * about what a valid amount or account number is. Those helpers in turn mirror
 * the backend: DECIMAL(15,2) (13 integer digits, 2 decimals), VARCHAR(20)
 * account numbers, VARCHAR(100) names, transfer amount <= balance.
 *
 * This is UX, not security. React Hook Form validation runs in the browser and
 * anyone can skip it; the backend validates every request again and its message
 * is what the UI shows when the two disagree.
 *
 * Money is a string in every schema. `z.number()` would turn an amount into the
 * float this project forbids.
 */

/** Builds a string field that runs one of the shared rule functions. */
function checkedString(check: (raw: string) => string | null) {
  return z.string().superRefine((value, ctx) => {
    const problem = check(value)
    if (problem !== null) {
      ctx.addIssue({ code: 'custom', message: problem })
    }
  })
}

const positiveAmount = checkedString(amountError)

function exceedsBalance(amount: string, availableBalance: string): boolean {
  // Only meaningful once the amount itself is well formed; otherwise the field
  // error already explains the problem.
  if (amountError(amount) !== null) {
    return false
  }
  return compareAmounts(normalizeAmount(amount), availableBalance) > 0
}

const overBalanceMessage = (availableBalance: string) =>
  `Amount exceeds the available balance of ${formatPesos(availableBalance)}.`

/**
 * Opening an account asks for a name and a deposit only. The account number is
 * not a field: the bank assigns it and the response carries it back, so nobody
 * can be told "that number already exists" and nobody can probe a range.
 */
export const openAccountSchema = z.object({
  accountName: checkedString(accountNameError),
  // Zero is allowed here: the backend opens the account with no transaction.
  initialDeposit: checkedString(openingDepositError),
})

export const depositSchema = z.object({
  amount: positiveAmount,
})

/** Withdrawals cannot exceed what is in the account. */
export function withdrawalSchema(availableBalance: string) {
  return z.object({ amount: positiveAmount }).superRefine((values, ctx) => {
    if (exceedsBalance(values.amount, availableBalance)) {
      ctx.addIssue({
        code: 'custom',
        path: ['amount'],
        message: overBalanceMessage(availableBalance),
      })
    }
  })
}

/**
 * Transfers check the destination and the amount together: two accounts that
 * look different as strings can still be rejected by the backend if they are
 * the same row, which is why the server stays authoritative.
 */
export function transferSchema(sourceAccountNumber: string, availableBalance: string) {
  return z
    .object({
      destinationAccountNumber: checkedString(accountNumberError),
      amount: positiveAmount,
    })
    .superRefine((values, ctx) => {
      if (values.destinationAccountNumber.trim() === sourceAccountNumber) {
        ctx.addIssue({
          code: 'custom',
          path: ['destinationAccountNumber'],
          message: 'Source and destination must be different accounts.',
        })
      }
      if (exceedsBalance(values.amount, availableBalance)) {
        ctx.addIssue({
          code: 'custom',
          path: ['amount'],
          message: overBalanceMessage(availableBalance),
        })
      }
    })
}

export type OpenAccountValues = z.infer<typeof openAccountSchema>
export type AmountValues = z.infer<typeof depositSchema>
// transferSchema is a factory (it closes over the source account and balance),
// so the field values are the return type's output, not the function's.
export type TransferValues = z.infer<ReturnType<typeof transferSchema>>
