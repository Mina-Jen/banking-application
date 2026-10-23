/**
 * The app-facing DTO types, derived from the generated OpenAPI schema.
 *
 * WHY THIS FILE EXISTS
 * --------------------
 * The backend's DTOs are Java records, so every component is always present in
 * the JSON: `AccountResponse` cannot be serialized without `accountNumber`.
 * But springdoc only writes a `required` list into the OpenAPI document when a
 * field is annotated (for example `@Schema(requiredMode = REQUIRED)`), and
 * these records are not annotated. `openapi-typescript` therefore generates
 * every property as optional — `accountNumber?: string`.
 *
 * Left alone, that would force `account.accountNumber ?? ''` noise through the
 * whole UI and would hide genuine mistakes. `Defined<T>` removes the optionality
 * instead, so the types say what the backend actually sends.
 *
 * This is a frontend-side correction, not a change to the contract. The
 * generated `schema.d.ts` stays byte-for-byte what the backend produced. The
 * alternative — annotating the DTOs so the spec itself lists required fields —
 * is the better long-term fix because it also improves Swagger UI, but it means
 * editing Phase 2 Java files, which is out of scope here.
 */

import type { components } from './schema'

/**
 * Recursively strips the `?` modifier. Homomorphic mapped types keep arrays as
 * arrays, so `transactions?: TransactionResponse[]` becomes a required array
 * of required transactions.
 */
type Defined<T> = T extends object ? { [K in keyof T]-?: Defined<T[K]> } : T

export type Account = Defined<components['schemas']['AccountResponse']>
export type AccountPage = Defined<components['schemas']['AccountPageResponse']>
export type Transaction = Defined<components['schemas']['TransactionResponse']>
export type TransactionPage = Defined<components['schemas']['TransactionPageResponse']>
export type RegisteredUser = Defined<components['schemas']['RegisterResponse']>
export type TokenPair = Defined<components['schemas']['TokenResponse']>

/** Transaction types as the backend names them in TransactionType.java. */
export type TransactionType = 'DEPOSIT' | 'WITHDRAW' | 'TRANSFER_OUT' | 'TRANSFER_IN'
