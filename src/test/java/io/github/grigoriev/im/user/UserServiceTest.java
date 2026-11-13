package io.github.grigoriev.im.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.crypto.password.PasswordEncoder;

class UserServiceTest {

    private static final String PASSWORD = "lang-genug-123";

    private final AppUserRepository repository = mock(AppUserRepository.class);
    private final PasswordEncoder encoder = mock(PasswordEncoder.class);
    private final UserService service = new UserService(repository, encoder,
            Clock.fixed(UserTestData.CREATED, ZoneOffset.UTC));

    UserServiceTest() {
        when(encoder.encode(any())).thenAnswer(call -> "{enc}" + call.getArgument(0));
        when(repository.save(any())).thenAnswer(call -> call.getArgument(0));
    }

    @Test
    void createsAnEnabledUserWithAnEncodedPassword() {
        AppUser user = service.create("elena", "  Elena Sommer ", PASSWORD, Role.USER);
        assertThat(user).isEqualTo(new AppUser(null, "elena", "Elena Sommer", "{enc}" + PASSWORD, Role.USER, true,
                UserTestData.CREATED));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"ab", "Elena", "elena sommer", "a234567890123456789012345678901234", "élena"})
    void rejectsAnInvalidUsername(String username) {
        assertThatThrownBy(() -> service.create(username, "Elena", PASSWORD, Role.USER))
                .isInstanceOf(UserException.class).hasMessageContaining("3 bis 32 Zeichen");
        verify(repository, never()).save(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"abc", "e.sommer_2-x", "a23456789012345678901234567890ab"})
    void acceptsValidUsernames(String username) {
        assertThat(service.create(username, "Elena", PASSWORD, Role.USER).username()).isEqualTo(username);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  "})
    void requiresADisplayName(String displayName) {
        assertThatThrownBy(() -> service.create("elena", displayName, PASSWORD, Role.USER))
                .isInstanceOf(UserException.class).hasMessageContaining("Namen");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"elf-zeichen"})
    void requiresTwelveCharacters(String password) {
        assertThatThrownBy(() -> service.create("elena", "Elena", password, Role.USER))
                .isInstanceOf(UserException.class).hasMessageContaining("mindestens 12 Zeichen");
    }

    @Test
    void acceptsExactlyTwelveCharacters() {
        assertThat(service.create("elena", "Elena", "zwoelf-zeich", Role.USER).passwordHash())
                .isEqualTo("{enc}zwoelf-zeich");
    }

    @Test
    void refusesATakenUsername() {
        when(repository.findByUsername("elena")).thenReturn(Optional.of(UserTestData.user(1, "elena", Role.USER)));
        assertThatThrownBy(() -> service.create("elena", "Elena", PASSWORD, Role.USER))
                .isInstanceOf(UserException.class).hasMessage("Der Benutzername „elena“ ist schon vergeben.");
    }

    @Test
    void resetsThePassword() {
        when(repository.findById(1L)).thenReturn(Optional.of(UserTestData.user(1, "elena", Role.USER)));
        assertThat(service.resetPassword(1, PASSWORD).passwordHash()).isEqualTo("{enc}" + PASSWORD);
    }

    @Test
    void refusesAShortNewPasswordAndAnUnknownUser() {
        assertThatThrownBy(() -> service.resetPassword(1, "kurz")).isInstanceOf(UserException.class)
                .hasMessageContaining("mindestens 12 Zeichen");
        when(repository.findById(9L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.resetPassword(9, PASSWORD)).isInstanceOf(UserException.class)
                .hasMessage("Diesen Benutzer gibt es nicht.");
    }

    @Test
    void listsTheUsersByName() {
        List<AppUser> all = List.of(UserTestData.user(1, "admin", Role.ADMIN));
        when(repository.findAllByOrderByUsername()).thenReturn(all);
        assertThat(service.list()).isEqualTo(all);
    }
}
