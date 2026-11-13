package io.github.grigoriev.im.user;

import java.time.Instant;

/** Fictitious users for the tests. */
public final class UserTestData {

    /** Fixed creation time. */
    public static final Instant CREATED = Instant.parse("2027-01-02T09:00:00Z");

    private UserTestData() {
    }

    /**
     * Returns a user.
     *
     * @param id database ID
     * @param username login name
     * @param role role
     * @return the user with the hash {@code {noop}x}
     */
    public static AppUser user(long id, String username, Role role) {
        return new AppUser(id, username, "Name " + username, "{noop}x", role, true, CREATED);
    }
}
