package io.github.grigoriev.im.user;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Creates the first admin at startup from {@code IM_ADMIN_USERNAME} and {@code IM_ADMIN_PASSWORD},
 * as long as no admin exists (docs/PLAN.md, section 5). Later starts change nothing.
 */
@Component
public class AdminBootstrap implements ApplicationRunner {

    private static final Logger LOG = LoggerFactory.getLogger(AdminBootstrap.class);

    private final AppUserRepository users;
    private final UserService service;
    private final String username;
    private final String password;

    /**
     * Creates the bootstrap.
     *
     * @param users the user store
     * @param service creates the admin
     * @param username login name of the first admin, may be empty
     * @param password password of the first admin, may be empty
     */
    public AdminBootstrap(AppUserRepository users, UserService service,
            @Value("${im.admin.username:}") String username, @Value("${im.admin.password:}") String password) {
        this.users = users;
        this.service = service;
        this.username = username;
        this.password = password;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (users.existsByRole(Role.ADMIN)) {
            return;
        }
        if (username.isBlank() || password.isBlank()) {
            LOG.warn("No admin exists. Set IM_ADMIN_USERNAME and IM_ADMIN_PASSWORD to create one at startup.");
            return;
        }
        service.create(username, "Administrator", password, Role.ADMIN);
        LOG.info("Created the first admin {}", username);
    }
}
