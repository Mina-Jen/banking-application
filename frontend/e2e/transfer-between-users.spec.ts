import { expect, test } from '@playwright/test'
import { manageAccount, openAccount, register, uniqueUser } from './helpers'

/**
 * Two separate browser contexts = two separate sessions (each context has its
 * own localStorage, so the two customers never share tokens or account lists).
 */
test('a transfer moves money from one customer to another', async ({ browser }) => {
  const aliceContext = await browser.newContext()
  const bobContext = await browser.newContext()

  try {
    const alice = await aliceContext.newPage()
    const bob = await bobContext.newPage()

    await register(alice, uniqueUser())
    const aliceNumber = await openAccount(alice, { name: 'Alice E2E', deposit: '100' })

    await register(bob, uniqueUser())
    const bobNumber = await openAccount(bob, { name: 'Bob E2E', deposit: '25' })

    // Each customer holds the number the bank assigned to them and nothing else
    // needs to be known to move money between them.
    expect(aliceNumber).not.toBe(bobNumber)

    await manageAccount(alice, aliceNumber, 'Alice E2E')

    await alice.getByLabel('To account number', { exact: true }).fill(bobNumber)
    await alice.getByLabel('Amount', { exact: true }).fill('30')
    await alice.getByRole('button', { name: 'Transfer', exact: true }).click()

    const dialog = alice.getByRole('dialog', { name: 'Confirm transfer' })
    await expect(dialog).toBeVisible()
    await expect(dialog.getByText('₱30.00', { exact: true })).toBeVisible()
    await expect(dialog.getByText(bobNumber, { exact: true })).toBeVisible()
    await dialog.getByRole('button', { name: 'Transfer ₱30.00' }).click()

    await expect(alice.getByText(new RegExp(`₱30\\.00 sent to ${bobNumber}\\.`))).toBeVisible()
    await expect(alice.getByText('New balance ₱70.00.')).toBeVisible()

    // Bob sees the money arrive in his own session. His dashboard re-reads
    // GET /api/accounts, which only ever returns accounts he owns.
    await bob.goto('/')
    const bobRow = bob.locator('li', { hasText: bobNumber })
    await expect(bobRow).toContainText('Bob E2E')
    await expect(bobRow.getByText('₱55.00')).toBeVisible()
  } finally {
    await aliceContext.close()
    await bobContext.close()
  }
})
