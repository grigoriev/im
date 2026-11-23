package io.github.grigoriev.im.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.github.grigoriev.im.user.AppUser;
import io.github.grigoriev.im.user.Role;
import io.github.grigoriev.im.user.UserService;
import io.github.grigoriev.im.user.UserTestData;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

class CurrentUserAdviceTest {

    private final UserService users = mock(UserService.class);
    private final CurrentUserAdvice advice = new CurrentUserAdvice(users);

    @Test
    void returnsNobodyOnPublicPages() {
        assertThat(advice.currentUser(null)).isNull();
    }

    @Test
    void returnsTheLoggedInUserIfItStillExists() {
        AppUser elena = UserTestData.user(2, "elena", Role.USER);
        when(users.find("elena")).thenReturn(Optional.of(elena));
        assertThat(advice.currentUser(new UsernamePasswordAuthenticationToken("elena", null))).isEqualTo(elena);
        when(users.find("gone")).thenReturn(Optional.empty());
        assertThat(advice.currentUser(new UsernamePasswordAuthenticationToken("gone", null))).isNull();
    }
}
