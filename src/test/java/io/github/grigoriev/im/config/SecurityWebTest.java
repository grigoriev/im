package io.github.grigoriev.im.config;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.grigoriev.im.user.AppUserDetailsService;
import io.github.grigoriev.im.user.AppUserRepository;
import io.github.grigoriev.im.user.Role;
import io.github.grigoriev.im.user.UserException;
import io.github.grigoriev.im.user.UserService;
import io.github.grigoriev.im.user.UserTestData;
import io.github.grigoriev.im.web.AdminUserController;
import io.github.grigoriev.im.web.CurrentUserAdvice;
import io.github.grigoriev.im.web.HomeController;
import io.github.grigoriev.im.web.LoginController;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.authentication.rememberme.PersistentTokenRepository;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** The access rules of {@link SecurityConfig} with the real controllers and templates. */
@WebMvcTest(controllers = {HomeController.class, LoginController.class, AdminUserController.class})
@Import({SecurityConfig.class, CurrentUserAdvice.class})
class SecurityWebTest {

    private static final String PASSWORD = "lang-genug-123";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private PasswordEncoder encoder;

    @MockitoBean
    private AppUserRepository repository;

    @MockitoBean
    private AppUserDetailsService details;

    @MockitoBean
    private UserService users;

    @MockitoBean
    private PersistentTokenRepository tokens;

    private void knownUsers() {
        when(repository.findByUsername("admin")).thenReturn(Optional.of(UserTestData.user(1, "admin", Role.ADMIN)));
        when(repository.findByUsername("elena")).thenReturn(Optional.of(UserTestData.user(2, "elena", Role.USER)));
    }

    @Test
    void sendsAnonymousVisitorsToTheLogin() throws Exception {
        mvc.perform(get("/")).andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/login"));
        mvc.perform(get("/admin/benutzer")).andExpect(status().is3xxRedirection());
    }

    @Test
    void showsTheLoginPageWithItsMessagesAndTheCsp() throws Exception {
        mvc.perform(get("/login")).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Anmelden")))
                .andExpect(header().string("Content-Security-Policy", SecurityConfig.CSP));
        mvc.perform(get("/login").param("error", ""))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Benutzername oder Passwort ist falsch.")));
        mvc.perform(get("/login").param("abgemeldet", ""))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Sie sind abgemeldet.")));
    }

    @Test
    void logsInWithThePasswordAndRemembersOnRequest() throws Exception {
        String hash = encoder.encode(PASSWORD);
        // A fresh user per call: Spring Security erases the password of the object after a login.
        when(details.loadUserByUsername("elena")).thenAnswer(
                call -> User.withUsername("elena").password(hash).authorities("ROLE_USER").build());
        mvc.perform(post("/login").param("username", "elena").param("password", PASSWORD).with(csrf()))
                .andExpect(redirectedUrl("/")).andExpect(authenticated().withUsername("elena"));
        mvc.perform(post("/login").param("username", "elena").param("password", PASSWORD)
                        .param("angemeldet-bleiben", "on").with(csrf()))
                .andExpect(cookie().exists("remember-me"))
                .andExpect(cookie().maxAge("remember-me", (int) SecurityConfig.REMEMBER_ME.toSeconds()));
        verify(tokens).createNewToken(any());
        mvc.perform(post("/login").param("username", "elena").param("password", "falsch-falsch").with(csrf()))
                .andExpect(redirectedUrl("/login?error")).andExpect(unauthenticated());
    }

    @Test
    void sendsAPostWithoutValidCsrfTokenBackToTheLogin() throws Exception {
        mvc.perform(post("/login").param("username", "elena").param("password", PASSWORD))
                .andExpect(redirectedUrl("/login?abgelaufen"));
        mvc.perform(post("/logout").with(user("elena")).with(csrf().useInvalidToken()))
                .andExpect(redirectedUrl("/login?abgelaufen"));
        mvc.perform(get("/login").param("abgelaufen", ""))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Die Sitzung ist abgelaufen.")));
    }

    @Test
    void logsOut() throws Exception {
        mvc.perform(post("/logout").with(user("elena")).with(csrf())).andExpect(redirectedUrl("/login?abgemeldet"));
    }

    @Test
    void greetsTheUserAndHidesTheAdminMenu() throws Exception {
        knownUsers();
        mvc.perform(get("/").with(user("elena").roles("USER"))).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Guten Tag, Name elena")))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("/admin/benutzer"))));
    }

    @Test
    void keepsTheAdminAreaForAdmins() throws Exception {
        knownUsers();
        mvc.perform(get("/admin/benutzer").with(user("elena").roles("USER"))).andExpect(status().isForbidden());
        when(users.list()).thenReturn(List.of(UserTestData.user(1, "admin", Role.ADMIN),
                UserTestData.user(2, "elena", Role.USER)));
        mvc.perform(get("/admin/benutzer").with(user("admin").roles("ADMIN"))).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Name elena")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Neuer Benutzer")));
    }

    @Test
    void createsAUserAndReportsAProblem() throws Exception {
        knownUsers();
        mvc.perform(post("/admin/benutzer").with(user("admin").roles("ADMIN")).with(csrf())
                        .param("username", "neu").param("displayName", "Neu").param("password", PASSWORD)
                        .param("role", "USER"))
                .andExpect(redirectedUrl("/admin/benutzer"))
                .andExpect(flash().attribute("success", "Benutzer „neu“ angelegt."));
        verify(users).create("neu", "Neu", PASSWORD, Role.USER);
        when(users.create(eq("x"), any(), any(), any())).thenThrow(new UserException("Kaputt."));
        mvc.perform(post("/admin/benutzer").with(user("admin").roles("ADMIN")).with(csrf())
                        .param("username", "x").param("displayName", "X").param("password", "p").param("role", "ADMIN"))
                .andExpect(flash().attribute("error", "Kaputt."))
                .andExpect(flash().attribute("username", "x"))
                .andExpect(flash().attribute("displayName", "X"));
    }

    @Test
    void resetsAPasswordAndReportsAProblem() throws Exception {
        when(users.resetPassword(2, PASSWORD)).thenReturn(UserTestData.user(2, "elena", Role.USER));
        mvc.perform(post("/admin/benutzer/2/passwort").with(user("admin").roles("ADMIN")).with(csrf())
                        .param("password", PASSWORD))
                .andExpect(flash().attribute("success", "Neues Passwort für „elena“ gesetzt."));
        when(users.resetPassword(anyLong(), eq("kurz"))).thenThrow(new UserException("Zu kurz."));
        mvc.perform(post("/admin/benutzer/2/passwort").with(user("admin").roles("ADMIN")).with(csrf())
                        .param("password", "kurz"))
                .andExpect(flash().attribute("error", "Zu kurz."));
    }
}
