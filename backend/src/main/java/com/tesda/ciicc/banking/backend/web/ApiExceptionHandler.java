package com.tesda.ciicc.banking.backend.web;

import com.tesda.ciicc.banking.backend.auth.InvalidCredentialsException;
import com.tesda.ciicc.banking.backend.auth.InvalidRefreshTokenException;
import com.tesda.ciicc.banking.backend.auth.RateLimitExceededException;
import com.tesda.ciicc.banking.backend.auth.UsernameAlreadyTakenException;
import com.tesda.ciicc.banking.backend.exception.AccountAlreadyExistsException;
import com.tesda.ciicc.banking.backend.exception.AccountNotFoundException;
import com.tesda.ciicc.banking.backend.exception.BankingException;
import com.tesda.ciicc.banking.backend.web.dto.ErrorResponse;
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
 * rate-limited logins.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(AccountNotFoundException.class)
    public ResponseEntity<ErrorResponse> notFound(AccountNotFoundException e) {
        return build(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(AccountAlreadyExistsException.class)
    public ResponseEntity<ErrorResponse> duplicateAccount(AccountAlreadyExistsException e) {
        return build(HttpStatus.CONFLICT, e.getMessage());
    }

    @ExceptionHandler(IdempotencyKeyConflictException.class)
    public ResponseEntity<ErrorResponse> idempotencyConflict(IdempotencyKeyConflictException e) {
        return build(HttpStatus.CONFLICT, e.getMessage());
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

    /** Thrown by the service layer when a CUSTOMER touches an account they do not own. */
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

    private static ResponseEntity<ErrorResponse> build(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(ErrorResponse.of(status, message));
    }
}
