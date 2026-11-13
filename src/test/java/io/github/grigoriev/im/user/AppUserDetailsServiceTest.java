package io.github.grigoriev.im.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

class AppUserDetailsServiceTest {

    private final AppUserRepository repository = mock(AppUserRepository.class);
    private final AppUserDetailsService service = new AppUserDetailsService(repository);

    @Test
    void loadsUserHashAndRole() {
        when(repository.findByUsername("admin")).thenReturn(Optional.of(UserTestData.user(1, "admin", Role.ADMIN)));
        UserDetails details = service.loadUserByUsername("admin");
        assertThat(details.getUsername()).isEqualTo("admin");
        assertThat(details.getPassword()).isEqualTo("{noop}x");
        assertThat(details.getAuthorities()).extracting(GrantedAuthority::getAuthority).containsExactly("ROLE_ADMIN");
        assertThat(details.isEnabled()).isTrue();
    }

    @Test
    void marksADisabledUser() {
        AppUser disabled = new AppUser(2L, "alt", "Alt", "{noop}x", Role.USER, false, UserTestData.CREATED);
        when(repository.findByUsername("alt")).thenReturn(Optional.of(disabled));
        assertThat(service.loadUserByUsername("alt").isEnabled()).isFalse();
    }

    @Test
    void failsForAnUnknownUser() {
        when(repository.findByUsername("nobody")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.loadUserByUsername("nobody")).isInstanceOf(UsernameNotFoundException.class);
    }
}
