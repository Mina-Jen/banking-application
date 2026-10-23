import { act, renderHook } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { useIdempotencyKey } from './useIdempotencyKey'
import { newIdempotencyKey } from './idempotency'

describe('newIdempotencyKey', () => {
  it('produces a fresh UUID inside the 100 character backend limit', () => {
    const key = newIdempotencyKey()
    expect(key).toMatch(
      /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i,
    )
    expect(key.length).toBeLessThanOrEqual(100)
    expect(newIdempotencyKey()).not.toBe(key)
  })
})

describe('useIdempotencyKey', () => {
  it('returns the SAME key on every acquire until it is reset', () => {
    const { result } = renderHook(() => useIdempotencyKey())

    let first = ''
    let second = ''
    act(() => {
      first = result.current.acquire()
      second = result.current.acquire()
    })

    // This is the property that stops a double payment: a retry of the same
    // intent must reuse the key so the backend replays instead of re-running.
    expect(second).toBe(first)
  })

  it('issues a different key after a success clears it', () => {
    const { result } = renderHook(() => useIdempotencyKey())

    let first = ''
    let second = ''
    act(() => {
      first = result.current.acquire()
      result.current.reset()
      second = result.current.acquire()
    })

    expect(second).not.toBe(first)
  })

  it('keeps the key stable across unrelated re-renders', () => {
    const { result, rerender } = renderHook(() => useIdempotencyKey())

    let first = ''
    let afterRerender = ''
    act(() => {
      first = result.current.acquire()
    })
    rerender()
    act(() => {
      afterRerender = result.current.acquire()
    })

    expect(afterRerender).toBe(first)
  })
})
