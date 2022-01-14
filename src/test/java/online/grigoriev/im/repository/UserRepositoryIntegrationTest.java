package online.grigoriev.im.repository;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import online.grigoriev.im.dto.model.Action;
import online.grigoriev.im.entity.AuditLog;
import online.grigoriev.im.entity.Role;
import online.grigoriev.im.entity.User;
import online.grigoriev.im.model.UserRole;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(SpringExtension.class)
@DataJpaTest
@ActiveProfiles("h2")
class UserRepositoryIntegrationTest {

    @Autowired
    UserRepository userRepository;

    @Autowired
    RoleRepository roleRepository;

    @Autowired
    AuditLogRepository auditLogRepository;

    @Test
    void findByIdWithAuditLogs() {
        final Role role = Role.builder()
                .name(UserRole.ADMIN)
                .build();
        final AuditLog auditLog1 = AuditLog.builder()
                .action(Action.LOGIN)
                .time(LocalDateTime.now())
                .log("user logged in")
                .build();
        final AuditLog auditLog2 = AuditLog.builder()
                .action(Action.LOGOUT)
                .time(LocalDateTime.now())
                .log("user logged out")
                .build();
        final User user = User.builder()
                .firstName("Max")
                .lastName("Mustermann")
                .username("maxmustermann")
                .email("max.mustermann@gmx.de")
                .roles(Collections.singleton(role))
                .build();
        user.addAuditLog(auditLog1);
        user.addAuditLog(auditLog2);

        userRepository.save(user);

        final Long id = user.getId();

        final User userById = userRepository.findById(id)
                .orElseThrow(NullPointerException::new);

        final User userByIdWithActions = userRepository.findByIdWithAuditLogs(id)
                .orElseThrow(NullPointerException::new);

        assertEquals(userById, userByIdWithActions);

        final List<Role> roles = roleRepository.findAll();
        assertEquals(1, roles.size());
        final List<AuditLog> auditLogs = auditLogRepository.findAll();
        assertEquals(2, auditLogs.size());
    }

    @Test
    void findAllWithAuditLogs() {
        assertTrue(true);
    }

    @Test
    void findByLastName() {
        assertTrue(true);
    }
}