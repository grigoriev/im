package io.github.grigoriev.im.api;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;

/** Stores the API tokens. */
public interface ApiTokenRepository extends ListCrudRepository<ApiToken, Long> {

    /**
     * Finds a token by its hash.
     *
     * @param tokenHash SHA-256 of the token, hex
     * @return the token, if it exists
     */
    Optional<ApiToken> findByTokenHash(String tokenHash);

    /**
     * Lists the tokens of a user, newest first.
     *
     * @param userId the user
     * @return the tokens
     */
    List<ApiToken> findAllByUserIdOrderByCreatedAtDescIdDesc(Long userId);

    /**
     * Records the use of a token. Only this column changes, so a token deleted meanwhile stays deleted.
     *
     * @param id the token
     * @param at the time of the request
     * @return 1, or 0 if the token is gone
     */
    @Modifying
    @Query("UPDATE api_token SET last_used_at = :at WHERE id = :id")
    int markUsed(long id, Instant at);
}
