package io.github.grigoriev.im.user;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RoleTest {

    @Test
    void mapsToSpringSecurityAuthorities() {
        assertThat(Role.USER.authority()).isEqualTo("ROLE_USER");
        assertThat(Role.ADMIN.authority()).isEqualTo("ROLE_ADMIN");
    }
}
