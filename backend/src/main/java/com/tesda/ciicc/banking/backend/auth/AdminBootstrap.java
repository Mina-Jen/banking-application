package com.tesda.ciicc.banking.backend.auth;

import com.tesda.ciicc.banking.backend.model.Role;
import com.tesda.ciicc.banking.backend.model.User;
import com.tesda.ciicc.banking.backend.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * Creates the first ADMIN at startup from environment variables, so no
 * password ever lives in code or migrations (project rule). Set
 * {@code ADMIN_PASSWORD} to create user {@code ADMIN_USERNAME} (default
 * "admin"). When an admin with that username already exists, or the variable
 * is unset, this does nothing. Registration always creates CUSTOMERs; ADMIN
 * is an operator role and can only be created this way.
 */
@Component
public class AdminBootstrap implements ApplicationRunner {

    private static final Logger LOG = LoggerFactory.getLogger(AdminBootstrap.class);

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final String username;
    private final String password;

    public AdminBootstrap(UserRepository users,
                          PasswordEncoder encoder,
                          @Value("${ADMIN_USERNAME:admin}") String username,
                          @Value("${ADMIN_PASSWORD:}") String password) {
        this.users = users;
        this.encoder = encoder;
        this.username = username.trim().toLowerCase(Locale.ROOT);
        this.password = password;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (password.isBlank()) {
            LOG.info("ADMIN_PASSWORD is not set: no admin user created. Registrations still work (they "
                    + "always create CUSTOMER accounts).");
            return;
        }
        if (users.existsByUsername(username)) {
            LOG.info("Admin user '{}' already exists: nothing to do.", username);
            return;
        }
        users.save(new User(username, encoder.encode(password), Role.ADMIN));
        LOG.info("Created admin user '{}' from ADMIN_USERNAME/ADMIN_PASSWORD.", username);
    }
}
