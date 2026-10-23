package com.tesda.ciicc.banking.backend.web;

import com.tesda.ciicc.banking.backend.auth.ApiRateLimiter;
import com.tesda.ciicc.banking.backend.auth.CurrentUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Applies the per-user request budget to the money endpoints registered in
 * {@link WebConfig}.
 *
 * <p>This runs after the security filter chain, so an unauthenticated request
 * has already been answered with 401 and never reaches here. The exception it
 * throws is mapped by {@code ApiExceptionHandler} to the usual
 * {@code {status, error, message}} body plus {@code Retry-After} — the same
 * response the login limiter produces.</p>
 */
public class ApiRateLimitInterceptor implements HandlerInterceptor {

    private final ApiRateLimiter limiter;

    public ApiRateLimitInterceptor(ApiRateLimiter limiter) {
        this.limiter = limiter;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof CurrentUser caller) {
            limiter.checkAllowed(caller.id());
        }
        return true;
    }
}
