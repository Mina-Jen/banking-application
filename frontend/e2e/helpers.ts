import { randomUUID } from 'node:crypto'
import { expect, type Page } from '@playwright/test'

/**
 * Shared steps for the browser specs.
 *
 * Every run creates its own users so tests never collide with the previous
 * run's rows in `banking_e2e`, and so a failure never leaves a name that the
 * next run would find taken. Account numbers are not invented here: the backend
 * assigns them, so `openAccount` returns the one it handed out.
 */

export const PASSWORD = 'e2e-password-123'

/** Matches the alert the dashboard shows after an account is opened. */
const OPENED = /^Account (\d{11}) opened with /

/** A username that satisfies the backend's lowercase pattern. */
export function uniqueUser(): string {
  return `e2e.${randomUUID().replace(/-/g, '').slice(0, 10)}`
}

/** Registers and lands on the dashboard, already signed in. */
export async function register(page: Page, username: string): Promise<void> {
  await page.goto('/register')
  await page.getByLabel('Username', { exact: true }).fill(username)
  await page.getByLabel('Password', { exact: true }).fill(PASSWORD)
  await page.getByLabel('Repeat password', { exact: true }).fill(PASSWORD)
  await page.getByRole('button', { name: 'Register' }).click()

  // Registration signs the new customer in automatically.
  await expect(page.getByRole('heading', { name: 'Your accounts' })).toBeVisible()
}

/**
 * Opens an account through the dashboard form and returns the number the bank
 * assigned for it. The section is scoped because an administrator also gets a
 * dashboard "Account number" field for lookups, and the two labels would
 * otherwise collide.
 */
export async function openAccount(
  page: Page,
  account: { name: string; deposit: string },
): Promise<string> {
  await page.getByRole('button', { name: 'Open an account' }).click()
  const panel = page.locator('section[aria-labelledby="open-account-heading"]')

  // A customer has no way to offer a number, so no request of theirs can be
  // told apart from a collision on a number that already exists.
  await expect(panel.getByLabel('Account number', { exact: true })).toHaveCount(0)

  await panel.getByLabel('Account holder name', { exact: true }).fill(account.name)
  await panel.getByLabel('Opening deposit', { exact: true }).fill(account.deposit)
  await panel.getByRole('button', { name: 'Open account' }).click()

  const notice = page.getByText(OPENED)
  await expect(notice).toBeVisible()

  const assigned = OPENED.exec((await notice.textContent()) ?? '')
  if (assigned === null) {
    throw new Error(`The alert did not carry an assigned number: ${await notice.textContent()}`)
  }
  return assigned[1]
}

/** Follows the row's Manage link and waits for the account page. */
export async function manageAccount(
  page: Page,
  accountNumber: string,
  accountName: string,
): Promise<void> {
  // The card face masks the number; its data attribute gives tests a stable
  // machine-readable identity without exposing the number as visible text.
  const row = page.locator(`li[data-account-number="${accountNumber}"]`)
  await row.getByRole('link', { name: 'Manage' }).click()

  await expect(page).toHaveURL(new RegExp(`/accounts/${accountNumber}$`))
  await expect(page.getByRole('heading', { name: accountName })).toBeVisible()
}
