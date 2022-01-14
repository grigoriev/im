package online.grigoriev.im.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import online.grigoriev.im.entity.Role;
import online.grigoriev.im.model.UserRole;

@Repository
public interface RoleRepository extends JpaRepository<Role, Integer> {
    Optional<Role> findByName(@Param("name") UserRole name);
}
