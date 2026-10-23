/**
 * Typed wrappers around the generated OpenAPI client — one function per
 * backend endpoint, and the only place in the app that turns a non-2xx
 * response into a thrown `ApiError`.
 *
 * Every request and response type below is derived from the backend's OpenAPI
 * document, so if a DTO field is renamed in Java and `npm run sync:api` is
 * re-run, the mismatch shows up here as a TypeScript error.
 *
 * Money crosses this boundary as a decimal string and stays a string. It is
 * never converted to a number.
 */

import { client } from './client'
import { ApiError } from './errors'
import type { Account, AccountPage, RegisteredUser, TokenPair, Transaction, TransactionPage } from './types'

export type { Account, AccountPage, RegisteredUser, TokenPair, Transaction, TransactionPage }

/**
 * Request body for opening an account. There is no account number to send: the
 * bank assigns it, and the created {@link Account} in the response carries it.
 */
export interface CreateAccountInput {
  accountName: string
  /** Decimal string; "0" or "0.00" creates the account with no transaction. */
  initialDeposit: string
}

/** Request body for moving money between two accounts. */
export interface TransferInput {
  sourceAccountNumber: string
  destinationAccountNumber: string
  /** Decimal string, greater than zero. */
  amount: string
}

/**
 * Unwraps an openapi-fetch result into the value, or throws the backend's own
 * error message.
 *
 * openapi-fetch reports `{data, error, response}`, and because springdoc has no
 * error schema on these operations, `data` and `error` carry the SAME static
 * type and cannot be told apart. `response.ok` is therefore the one trustworthy
 * signal.
 *
 * The `as T` is the single deliberate assertion in the API layer: `T` is the
 * `Defined<...>` type from ./types, which drops the optionality that springdoc
 * adds by not emitting a `required` list for Java records. See the long comment
 * in ./types.ts for why that is accurate.
 *
 * `error` is forwarded to ApiError.from because openapi-fetch has already
 * consumed the response body by the time this runs; without it every failure
 * would report a generic message instead of the backend's own.
 */
async function unwrap<T>(result: {
  data?: unknown
  error?: unknown
  response: Response
}): Promise<T> {
  if (!result.response.ok) {
    throw await ApiError.from(result.response, result.error)
  }
  if (result.data === undefined) {
    throw new ApiError(500, 'Internal Server Error', 'The backend returned an empty response.')
  }
  return result.data as T
}

export const bankingApi = {
  /**
   * Creates a CUSTOMER login. The role always comes from the backend, never
   * from the browser, so this cannot be used to make an admin.
   * 409 = username taken, 422 = username or password format rejected.
   */
  async register(username: string, password: string): Promise<RegisteredUser> {
    return unwrap<RegisteredUser>(
      await client.POST('/api/auth/register', { body: { username, password } }),
    )
  },

  /**
   * Exchanges credentials for the token pair.
   * 401 = invalid credentials, 403 = disabled, 429 = rate limited.
   */
  async login(username: string, password: string): Promise<TokenPair> {
    return unwrap<TokenPair>(
      await client.POST('/api/auth/login', { body: { username, password } }),
    )
  },

  /**
   * Opens an account owned by the caller and returns it with the number the
   * bank assigned.
   * 422 = invalid name or deposit, 503 = no number could be assigned (retry).
   */
  async createAccount(input: CreateAccountInput): Promise<Account> {
    return unwrap<Account>(await client.POST('/api/accounts', { body: input }))
  },

  /**
   * One page of the accounts the caller may see: only their own for a
   * CUSTOMER, every account in the system for an ADMIN. Ordered by account
   * number; the backend caps `size` at 100 and answers 422 above that.
   */
  async accounts(page = 0, size = 100): Promise<AccountPage> {
    return unwrap<AccountPage>(
      await client.GET('/api/accounts', { params: { query: { page, size } } }),
    )
  },

  /**
   * 404 covers both cases a caller must not be able to tell apart: the account
   * does not exist, and the account exists but is not theirs.
   */
  async getAccount(accountNumber: string): Promise<Account> {
    return unwrap<Account>(
      await client.GET('/api/accounts/{accountNumber}', {
        params: { path: { accountNumber } },
      }),
    )
  },

  /**
   * Adds money to an account.
   *
   * @param idempotencyKey must stay the same across retries of ONE deposit,
   *   and must be fresh for the next one. See api/idempotency.ts.
   */
  async deposit(accountNumber: string, amount: string, idempotencyKey: string): Promise<Transaction> {
    return unwrap<Transaction>(
      await client.POST('/api/accounts/{accountNumber}/deposits', {
        params: { path: { accountNumber }, header: { 'Idempotency-Key': idempotencyKey } },
        body: { amount },
      }),
    )
  },

  /** Removes money from an account. 422 covers insufficient funds. */
  async withdraw(accountNumber: string, amount: string, idempotencyKey: string): Promise<Transaction> {
    return unwrap<Transaction>(
      await client.POST('/api/accounts/{accountNumber}/withdrawals', {
        params: { path: { accountNumber }, header: { 'Idempotency-Key': idempotencyKey } },
        body: { amount },
      }),
    )
  },

  /**
   * Moves money between two accounts in one database transaction. The caller
   * must own the source; the destination may belong to anyone.
   * The returned transaction is the source account's TRANSFER_OUT entry.
   */
  async transfer(input: TransferInput, idempotencyKey: string): Promise<Transaction> {
    return unwrap<Transaction>(
      await client.POST('/api/transfers', {
        params: { header: { 'Idempotency-Key': idempotencyKey } },
        body: input,
      }),
    )
  },

  /** One page of an account's history, oldest first. `page` is zero-based. */
  async transactionHistory(
    accountNumber: string,
    page = 0,
    size = 20,
  ): Promise<TransactionPage> {
    return unwrap<TransactionPage>(
      await client.GET('/api/accounts/{accountNumber}/transactions', {
        params: { path: { accountNumber }, query: { page, size } },
      }),
    )
  },
}
