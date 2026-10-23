package com.tesda.ciicc.banking.backend.web;

import com.tesda.ciicc.banking.backend.ai.AssistantUnavailableException;
import com.tesda.ciicc.banking.backend.ai.CategorySuggestionUnavailableException;
import com.tesda.ciicc.banking.backend.auth.InvalidCredentialsException;
import com.tesda.ciicc.banking.backend.auth.InvalidRefreshTokenException;
import com.tesda.ciicc.banking.backend.auth.RateLimitExceededException;
import com.tesda.ciicc.banking.backend.auth.UsernameAlreadyTakenException;
import com.tesda.ciicc.banking.backend.exception.AccountAlreadyExistsException;
import com.tesda.ciicc.banking.backend.exception.AccountNotFoundException;
import com.tesda.ciicc.banking.backend.exception.AccountNumberAllocationException;
import com.tesda.ciicc.banking.backend.exception.AnomalyFlagAlreadyReviewedException;
import com.tesda.ciicc.banking.backend.exception.AnomalyFlagNotFoundException;
import com.tesda.ciicc.banking.backend.exception.BankingException;
import com.tesda.ciicc.banking.backend.exception.TransactionNotFoundException;
import com.tesda.ciicc.banking.backend.web.dto.ErrorResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * Maps service exceptions to HTTP status codes:
 * 404 for unknown accounts, 409 for conflicts, 422 for rejected business
 * rules (the domain exceptions are checked, so {@link BankingException} is
 * the fallback), and 400 for malformed requests. Phase 2 adds the auth
 * statuses: 401 for bad credentials/tokens, 403 for denied access, 429 for
 * rate-limited logins. {@link #unexpected(Exception)} is the last resort, so
 * every response — including an unexpected 500 — uses the same
 * {@link ErrorResponse} body.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    /** Shown when nothing more specific matched. The real cause goes to the log. */
    private static final String GENERIC_FAILURE = "Something went wrong. Please try again later.";

    @ExceptionHandler(AccountNotFoundException.class)
    public ResponseEntity<ErrorResponse> notFound(AccountNotFoundException e) {
        return build(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(TransactionNotFoundException.class)
    public ResponseEntity<ErrorResponse> transactionNotFound(TransactionNotFoundException e) {
        return build(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(AnomalyFlagNotFoundException.class)
    public ResponseEntity<ErrorResponse> anomalyFlagNotFound(AnomalyFlagNotFoundException e) {
        return build(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(AccountAlreadyExistsException.class)
    public ResponseEntity<ErrorResponse> duplicateAccount(AccountAlreadyExistsException e) {
        return build(HttpStatus.CONFLICT, e.getMessage());
    }

    @ExceptionHandler(AnomalyFlagAlreadyReviewedException.class)
    public ResponseEntity<ErrorResponse> anomalyFlagAlreadyReviewed(AnomalyFlagAlreadyReviewedException e) {
        return build(HttpStatus.CONFLICT, e.getMessage());
    }

    /**
     * The bank could not hand out an account number. Nothing in the request is
     * wrong and nothing for the caller to correct, so this is a 503 with a
     * retry — never the 409 an ADMIN sees for a number that is taken, which
     * would turn a customer's own request into a test of "does this number
     * exist?".
     */
    @ExceptionHandler(AccountNumberAllocationException.class)
    public ResponseEntity<ErrorResponse> allocationFailed(AccountNumberAllocationException e) {
        return build(HttpStatus.SERVICE_UNAVAILABLE, e.getMessage());
    }

    @ExceptionHandler(IdempotencyKeyConflictException.class)
    public ResponseEntity<ErrorResponse> idempotencyConflict(IdempotencyKeyConflictException e) {
        return build(HttpStatus.CONFLICT, e.getMessage());
    }

    /**
     * The AI categorizer is an optional dependency: a missing key or a failed
     * upstream call is a problem with the bank's tooling, not with the
     * caller's request, so it answers 503 and nothing about the request is
     * changed. The handler passes only the safe message prepared by the client.
     */
    @ExceptionHandler(CategorySuggestionUnavailableException.class)
    public ResponseEntity<ErrorResponse> categorySuggestionUnavailable(CategorySuggestionUnavailableException e) {
        return build(HttpStatus.SERVICE_UNAVAILABLE, e.getMessage());
    }

    /** Same optional-dependency rule as category suggestions: the assistant is not the caller's fault. */
    @ExceptionHandler(AssistantUnavailableException.class)
    public ResponseEntity<ErrorResponse> assistantUnavailable(AssistantUnavailableException e) {
        return build(HttpStatus.SERVICE_UNAVAILABLE, e.getMessage());
    }

    /** Fallback for every domain exception: rejected business rule, nothing wrong with the request format. */
    @ExceptionHandler(BankingException.class)
    public ResponseEntity<ErrorResponse> unprocessable(BankingException e) {
        return build(HttpStatus.UNPROCESSABLE_ENTITY, e.getMessage());
    }

    @ExceptionHandler(IdempotencyKeyException.class)
    public ResponseEntity<ErrorResponse> badIdempotencyKey(IdempotencyKeyException e) {
        return build(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ErrorResponse> missingHeader(MissingRequestHeaderException e) {
        return build(HttpStatus.BAD_REQUEST, "Required header '" + e.getHeaderName() + "' is missing.");
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> unreadableBody(HttpMessageNotReadableException e) {
        return build(HttpStatus.BAD_REQUEST, "Request body is not valid JSON.");
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> badParameter(MethodArgumentTypeMismatchException e) {
        return build(HttpStatus.BAD_REQUEST,
                "Parameter '" + e.getName() + "' has an invalid value.");
    }

    // =====================================================================
    //  Phase 2: authentication and authorization
    // =====================================================================

    /** Same message for unknown username and wrong password, so neither can be probed. */
    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ErrorResponse> invalidCredentials(InvalidCredentialsException e) {
        return build(HttpStatus.UNAUTHORIZED, e.getMessage());
    }

    @ExceptionHandler(InvalidRefreshTokenException.class)
    public ResponseEntity<ErrorResponse> invalidRefreshToken(InvalidRefreshTokenException e) {
        return build(HttpStatus.UNAUTHORIZED, e.getMessage());
    }

    @ExceptionHandler(UsernameAlreadyTakenException.class)
    public ResponseEntity<ErrorResponse> usernameTaken(UsernameAlreadyTakenException e) {
        return build(HttpStatus.CONFLICT, e.getMessage());
    }

    /**
     * Leftover denials from the security chain itself (an @PreAuthorize rule,
     * or the filter's access-denied handler). Account ownership no longer
     * comes here: BankingService answers a foreign account with the same 404
     * as a missing one, so the API cannot be used to enumerate accounts.
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> accessDenied(AccessDeniedException e) {
        return build(HttpStatus.FORBIDDEN, e.getMessage());
    }

    @ExceptionHandler(DisabledException.class)
    public ResponseEntity<ErrorResponse> disabledLogin(DisabledException e) {
        return build(HttpStatus.FORBIDDEN, e.getMessage());
    }

    /** Retry-After tells the client how long to wait before the next attempt. */
    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<ErrorResponse> rateLimited(RateLimitExceededException e) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(e.getRetryAfterSeconds()))
                .body(ErrorResponse.of(HttpStatus.TOO_MANY_REQUESTS, e.getMessage()));
    }

    /**
     * Catch-all: nothing may leave this application as a stack-trace page, a
     * half-written response, or a message that quoting the failure would
     * expose. Spring's own request errors (405 wrong method, 404 unknown path,
     * 415 unreadable content type) keep their status so a mistaken call is still
     * described correctly; anything else is logged in full here and answered
     * with a generic 500, because its message can carry SQL, file paths, or a
     * token.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> unexpected(Exception e) {
        if (e instanceof org.springframework.web.ErrorResponse springError) {
            HttpStatus status = HttpStatus.resolve(springError.getStatusCode().value());
            if (status != null && status.is4xxClientError()) {
                log.warn("Rejected request: {} {}", status.value(), e.getMessage());
                return build(status, status.getReasonPhrase() + ".");
            }
        }
        log.error("Unhandled exception while serving a request", e);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, GENERIC_FAILURE);
    }

    private static ResponseEntity<ErrorResponse> build(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(ErrorResponse.of(status, message));
    }
}
