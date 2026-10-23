/**
 * Test setup, run before each test file.
 */
import '@testing-library/jest-dom/vitest'
import { cleanup } from '@testing-library/react'
import { afterEach } from 'vitest'

/**
 * Unmounts the previous test's component tree.
 *
 * Testing Library normally registers this itself, but only when Vitest runs
 * with `globals: true`. This project imports `describe`/`it`/`expect`
 * explicitly instead, so the automatic hook is never installed and — without
 * this — every render accumulates in document.body. A later `screen.getByText`
 * then finds several matches, or the wrong test's markup.
 */
afterEach(() => {
  cleanup()
})
