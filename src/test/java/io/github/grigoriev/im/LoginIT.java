package io.github.grigoriev.im;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.grigoriev.im.user.AppUser;
import io.github.grigoriev.im.user.AppUserRepository;
import io.github.grigoriev.im.user.Role;
import io.github.grigoriev.im.user.UserException;
import io.github.grigoriev.im.user.UserService;
import java.net.CookieManager;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

/** Login, roles and the first admin against PostgreSQL, through real HTTP with session and CSRF token. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"im.admin.username=chef", "im.admin.password=chef-passwort-123"})
@Import(TestDatabase.class)
class LoginIT {

    private static final Pattern CSRF = Pattern.compile("name=\"_csrf\" value=\"([^\"]+)\"");

    @LocalServerPort
    private int port;

    @Autowired
    private AppUserRepository users;

    @Autowired
    private UserService service;

    @Autowired
    private PasswordEncoder encoder;

    @Autowired
    private JdbcTemplate jdbc;

    private HttpClient browser() {
        return HttpClient.newBuilder().cookieHandler(new CookieManager()).build();
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + port + path);
    }

    private HttpResponse<String> get(HttpClient client, String path) throws Exception {
        // Accept HTML as a browser does: error pages are HTML then, not JSON.
        return client.send(HttpRequest.newBuilder(uri(path)).header("Accept", "text/html").build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> post(HttpClient client, String path, String form) throws Exception {
        return client.send(HttpRequest.newBuilder(uri(path))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form)).build(), HttpResponse.BodyHandlers.ofString());
    }

    private static String csrf(String page) {
        Matcher matcher = CSRF.matcher(page);
        assertThat(matcher.find()).as("CSRF token on the page").isTrue();
        return URLEncoder.encode(matcher.group(1), StandardCharsets.UTF_8);
    }

    private HttpClient loggedIn(String username, String password, boolean remember) throws Exception {
        HttpClient client = browser();
        String token = csrf(get(client, "/login").body());
        HttpResponse<String> login = post(client, "/login", "username=" + username + "&password=" + password
                + "&_csrf=" + token + (remember ? "&angemeldet-bleiben=on" : ""));
        assertThat(login.statusCode()).isEqualTo(302);
        assertThat(login.headers().firstValue("Location")).hasValueSatisfying(url -> assertThat(url).endsWith("/"));
        return client;
    }

    @Test
    void createsTheFirstAdminFromTheSettingsWithAnArgon2Hash() {
        AppUser admin = users.findByUsername("chef").orElseThrow();
        assertThat(admin.role()).isEqualTo(Role.ADMIN);
        assertThat(admin.passwordHash()).startsWith("{argon2}");
        assertThat(encoder.matches("chef-passwort-123", admin.passwordHash())).isTrue();
    }

    @Test
    void letsTheAdminManageUsersAndKeepsTheUserOut() throws Exception {
        HttpClient admin = loggedIn("chef", "chef-passwort-123", false);
        HttpResponse<String> page = get(admin, "/admin/benutzer");
        assertThat(page.statusCode()).isEqualTo(200);
        HttpResponse<String> created = post(admin, "/admin/benutzer", "username=elena&displayName=Elena+Sommer"
                + "&password=elena-passwort-123&role=USER&_csrf=" + csrf(page.body()));
        assertThat(created.statusCode()).isEqualTo(302);
        assertThat(get(admin, "/admin/benutzer").body()).contains("Benutzer „elena“ angelegt.").contains("Elena Sommer");

        HttpClient elena = loggedIn("elena", "elena-passwort-123", false);
        assertThat(get(elena, "/").body()).contains("Guten Tag, Elena Sommer").doesNotContain("/admin/benutzer");
        HttpResponse<String> forbidden = get(elena, "/admin/benutzer");
        assertThat(forbidden.statusCode()).isEqualTo(403);
        assertThat(forbidden.body()).contains("Kein Zugriff");
    }

    @Test
    void storesTheRememberMeToken() throws Exception {
        service.create("gast", "Gast", "gast-passwort-123", Role.USER);
        loggedIn("gast", "gast-passwort-123", true);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM persistent_logins WHERE username = 'gast'", Integer.class))
                .isEqualTo(1);
    }

    @Test
    void refusesAWrongPasswordAndADuplicateUsername() throws Exception {
        HttpClient client = browser();
        HttpResponse<String> failed = post(client, "/login",
                "username=chef&password=falsch-falsch-falsch&_csrf=" + csrf(get(client, "/login").body()));
        assertThat(failed.headers().firstValue("Location")).hasValueSatisfying(url -> assertThat(url).endsWith("/login?error"));
        assertThatThrownBy(() -> service.create("chef", "Chef", "noch-ein-passwort", Role.USER))
                .isInstanceOf(UserException.class);
    }
}
