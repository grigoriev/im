package io.github.grigoriev.im.user;

import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

/**
 * A user account.
 *
 * @param id database ID, {@code null} before the first save
 * @param username login name
 * @param displayName name shown in the app
 * @param passwordHash encoded password, never the password itself
 * @param role role
 * @param enabled whether the user may log in
 * @param createdAt time of creation
 */
@Table("app_user")
public record AppUser(
        @Id Long id,
        String username,
        String displayName,
        String passwordHash,
        Role role,
        boolean enabled,
        Instant createdAt) {

    /**
     * Returns a copy with another password hash.
     *
     * @param hash the new encoded password
     * @return the changed user
     */
    public AppUser withPasswordHash(String hash) {
        return new AppUser(id, username, displayName, hash, role, enabled, createdAt);
    }

    /** Leaves the password hash out of logs and error messages. */
    @Override
    public String toString() {
        return "AppUser[id=" + id + ", username=" + username + ", role=" + role + ", enabled=" + enabled + "]";
    }
}
