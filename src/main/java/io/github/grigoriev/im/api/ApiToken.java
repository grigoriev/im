package io.github.grigoriev.im.api;

import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

/**
 * A personal API token. The table keeps its hash, never the token itself.
 *
 * @param id database ID, {@code null} before the first save
 * @param userId the user the token acts for
 * @param name the name its user gave it, for example "Claude Code"
 * @param tokenHash SHA-256 of the token, hex
 * @param scope what it may do
 * @param createdAt time of creation
 * @param expiresAt time it stops working
 * @param lastUsedAt time of the last request, {@code null} if never used
 */
@Table("api_token")
public record ApiToken(
        @Id Long id,
        Long userId,
        String name,
        String tokenHash,
        ApiTokenScope scope,
        Instant createdAt,
        Instant expiresAt,
        Instant lastUsedAt) {

    /**
     * Returns a copy used at a given time.
     *
     * @param at the time of the request
     * @return the copy
     */
    public ApiToken usedAt(Instant at) {
        return new ApiToken(id, userId, name, tokenHash, scope, createdAt, expiresAt, at);
    }

    /** Leaves the hash out of logs and error messages. */
    @Override
    public String toString() {
        return "ApiToken[id=" + id + ", userId=" + userId + ", name=" + name + ", scope=" + scope + "]";
    }
}
