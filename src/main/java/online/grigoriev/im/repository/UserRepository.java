package online.grigoriev.im.repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import online.grigoriev.im.entity.User;
import online.grigoriev.im.model.UserRole;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    @Query("select u from User u left join fetch u.auditLogs d where u.id = :id")
    Optional<User> findByIdWithAuditLogs(@Param("id") Long id);

    @Query("select distinct u from User u left join fetch u.auditLogs")
    List<User> findAllWithAuditLogs();

    List<User> findByLastName(@Param("lastName") String lastName);

    Optional<User> findByUsername(@Param("username") String username);

    @Query(
            "select distinct user from User user " +
                    "join user.roles role " +
                    "where role.name in :userRoles"
    )
    List<User> findByUserRoles(Set<UserRole> userRoles);
}
