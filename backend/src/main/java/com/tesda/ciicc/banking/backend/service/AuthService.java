package com.tesda.ciicc.banking.backend.service;

import com.tesda.ciicc.banking.backend.auth.CurrentUser;
import com.tesda.ciicc.banking.backend.auth.InvalidCredentialsException;
import com.tesda.ciicc.banking.backend.auth.InvalidRefreshTokenException;
import com.tesda.ciicc.banking.backend.auth.JwtService;
import com.tesda.ciicc.banking.backend.auth.LoginRateLimiter;
import com.tesda.ciicc.banking.backend.auth.RateLimitExceededException;
import com.tesda.ciicc.banking.backend.auth.UsernameAlreadyTakenException;
import com.tesda.ciicc.banking.backend.exception.BankingValidationException;
import com.tesda.ciicc.banking.backend.model.Role;
import com.tesda.ciicc.banking.backend.model.User;
import com.tesda.ciicc.banking.backend.repository.UserRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Optional;

/**
 * Registration, login, and token refresh. Passwords are checked with BCrypt
 * (one-way hashing: the database stores a hash, never the password), tokens
 * come from {@link JwtService}, failed logins are throttled by
 * {@link LoginRateLimiter}, and every attempt lands in the audit trail.
 *
 * <p>Unknown username and wrong password throw the SAME exception with the
 * SAME message, so an attacker cannot use the error to find out which
 * usernames exist.</p>
 */
@Service
public class AuthService {

    private static final int MIN_PASSWORD_LENGTH = 8;
    private static final int MAX_PASSWORD_LENGTH = 72;   // BCrypt ignores bytes after the 72nd
    private static final String USERNAME_PATTERN = "[a-z0-9._-]{3,50}";

    /** Access + refresh pair handed to the web layer, which shapes the JSON. */
    public record SessionTokens(String accessToken, String refreshToken, long accessTtlSeconds) {
    }

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final JwtService jwt;
    private final LoginRateLimiter rateLimiter;
    private final AuditService audit;

    public AuthService(UserRepository users, PasswordEncoder encoder, JwtService jwt,
                       LoginRateLimiter rateLimiter, AuditService audit) {
        this.users = users;
        this.encoder = encoder;
        this.jwt = jwt;
        this.rateLimiter = rateLimiter;
        this.audit = audit;
    }

    /**
     * Creates a CUSTOMER login. Role is never taken from the request — an
     * ADMIN can only be created by the operator via ADMIN_PASSWORD.
     */
    @Transactional(rollbackFor = Exception.class)
    public CurrentUser register(String rawUsername, String rawPassword) throws BankingValidationException {
        String username = normalizeUsername(rawUsername);
        validateUsername(username);
        validatePassword(rawPassword);

        if (users.existsByUsername(username)) {
            throw new UsernameAlreadyTakenException("Username " + username + " is already taken.");
        }
        User user = new User(username, encoder.encode(rawPassword), Role.CUSTOMER);
        try {
            users.saveAndFlush(user);
        } catch (DataIntegrityViolationException duplicate) {
            // Two registrations raced past the exists check; the UNIQUE index decided.
            throw new UsernameAlreadyTakenException("Username " + username + " is already taken.");
        }
        audit.record(username, AuditService.REGISTER, null, "Registered a CUSTOMER login");
        return new CurrentUser(user.getId(), user.getUsername(), user.getRole());
    }

    /**
     * Deliberately NOT transactional: every check here is a single read, and
     * each audit entry (failure, rate limit, success) must stand on its own.
     */
    public SessionTokens login(String rawUsername, String rawPassword) {
        String username = normalizeUsername(rawUsername);
        String ip = AuditService.currentIpAddress();

        try {
            rateLimiter.checkAllowed(username, ip);
        } catch (RateLimitExceededException blocked) {
            audit.record(username, AuditService.LOGIN_RATE_LIMITED, null,
                    "Login blocked after too many failures");
            throw blocked;
        }

        Optional<User> found = users.findByUsername(username);
        if (found.isEmpty() || !encoder.matches(rawPassword, found.get().getPasswordHash())) {
            rateLimiter.recordFailure(username, ip);
            audit.record(username, AuditService.LOGIN_FAILURE, null, "Invalid username or password");
            throw new InvalidCredentialsException("Invalid username or password.");
        }

        User user = found.get();
        if (!user.isEnabled()) {
            audit.record(username, AuditService.LOGIN_FAILURE, null, "Account is disabled");
            throw new DisabledException("Account is disabled.");
        }

        rateLimiter.recordSuccess(username, ip);
        audit.record(username, AuditService.LOGIN_SUCCESS, null, "Logged in");
        return tokens(user);
    }

    /**
     * Exchanges a valid refresh token for a new pair. The refresh token is a
     * JWT of its own ({@code typ=refresh}), so an access token cannot be
     * smuggled in here.
     */
    public SessionTokens refresh(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new InvalidRefreshTokenException("Refresh token is required.");
        }
        Claims claims;
        try {
            claims = jwt.parse(refreshToken);
        } catch (JwtException | IllegalArgumentException expiredOrTampered) {
            throw new InvalidRefreshTokenException("Refresh token is invalid or expired.");
        }
        if (!JwtService.TYPE_REFRESH.equals(claims.get("typ", String.class))) {
            throw new InvalidRefreshTokenException(
                    "That is an access token. Send the refresh token you received at login.");
        }
        User user = users.findByUsername(claims.getSubject())
                .orElseThrow(() -> new InvalidRefreshTokenException("Refresh token is invalid or expired."));
        if (!user.isEnabled()) {
            throw new InvalidRefreshTokenException("Account is disabled.");
        }
        audit.record(user.getUsername(), AuditService.REFRESH, null, "Issued a new token pair");
        return tokens(user);
    }

    private SessionTokens tokens(User user) {
        CurrentUser caller = new CurrentUser(user.getId(), user.getUsername(), user.getRole());
        return new SessionTokens(jwt.createAccessToken(caller), jwt.createRefreshToken(caller),
                jwt.accessTtlSeconds());
    }

    /** Usernames are lowercase everywhere: stored, looked up, and rate-limited that way. */
    private static String normalizeUsername(String username) {
        return username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
    }

    private static void validateUsername(String username) throws BankingValidationException {
        if (!username.matches(USERNAME_PATTERN)) {
            throw new BankingValidationException(
                    "Username must be 3 to 50 characters: lowercase letters, digits, dot, underscore or hyphen.");
        }
    }

    private static void validatePassword(String password) throws BankingValidationException {
        if (password == null || password.length() < MIN_PASSWORD_LENGTH || password.length() > MAX_PASSWORD_LENGTH) {
            throw new BankingValidationException(
                    "Password must be " + MIN_PASSWORD_LENGTH + " to " + MAX_PASSWORD_LENGTH + " characters.");
        }
    }
}
