import { act, renderHook } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { ReactNode } from 'react'
import { AuthProvider, useAuth } from './AuthContext'
import { sessionStore } from './tokenStorage'
import { bankingApi } from '../api/banking'
import { ApiError } from '../api/errors'
import { fakeAccessToken } from '../test/helpers'

vi.mock('../api/banking', () => ({
  bankingApi: {
    login: vi.fn(),
    register: vi.fn(),
  },
}))

const login = vi.mocked(bankingApi.login)
const register = vi.mocked(bankingApi.register)

const TOKENS = {
  accessToken: fakeAccessToken({ username: 'alice' }),
  refreshToken: 'refresh-1',
  tokenType: 'Bearer',
  expiresInSeconds: 900,
}

function wrapper({ children }: { children: ReactNode }) {
  return <AuthProvider>{children}</AuthProvider>
}

beforeEach(() => {
  vi.clearAllMocks()
  localStorage.clear()
  sessionStore.clear()
})

describe('useAuth', () => {
  it('starts signed out', () => {
    const { result } = renderHook(() => useAuth(), { wrapper })

    expect(result.current.isAuthenticated).toBe(false)
    expect(result.current.username).toBe('')
    expect(result.current.isAdmin).toBe(false)
  })

  it('throws when used outside a provider, instead of failing silently', () => {
    expect(() => renderHook(() => useAuth())).toThrow(/inside an <AuthProvider>/)
  })

  it('signIn stores the token pair and exposes the username', async () => {
    login.mockResolvedValue(TOKENS)
    const { result } = renderHook(() => useAuth(), { wrapper })

    await act(async () => {
      await result.current.signIn('alice', 'correct-horse-battery')
    })

    expect(login).toHaveBeenCalledWith('alice', 'correct-horse-battery')
    expect(result.current.isAuthenticated).toBe(true)
    expect(result.current.username).toBe('alice')
    expect(result.current.session?.refreshToken).toBe('refresh-1')
  })

  it('signIn leaves the user signed out when the backend rejects the password', async () => {
    login.mockRejectedValue(new ApiError(401, 'Unauthorized', 'Invalid username or password.'))
    const { result } = renderHook(() => useAuth(), { wrapper })

    await expect(
      act(async () => {
        await result.current.signIn('alice', 'wrong')
      }),
    ).rejects.toThrow('Invalid username or password.')

    expect(result.current.isAuthenticated).toBe(false)
  })

  it('signUp signs straight in when both calls succeed', async () => {
    register.mockResolvedValue({ username: 'alice', role: 'CUSTOMER' })
    login.mockResolvedValue(TOKENS)
    const { result } = renderHook(() => useAuth(), { wrapper })

    let outcome = null
    await act(async () => {
      outcome = await result.current.signUp('alice', 'correct-horse-battery')
    })

    expect(outcome).toEqual({ outcome: 'signed-in' })
    expect(result.current.isAuthenticated).toBe(true)
    // /register returns only {username, role}, so the second call is required.
    expect(register).toHaveBeenCalledTimes(1)
    expect(login).toHaveBeenCalledTimes(1)
  })

  it('signUp always asks for a CUSTOMER account and never sends a role', async () => {
    register.mockResolvedValue({ username: 'alice', role: 'CUSTOMER' })
    login.mockResolvedValue(TOKENS)
    const { result } = renderHook(() => useAuth(), { wrapper })

    await act(async () => {
      await result.current.signUp('alice', 'correct-horse-battery')
    })

    expect(register).toHaveBeenCalledWith('alice', 'correct-horse-battery')
  })

  /**
   * The registration succeeded but signing into it did not. Telling the user to
   * register again would produce a 409, so the page sends them to /login.
   */
  it('signUp reports registered-only when the follow-up login fails', async () => {
    register.mockResolvedValue({ username: 'alice', role: 'CUSTOMER' })
    login.mockRejectedValue(new ApiError(429, 'Too Many Requests', 'Too many failed login attempts.'))
    const { result } = renderHook(() => useAuth(), { wrapper })

    let outcome = null
    await act(async () => {
      outcome = await result.current.signUp('alice', 'correct-horse-battery')
    })

    expect(outcome).toEqual({ outcome: 'registered-only' })
    expect(result.current.isAuthenticated).toBe(false)
  })

  it('signUp propagates a taken username', async () => {
    register.mockRejectedValue(
      new ApiError(409, 'Conflict', 'Username alice is already taken.'),
    )
    const { result } = renderHook(() => useAuth(), { wrapper })

    await expect(
      act(async () => {
        await result.current.signUp('alice', 'correct-horse-battery')
      }),
    ).rejects.toThrow('Username alice is already taken.')

    expect(login).not.toHaveBeenCalled()
  })

  it('signOut clears the session', async () => {
    login.mockResolvedValue(TOKENS)
    const { result } = renderHook(() => useAuth(), { wrapper })

    await act(async () => {
      await result.current.signIn('alice', 'correct-horse-battery')
    })
    expect(result.current.isAuthenticated).toBe(true)

    act(() => {
      result.current.signOut()
    })

    expect(result.current.isAuthenticated).toBe(false)
    expect(sessionStore.load()).toBeNull()
  })

  it('exposes isAdmin only for an ADMIN token', async () => {
    login.mockResolvedValue({
      ...TOKENS,
      accessToken: fakeAccessToken({ username: 'admin', role: 'ADMIN', uid: 3 }),
    })
    const { result } = renderHook(() => useAuth(), { wrapper })

    await act(async () => {
      await result.current.signIn('admin', 'operator-password')
    })

    expect(result.current.role).toBe('ADMIN')
    expect(result.current.isAdmin).toBe(true)
  })
})
