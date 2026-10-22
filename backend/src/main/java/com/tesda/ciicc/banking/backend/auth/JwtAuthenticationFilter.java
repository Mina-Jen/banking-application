package com.tesda.ciicc.banking.backend.auth;

import com.tesda.ciicc.banking.backend.model.Role;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Runs before every request: reads {@code Authorization: Bearer <access token>},
 * verifies it, and fills the {@link SecurityContextHolder} so the rest of
 * Spring Security (and {@code @AuthenticationPrincipal} in the controllers)
 * knows who is calling.
 *
 * <p>A missing, expired, or tampered token never produces an error response
 * here — the caller simply stays anonymous, and the authorization rules then
 * answer 401 for protected endpoints. A <em>refresh</em> token in the header
 * also counts as anonymous: refresh tokens are only accepted by
 * {@code /api/auth/refresh}, which validates the {@code typ} claim itself.</p>
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwt;

    public JwtAuthenticationFilter(JwtService jwt) {
        this.jwt = jwt;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        String header = request.getHeader(AUTHORIZATION_HEADER);
        if (header != null && header.startsWith(BEARER_PREFIX)
                && SecurityContextHolder.getContext().getAuthentication() == null) {

            try {
                Claims claims = jwt.parse(header.substring(BEARER_PREFIX.length()));
                if (JwtService.TYPE_ACCESS.equals(claims.get("typ", String.class))) {
                    CurrentUser user = new CurrentUser(
                            claims.get("uid", Integer.class),
                            claims.getSubject(),
                            Role.valueOf(claims.get("role", String.class)));
                    UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(user, null,
                                    List.of(new SimpleGrantedAuthority("ROLE_" + user.role().name())));
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            } catch (JwtException | IllegalArgumentException invalidToken) {
                // Expired, tampered, or malformed: remain anonymous and let the
                // security rules answer. Never leak why the token was rejected.
                SecurityContextHolder.clearContext();
            }
        }

        chain.doFilter(request, response);
    }
}
