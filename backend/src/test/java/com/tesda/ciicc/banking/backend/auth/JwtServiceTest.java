package com.tesda.ciicc.banking.backend.auth;

import com.tesda.ciicc.banking.backend.model.Role;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The token contract: what goes in at login comes out at the filter, expired
 * and tampered tokens are rejected, and a different secret cannot read our
 * tokens at all.
 */
class JwtServiceTest {

    /** 46 bytes of gibberish: long enough for HS256, looks nothing like a real secret. */
    private static final String SECRET = "unit-test-secret-0123456789abcdef0123456789abcdef";
    private static final String OTHER_SECRET = "another-unit-test-secret-0123456789abcdef0123";

    private final CurrentUser juan = new CurrentUser(7, "juan", Role.CUSTOMER);

    @Test
    void accessTokenRoundTripsTheCaller() {
        JwtService jwt = new JwtService(SECRET, 15, 7);

        Claims claims = jwt.parse(jwt.createAccessToken(juan));

        assertEquals("juan", claims.getSubject());
        assertEquals(7, claims.get("uid", Integer.class));
        assertEquals("CUSTOMER", claims.get("role", String.class));
        assertEquals(JwtService.TYPE_ACCESS, claims.get("typ", String.class));
    }

    @Test
    void refreshTokenIsTypedRefresh() {
        JwtService jwt = new JwtService(SECRET, 15, 7);

        Claims claims = jwt.parse(jwt.createRefreshToken(juan));

        assertEquals(JwtService.TYPE_REFRESH, claims.get("typ", String.class));
    }

    @Test
    void expiredTokenIsRejected() {
        // -1 minute: the token expired the moment it was created.
        JwtService jwt = new JwtService(SECRET, -1, 7);

        ExpiredJwtException e = assertThrows(ExpiredJwtException.class,
                () -> jwt.parse(jwt.createAccessToken(juan)));
    }

    @Test
    void tamperedTokenIsRejected() {
        JwtService jwt = new JwtService(SECRET, 15, 7);
        String token = jwt.createAccessToken(juan);
        String tampered = token.substring(0, token.length() - 2) + "xx";

        assertThrows(JwtException.class, () -> jwt.parse(tampered));
    }

    @Test
    void aDifferentSecretCannotReadOurTokens() {
        JwtService issuer = new JwtService(SECRET, 15, 7);
        JwtService attacker = new JwtService(OTHER_SECRET, 15, 7);

        assertThrows(JwtException.class, () -> attacker.parse(issuer.createAccessToken(juan)));
    }

    @Test
    void shortSecretFailsFastAtStartup() {
        assertThrows(IllegalStateException.class, () -> new JwtService("too-short", 15, 7));
    }
}
