import { expect, test } from '@playwright/test'
import {
  manageAccount,
  openAccount,
  register,
  uniqueUser,
} from './helpers'

test('a new user registers, opens an account, and deposits into it', async ({ page }) => {
  await register(page, uniqueUser())
  const accountNumber = await openAccount(page, {
    name: 'E2E Deposit Account',
    deposit: '0',
  })
  await manageAccount(page, accountNumber, 'E2E Deposit Account')

  await expect(page.getByText('0 transactions, oldest first')).toBeVisible()

  // Typing an amount only opens the dialog; no money moves before Confirm.
  await page.getByLabel('Amount to deposit', { exact: true }).fill('1250.75')
  await page.getByRole('button', { name: 'Deposit', exact: true }).click()

  const dialog = page.getByRole('dialog', { name: 'Confirm deposit' })
  await expect(dialog).toBeVisible()
  await expect(dialog.getByText('₱1,250.75', { exact: true })).toBeVisible()
  await expect(dialog.getByText(accountNumber, { exact: true })).toBeVisible()

  await dialog.getByRole('button', { name: 'Deposit ₱1,250.75' }).click()

  // The receipt carries the backend's reference and the new balance.
  await expect(page.getByText(/recorded\. Reference TXN-/)).toBeVisible()
  await expect(page.getByText('New balance ₱1,250.75.')).toBeVisible()
  await expect(page.getByText('1 transaction, oldest first')).toBeVisible()
})
