import { beforeEach, describe, expect, it, vi } from 'vitest'
import { sessionStore } from './tokenStorage'
import { fakeAccessToken } from '../test/helpers'

const TOKEN = fakeAccessToken({ username: 'alice', role: 'CUSTOMER', uid: 7 })
const OTHER = fakeAccessToken({ username: 'bob', role: 'ADMIN', uid: 9, exp: 9_999_999_999 })

beforeEach(() => {
  localStorage.clear()
  sessionStore.clear()
})

describe('sessionStore', () => {
  it('round-trips a token pair and adds the decoded username and role', () => {
    sessionStore.save({ accessToken: TOKEN, refreshToken: 'refresh-1' })

    const loaded = sessionStore.load()
    expect(loaded?.username).toBe('alice')
    expect(loaded?.role).toBe('CUSTOMER')
    expect(loaded?.refreshToken).toBe('refresh-1')
  })

  it('survives a reload by reading back from localStorage', () => {
    sessionStore.save({ accessToken: TOKEN, refreshToken: 'refresh-1' })

    // Simulate a fresh page: the in-memory cache is gone, storage is not.
    expect(JSON.parse(localStorage.getItem('banking.session') ?? 'null')).toEqual({
      accessToken: TOKEN,
      refreshToken: 'refresh-1',
    })
  })

  it('refuses to store a token it cannot decode', () => {
    const stored = sessionStore.save({ accessToken: 'garbage', refreshToken: 'refresh-1' })

    expect(stored).toBeNull()
    expect(sessionStore.load()).toBeNull()
    expect(localStorage.getItem('banking.session')).toBeNull()
  })

  it('clear() empties both storage and memory', () => {
    sessionStore.save({ accessToken: TOKEN, refreshToken: 'refresh-1' })
    sessionStore.clear()

    expect(sessionStore.load()).toBeNull()
    expect(localStorage.getItem('banking.session')).toBeNull()
  })

  /**
   * This is the invariant that makes `useSyncExternalStore` usable: the snapshot
   * function must return the SAME object while nothing has changed, or React
   * re-renders forever.
   */
  it('returns a stable snapshot between writes', () => {
    sessionStore.save({ accessToken: TOKEN, refreshToken: 'refresh-1' })

    expect(sessionStore.load()).toBe(sessionStore.load())
  })

  it('returns a new snapshot after a write, so React sees the change', () => {
    sessionStore.save({ accessToken: TOKEN, refreshToken: 'refresh-1' })
    const before = sessionStore.load()
    sessionStore.save({ accessToken: OTHER, refreshToken: 'refresh-2' })

    const after = sessionStore.load()
    expect(after).not.toBe(before)
    expect(after?.username).toBe('bob')
    expect(after?.role).toBe('ADMIN')
  })

  it('notifies subscribers on save and on clear, and stops after unsubscribe', () => {
    const listener = vi.fn()
    const unsubscribe = sessionStore.subscribe(listener)

    sessionStore.save({ accessToken: TOKEN, refreshToken: 'refresh-1' })
    expect(listener).toHaveBeenLastCalledWith(expect.objectContaining({ username: 'alice' }))

    sessionStore.clear()
    expect(listener).toHaveBeenLastCalledWith(null)
    expect(listener).toHaveBeenCalledTimes(2)

    unsubscribe()
    sessionStore.save({ accessToken: OTHER, refreshToken: 'refresh-2' })
    expect(listener).toHaveBeenCalledTimes(2)
  })

  /**
   * A corrupt or hand-edited storage entry must mean "signed out", not a crash
   * on the login screen. `vi.resetModules()` + a fresh import simulates a real
   * page load, which is the only time this entry is read from disk; without it
   * the in-memory cache would answer and the test would prove nothing.
   */
  it.each(['{ not json', 'null', '[1,2,3]', '{"accessToken":5}', '""'])(
    'treats the corrupt entry %s as signed out',
    async (corrupt) => {
      localStorage.setItem('banking.session', corrupt)
      vi.resetModules()

      const fresh = (await import('./tokenStorage')).sessionStore

      expect(fresh.load()).toBeNull()
    },
  )
})
