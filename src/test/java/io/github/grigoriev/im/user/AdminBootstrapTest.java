package io.github.grigoriev.im.user;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class AdminBootstrapTest {

    private final AppUserRepository repository = mock(AppUserRepository.class);
    private final UserService service = mock(UserService.class);

    @Test
    void createsTheFirstAdmin() {
        new AdminBootstrap(repository, service, "admin", "lang-genug-123").run(null);
        verify(service).create("admin", "Administrator", "lang-genug-123", Role.ADMIN);
    }

    @Test
    void changesNothingOnceAnAdminExists() {
        when(repository.existsByRole(Role.ADMIN)).thenReturn(true);
        new AdminBootstrap(repository, service, "admin", "lang-genug-123").run(null);
        verify(service, never()).create(any(), any(), any(), any());
    }

    @ParameterizedTest
    @CsvSource({"'', secret-password", "admin, ''", "' ', ' '"})
    void createsNothingWithoutUsernameAndPassword(String username, String password) {
        new AdminBootstrap(repository, service, username, password).run(null);
        verify(service, never()).create(any(), any(), any(), any());
    }
}
