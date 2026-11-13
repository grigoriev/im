package io.github.grigoriev.im.user;

import java.util.List;
import java.util.Optional;
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
     * Lists all users by login name.
     *
     * @return the users
     */
    List<AppUser> findAllByOrderByUsername();
}
