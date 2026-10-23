package com.tesda.ciicc.banking.backend.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

/**
 * Creates and verifies the JSON Web Tokens (JWTs) of the API.
 *
 * <p>A JWT is a string that carries its own payload (who the caller is, until
 * when it is valid) plus a signature. Because the signature is made with the
 * server's secret, a client cannot change the payload without the token
 * becoming invalid. The secret comes from the {@code JWT_SECRET} environment
 * variable — the project rule forbids secrets in code — and the application
 * refuses to start without it.</p>
 *
 * <p>Two kinds of token are issued, told apart by the {@code typ} claim:</p>
 * <ul>
 *   <li><b>access</b> — short-lived, sent as {@code Authorization: Bearer ...}
 *   on every API call.</li>
 *   <li><b>refresh</b> — long-lived, accepted only by {@code /api/auth/refresh}
 *   to obtain a new pair without re-typing the password.</li>
 * </ul>
 */
@Service
public class JwtService {

    public static final String TYPE_ACCESS = "access";
    public static final String TYPE_REFRESH = "refresh";

    private static final int MIN_SECRET_BYTES = 32;   // HS256 needs at least 256 bits

    private final SecretKey key;
    private final Duration accessTtl;
    private final Duration refreshTtl;

    public JwtService(@Value("${JWT_SECRET:}") String secret,
                      @Value("${app.auth.access-token-minutes:15}") long accessMinutes,
                      @Value("${app.auth.refresh-token-days:7}") long refreshDays) {

        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "JWT_SECRET is missing or too short. Set it in the environment to a random value "
                            + "of at least " + MIN_SECRET_BYTES + " characters, e.g. `openssl rand -base64 48`.");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessTtl = Duration.ofMinutes(accessMinutes);
        this.refreshTtl = Duration.ofDays(refreshDays);
    }

    public String createAccessToken(CurrentUser user) {
        return build(user, TYPE_ACCESS, accessTtl);
    }

    public String createRefreshToken(CurrentUser user) {
        return build(user, TYPE_REFRESH, refreshTtl);
    }

    /** Lifetime of the access token in seconds, so clients know when to refresh. */
    public long accessTtlSeconds() {
        return accessTtl.toSeconds();
    }

    /**
     * Verifies signature and expiry, then returns the payload. Throws a
     * {@link io.jsonwebtoken.JwtException} subclass when the token is expired,
     * tampered with, or malformed.
     */
    public Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private String build(CurrentUser user, String type, Duration ttl) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(user.username())
                .claim("uid", user.id())
                .claim("role", user.role().name())
                .claim("typ", type)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(ttl)))
                .signWith(key)
                .compact();
    }
}
