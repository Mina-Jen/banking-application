package com.tesda.ciicc.banking.backend.auth;

import com.tesda.ciicc.banking.backend.model.Role;

/**
 * The authenticated caller, decoded from a verified access token by
 * {@link JwtAuthenticationFilter} and passed down into the service layer.
 * A plain value object on purpose: services receive it as a method parameter,
 * so the ownership rule is visible in their signatures instead of hidden in a
 * static security-context lookup.
 */
public record CurrentUser(int id, String username, Role role) {

    public boolean isAdmin() {
        return role == Role.ADMIN;
    }
}
