package com.tesda.ciicc.banking.backend.web;

import com.tesda.ciicc.banking.backend.auth.ApiRateLimiter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.time.Clock;

/**
 * Registers the per-user API rate limit. The paths are the ones that read or
 * move money; {@code /api/auth/**} is deliberately absent, because a client
 * that has spent its budget must still be able to refresh its token, and the
 * login limiter already covers password guessing.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final ApiRateLimitInterceptor rateLimit;

    public WebConfig(Clock clock,
                     @Value("${app.api.max-requests:60}") int maxRequests,
                     @Value("${app.api.window-minutes:1}") long windowMinutes) {
        this.rateLimit = new ApiRateLimitInterceptor(
                new ApiRateLimiter(maxRequests, windowMinutes, clock));
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(rateLimit)
                .addPathPatterns("/api/accounts", "/api/accounts/**", "/api/transfers",
                        "/api/anomaly-flags", "/api/anomaly-flags/**",
                        "/api/reconciliation",
                        "/api/assistant", "/api/assistant/**");
    }
}
