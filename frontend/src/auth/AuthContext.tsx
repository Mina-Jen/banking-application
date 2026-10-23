/**
 * Who is signed in, and how to sign in or out.
 *
 * The provider does not hold its own copy of the session. It subscribes to
 * `sessionStore` with `useSyncExternalStore`, so the UI updates no matter who
 * changed the session: the login form, the API client's silent token refresh,
 * a failed refresh that cleared it, or another browser tab.
 */

import {
  createContext,
  useCallback,
  useContext,
  useMemo,
  useSyncExternalStore,
  type ReactNode,
} from 'react'
import { bankingApi } from '../api/banking'
import type { Role } from './jwt'
import { sessionStore, type StoredSession } from './tokenStorage'

/** What happened after a registration attempt. */
export type SignUpResult =
  /** The login was created AND the automatic sign-in worked. */
  | { outcome: 'signed-in' }
  /**
   * The login was created but signing into it immediately failed. The user must
   * go to the login page rather than register again, which would 409.
   */
  | { outcome: 'registered-only' }

export interface AuthContextValue {
  session: StoredSession | null
  isAuthenticated: boolean
  username: string
  role: Role
  isAdmin: boolean
  signIn(username: string, password: string): Promise<void>
  signUp(username: string, password: string): Promise<SignUpResult>
  signOut(): void
}

const AuthContext = createContext<AuthContextValue | null>(null)

export function AuthProvider({ children }: { children: ReactNode }) {
  // load() returns a cached object, which is what makes it a legal snapshot.
  const session = useSyncExternalStore(sessionStore.subscribe, sessionStore.load, sessionStore.load)

  const signIn = useCallback(async (username: string, password: string) => {
    const tokens = await bankingApi.login(username, password)
    sessionStore.save({ accessToken: tokens.accessToken, refreshToken: tokens.refreshToken })
  }, [])

  const signUp = useCallback(async (username: string, password: string): Promise<SignUpResult> => {
    const registered = await bankingApi.register(username, password)
    try {
      // /api/auth/register deliberately returns only {username, role} — no
      // tokens — so a second call is needed to actually get a session.
      const tokens = await bankingApi.login(registered.username, password)
      sessionStore.save({ accessToken: tokens.accessToken, refreshToken: tokens.refreshToken })
      return { outcome: 'signed-in' }
    } catch {
      return { outcome: 'registered-only' }
    }
  }, [])

  const signOut = useCallback(() => {
    // The backend has no logout or revocation endpoint, so this only forgets the
    // tokens locally: an attacker who already copied the refresh token keeps it
    // until it expires (7 days). Server-side revocation is a backend change.
    sessionStore.clear()
  }, [])

  const value = useMemo<AuthContextValue>(
    () => ({
      session,
      isAuthenticated: session !== null,
      username: session?.username ?? '',
      role: session?.role ?? 'CUSTOMER',
      isAdmin: session?.role === 'ADMIN',
      signIn,
      signUp,
      signOut,
    }),
    [session, signIn, signUp, signOut],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

/** Reads the session. Throws when used outside an AuthProvider, by design. */
export function useAuth(): AuthContextValue {
  const context = useContext(AuthContext)
  if (context === null) {
    throw new Error('useAuth must be used inside an <AuthProvider>.')
  }
  return context
}
