package online.grigoriev.im.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import online.grigoriev.im.entity.Role;

@Repository
public interface RoleRepository extends JpaRepository<Role, Long> {
}
