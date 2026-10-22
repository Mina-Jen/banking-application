package com.tesda.ciicc.banking.backend.web;

import com.tesda.ciicc.banking.backend.auth.CurrentUser;
import com.tesda.ciicc.banking.backend.model.Role;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.context.support.WithSecurityContext;
import org.springframework.security.test.context.support.WithSecurityContextFactory;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.List;

/**
 * Puts a {@link CurrentUser} into the security context for one test method —
 * the same principal type the JWT filter installs in production, so the
 * controllers' {@code @AuthenticationPrincipal} parameters resolve. Tests that
 * need a second, "foreign" user pick a different {@code id()}.
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@Documented
@WithSecurityContext(factory = WithMockCurrentUser.Factory.class)
public @interface WithMockCurrentUser {

    int id() default 1;

    String username() default "juan";

    Role role() default Role.CUSTOMER;

    class Factory implements WithSecurityContextFactory<WithMockCurrentUser> {

        @Override
        public SecurityContext createSecurityContext(WithMockCurrentUser annotation) {
            CurrentUser user = new CurrentUser(annotation.id(), annotation.username(), annotation.role());
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(new UsernamePasswordAuthenticationToken(user, null,
                    List.of(new SimpleGrantedAuthority("ROLE_" + user.role().name()))));
            return context;
        }
    }
}
