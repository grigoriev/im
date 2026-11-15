package io.github.grigoriev.im;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.grigoriev.im.user.AppUser;
import io.github.grigoriev.im.user.AppUserRepository;
import io.github.grigoriev.im.user.Role;
import io.github.grigoriev.im.user.UserException;
import io.github.grigoriev.im.user.UserService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.support.TransactionTemplate;

/** Deleting users against PostgreSQL: the lock on the admins keeps the last one. */
@SpringBootTest
@Import(TestDatabase.class)
class UserIT {

    @Autowired
    private UserService users;

    @Autowired
    private AppUserRepository repository;

    @Autowired
    private TransactionTemplate transaction;

    @Test
    void deletesAUserAndAnAdminNextToAnother() {
        String suffix = Long.toString(System.nanoTime() % 1_000_000);
        AppUser first = users.create("erste" + suffix, "Erste", "lang-genug-123", Role.ADMIN);
        AppUser second = users.create("zweite" + suffix, "Zweite", "lang-genug-123", Role.ADMIN);
        AppUser guest = users.create("gast" + suffix, "Gast", "lang-genug-123", Role.USER);
        List<Long> admins = transaction.execute(status -> repository.lockAdmins());
        assertThat(admins).as("the lock reads every admin")
                .contains(first.id(), second.id()).doesNotContain(guest.id());
        users.delete(guest.id());
        users.delete(first.id());
        assertThat(repository.findById(guest.id())).isEmpty();
        assertThat(repository.findById(first.id())).isEmpty();
        assertThat(repository.findById(second.id())).isPresent();
        assertThatThrownBy(() -> users.delete(first.id())).isInstanceOf(UserException.class);
    }
}
