package io.github.grigoriev.im.user;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;

/** Stores the user accounts. */
public interface AppUserRepository extends ListCrudRepository<AppUser, Long> {

    /**
     * Finds a user by login name.
     *
     * @param username the login name
     * @return the user, if any
     */
    Optional<AppUser> findByUsername(String username);

    /**
     * Tells whether a user with this role exists.
     *
     * @param role the role
     * @return {@code true} if at least one exists
     */
    boolean existsByRole(Role role);

    /**
     * Locks the admins until the transaction ends and returns their IDs. Two deletions of admins then run one after the
     * other, so the second sees the first and the last admin stays.
     *
     * @return the IDs of the admins
     */
    @Query("SELECT id FROM app_user WHERE role = 'ADMIN' ORDER BY id FOR UPDATE")
    List<Long> lockAdmins();

    /**
     * Deletes the API tokens of a user, after a new password: a token made with a leaked password stops too.
     *
     * @param userId the user
     * @return the number of deleted tokens
     */
    @Modifying
    @Query("DELETE FROM api_token WHERE user_id = :userId")
    int deleteApiTokens(long userId);

    /**
     * Lists all users by login name.
     *
     * @return the users
     */
    List<AppUser> findAllByOrderByUsername();
}
