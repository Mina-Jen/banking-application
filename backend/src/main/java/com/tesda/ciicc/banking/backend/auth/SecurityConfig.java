package com.tesda.ciicc.banking.backend.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tesda.ciicc.banking.backend.web.dto.ErrorResponse;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.io.IOException;
import java.time.Clock;

/**
 * Stateless security: every request carries its own Bearer token; nothing is
 * stored server-side between requests (no HTTP session, therefore no CSRF
 * token either). The public surface is only the three auth endpoints, the API
 * docs, and the actuator health probes — everything else needs a valid access
 * token.
 *
 * <p>Note what this class does NOT decide: which ACCOUNTS a caller may touch.
 * That ownership rule lives in {@code BankingService}, so it applies to every
 * caller of the service, not only to HTTP requests.</p>
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, JwtAuthenticationFilter jwtFilter)
            throws Exception {

        ObjectMapper mapper = new ObjectMapper();
        // Both handlers answer with the same {status, error, message} body as
        // the API's own error advice, so clients parse one error shape.
        AuthenticationEntryPoint unauthorized = (request, response, exception) -> write(mapper, response,
                HttpStatus.UNAUTHORIZED, "Authentication required: send 'Authorization: Bearer <access token>'.");
        AccessDeniedHandler forbidden = (request, response, exception) -> write(mapper, response,
                HttpStatus.FORBIDDEN, "You are not allowed to access this resource.");

        http.csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                    .requestMatchers(HttpMethod.POST,
                            "/api/auth/register", "/api/auth/login", "/api/auth/refresh").permitAll()
                    .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                    .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                    .anyRequest().authenticated())
            .exceptionHandling(handling -> handling
                    .authenticationEntryPoint(unauthorized)
                    .accessDeniedHandler(forbidden))
            .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public JwtAuthenticationFilter jwtAuthenticationFilter(JwtService jwt) {
        return new JwtAuthenticationFilter(jwt);
    }

    /**
     * The filter is part of the security chain above. Without this bean Boot
     * would ALSO register it as a global servlet filter, so it would run twice
     * per request.
     */
    @Bean
    public FilterRegistrationBean<JwtAuthenticationFilter> jwtFilterRegistration(JwtAuthenticationFilter filter) {
        FilterRegistrationBean<JwtAuthenticationFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    /** One-way password hashing: store the hash, never the password. */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /** Injectable time source, so tests can freeze or advance the clock. */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    private static void write(ObjectMapper mapper, HttpServletResponse response,
                              HttpStatus status, String message) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        mapper.writeValue(response.getOutputStream(), ErrorResponse.of(status, message));
    }
}
