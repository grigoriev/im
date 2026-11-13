package io.github.grigoriev.im.user;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AppUserTest {

    private final AppUser user = new AppUser(7L, "elena", "Elena Sommer", "{argon2}secret", Role.USER, true,
            UserTestData.CREATED);

    @Test
    void changesOnlyThePasswordHash() {
        AppUser changed = user.withPasswordHash("{argon2}new");
        assertThat(changed.passwordHash()).isEqualTo("{argon2}new");
        assertThat(changed).usingRecursiveComparison().ignoringFields("passwordHash").isEqualTo(user);
    }

    @Test
    void keepsTheHashOutOfToString() {
        assertThat(user).hasToString("AppUser[id=7, username=elena, role=USER, enabled=true]");
        assertThat(user.toString()).doesNotContain("secret");
    }
}
