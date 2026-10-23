/**
 * Where the logged-in session lives.
 *
 * The token pair is kept in localStorage so a page reload does not log the user
 * out. That is a deliberate, documented trade-off: localStorage is readable by
 * any script running on the page, so it is only acceptable together with a
 * strict Content-Security-Policy and no third-party scripts. The stronger
 * alternative is an httpOnly cookie, which needs a backend change (cookie
 * issuance plus CSRF protection) and is out of scope for this phase.
 *
 * This module also owns a tiny observer list. The API client writes here when
 * it silently refreshes a token or when a refresh fails, and `AuthProvider`
 * subscribes so the UI always matches the stored session without polling.
 *
 * The two invariants that make it safe to use from React:
 *   - `load()` returns a CACHED object, so it is a valid `useSyncExternalStore`
 *     snapshot. Returning a freshly parsed object every call would make React
 *     re-render forever.
 *   - the exported functions never use `this`, so they can be passed around
 *     detached (which is exactly what `useSyncExternalStore` does).
 */

import { decodeAccessToken, type Role } from './jwt'

export interface Session {
  accessToken: string
  refreshToken: string
}

export interface StoredSession extends Session {
  /** Username, decoded from the access token. */
  username: string
  role: Role
}

const STORAGE_KEY = 'banking.session'

type Listener = (session: StoredSession | null) => void

const listeners = new Set<Listener>()

let cached: StoredSession | null = null
let loaded = false

/** Enriches a raw token pair with the username and role decoded from the JWT. */
function withClaims(accessToken: string, refreshToken: string): StoredSession | null {
  const claims = decodeAccessToken(accessToken)
  if (!claims) {
    return null
  }
  return { accessToken, refreshToken, username: claims.sub, role: claims.role }
}

function readFromStorage(): StoredSession | null {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    if (!raw) {
      return null
    }
    const parsed = JSON.parse(raw) as Partial<Session>
    if (typeof parsed.accessToken !== 'string' || typeof parsed.refreshToken !== 'string') {
      return null
    }
    return withClaims(parsed.accessToken, parsed.refreshToken)
  } catch {
    // Corrupt entry, or storage blocked (private mode). Treat as signed out.
    return null
  }
}

function notify(session: StoredSession | null): void {
  for (const listener of listeners) {
    listener(session)
  }
}

function load(): StoredSession | null {
  if (!loaded) {
    cached = readFromStorage()
    loaded = true
  }
  return cached
}

/** Stores a token pair. Called after login and after a successful refresh. */
function save(session: Session): StoredSession | null {
  const stored = withClaims(session.accessToken, session.refreshToken)
  if (!stored) {
    // An undecodable access token is a broken session; do not half-store it.
    clear()
    return null
  }
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(session))
  } catch {
    // Storage full or blocked. Keep the session in memory for this page load.
  }
  cached = stored
  loaded = true
  notify(stored)
  return stored
}

/** Drops the session and tells the UI to redirect to the login page. */
function clear(): void {
  try {
    localStorage.removeItem(STORAGE_KEY)
  } catch {
    // Nothing to remove.
  }
  cached = null
  loaded = true
  notify(null)
}

function subscribe(listener: Listener): () => void {
  listeners.add(listener)
  return () => {
    listeners.delete(listener)
  }
}

// Another tab logged in or out. The `storage` event fires only in the OTHER
// tabs, so this keeps every open tab agreeing on who is signed in instead of
// leaving one tab showing a session the backend has already been told to drop.
if (typeof window !== 'undefined') {
  window.addEventListener('storage', (event) => {
    if (event.key !== STORAGE_KEY) {
      return
    }
    loaded = false
    notify(load())
  })
}

export const sessionStore = { load, save, clear, subscribe }
