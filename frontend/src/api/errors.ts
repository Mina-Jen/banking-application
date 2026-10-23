/**
 * The single error shape every backend failure uses.
 *
 * Mirrors backend/.../web/dto/ErrorResponse.java. It is written by hand rather
 * than generated because springdoc emits `@ApiResponse(responseCode = "422")`
 * without a content schema, so ErrorResponse never appears in the OpenAPI
 * document. Keep this in step with the Java record.
 */
export interface ErrorBody {
  status: number
  error: string
  message: string
}

/** Type guard for the backend's error body. */
function isErrorBody(value: unknown): value is ErrorBody {
  if (typeof value !== 'object' || value === null) {
    return false
  }
  const candidate = value as Partial<ErrorBody>
  return typeof candidate.message === 'string' && candidate.message.length > 0
}

/**
 * A rejected API call. `status` is the HTTP code, so callers can branch on
 * 401/404/409/422/429, and `message` is the backend's own explanation, which
 * is already written for end users.
 */
export class ApiError extends Error {
  readonly status: number
  readonly error: string
  /** Seconds the client should wait before retrying; set only on 429. */
  readonly retryAfterSeconds: number | null

  constructor(status: number, error: string, message: string, retryAfterSeconds: number | null = null) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.error = error
    this.retryAfterSeconds = retryAfterSeconds
  }

  /**
   * Builds the error from a response.
   *
   * @param parsedBody the already-parsed error body, which openapi-fetch
   *   provides as its `error` field. It has to be passed in: by the time a
   *   caller sees the Response, openapi-fetch has CONSUMED its body, so
   *   `response.json()` or `response.clone()` would throw "Body already used"
   *   and every message would collapse to a generic "Request failed with
   *   status 422". The backend's wording is the useful part, so it is worth
   *   threading through. The response is only read directly when no parsed
   *   body was supplied, which is the case for a response this app fetched
   *   itself.
   */
  static async from(response: Response, parsedBody?: unknown): Promise<ApiError> {
    const fallback = response.statusText || `Request failed with status ${response.status}`
    let message = fallback
    let error = response.statusText || 'Error'

    let body: unknown = parsedBody
    if (body === undefined && !response.bodyUsed) {
      try {
        body = await response.clone().json()
      } catch {
        // Not JSON: a proxy error page, an empty body, or a network appliance.
        // Keep the status-text message rather than throwing from the error path.
      }
    }

    if (isErrorBody(body)) {
      message = body.message
      if (typeof body.error === 'string' && body.error.length > 0) {
        error = body.error
      }
    }

    const retryAfter = response.headers.get('Retry-After')
    const retryAfterSeconds = retryAfter === null ? null : Number.parseInt(retryAfter, 10)

    return new ApiError(
      response.status,
      error,
      message,
      Number.isFinite(retryAfterSeconds) ? retryAfterSeconds : null,
    )
  }
}

/**
 * Turns anything thrown into a message safe to show a user. Network failures
 * are not `ApiError`, so this is the one place that decides their wording.
 */
export function errorMessage(cause: unknown): string {
  if (cause instanceof ApiError) {
    return cause.message
  }
  if (cause instanceof TypeError) {
    // fetch() rejects with TypeError when the server is unreachable.
    return 'Cannot reach the backend. Is it running on port 18080?'
  }
  if (cause instanceof Error && cause.message) {
    return cause.message
  }
  return 'Something went wrong. Please try again.'
}
