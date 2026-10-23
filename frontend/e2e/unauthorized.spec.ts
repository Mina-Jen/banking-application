import { expect, test } from '@playwright/test'
import { openAccount, register, uniqueUser } from './helpers'

test('signed-out visitors and other customers cannot reach an account', async ({
  page,
  browser,
}) => {
  // 1. No session at all: the route guard sends the visitor to the login page
  // instead of rendering anything about the account.
  await page.goto('/accounts/200001')
  await expect(page).toHaveURL(/\/login$/)
  await expect(page.getByRole('heading', { name: 'Sign in' })).toBeVisible()

  // 2. Alice registers and owns an account. The number is the one the bank gave
  // her, not one she picked.
  await register(page, uniqueUser())
  const accountNumber = await openAccount(page, { name: 'Alice Private', deposit: '10' })

  await page.getByRole('button', { name: 'Sign out' }).click()
  await expect(page).toHaveURL(/\/login$/)

  // 3. Bob is a valid customer, but not the owner. His dashboard is driven by
  // GET /api/accounts, which is scoped to the caller, so Alice's account is not
  // in his list and he has no lookup box for guessing it. Every route to the
  // account answers 404 with the same text as a number that was never opened.
  const bobContext = await browser.newContext()
  try {
    const bob = await bobContext.newPage()
    await register(bob, uniqueUser())

    await expect(bob.getByRole('heading', { name: 'Look up an account' })).toHaveCount(0)
    await expect(bob.getByLabel('Account number', { exact: true })).toHaveCount(0)
    await expect(bob.getByText('No accounts yet. Open one to get started.')).toBeVisible()

    // A typed URL is no different: the page shows the fatal state, not the money.
    await bob.goto(`/accounts/${accountNumber}`)
    await expect(bob.getByText('This account is not available')).toBeVisible()
    await expect(bob.getByText('Account not found.')).toBeVisible()
    await expect(bob.getByRole('heading', { name: 'Alice Private' })).not.toBeVisible()
    // The refusal must not confirm that this account number exists.
    await expect(bob.getByText(accountNumber, { exact: true })).toHaveCount(0)
  } finally {
    await bobContext.close()
  }
})
