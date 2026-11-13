package io.github.grigoriev.im;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.grigoriev.im.audit.AuditAction;
import io.github.grigoriev.im.audit.AuditEvent;
import io.github.grigoriev.im.audit.AuditService;
import io.github.grigoriev.im.customer.CustomerService;
import io.github.grigoriev.im.customer.CustomerTestData;
import java.net.CookieManager;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.support.TransactionTemplate;

/** The audit log against PostgreSQL: logins over real HTTP, changes of the services, a rollback. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"im.admin.username=chef", "im.admin.password=chef-passwort-123"})
@Import(TestDatabase.class)
class AuditIT {

    private static final Pattern CSRF = Pattern.compile("name=\"_csrf\" value=\"([^\"]+)\"");

    @LocalServerPort
    private int port;

    @Autowired
    private AuditService audit;

    @Autowired
    private CustomerService customers;

    @Autowired
    private TransactionTemplate transactions;

    @Autowired
    private JdbcTemplate jdbc;

    @AfterEach
    void clearTheLogin() {
        SecurityContextHolder.clearContext();
    }

    private int login(String username, String password) throws Exception {
        HttpClient client = HttpClient.newBuilder().cookieHandler(new CookieManager()).build();
        String page = client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/login")).build(),
                HttpResponse.BodyHandlers.ofString()).body();
        Matcher token = CSRF.matcher(page);
        assertThat(token.find()).isTrue();
        String form = "username=" + URLEncoder.encode(username, StandardCharsets.UTF_8) + "&password="
                + URLEncoder.encode(password, StandardCharsets.UTF_8) + "&_csrf="
                + URLEncoder.encode(token.group(1), StandardCharsets.UTF_8);
        return client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/login"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form)).build(), HttpResponse.BodyHandlers.ofString()).statusCode();
    }

    private List<AuditEvent> events(AuditAction action) {
        return audit.latest().stream().filter(event -> event.action() == action).toList();
    }

    @Test
    void recordsTheFirstAdminAsTheAppItself() {
        assertThat(events(AuditAction.USER_CREATED)).anySatisfy(event -> {
            assertThat(event.username()).isEqualTo("system");
            assertThat(event.detail()).isEqualTo("chef (ADMIN)");
        });
    }

    @Test
    void recordsLoginsAndNeverTheEnteredText() throws Exception {
        assertThat(login("chef", "chef-passwort-123")).isEqualTo(302);
        assertThat(login("chef", "falsch-falsch-falsch")).isEqualTo(302);
        assertThat(login("mein-geheimes-passwort-im-namensfeld", "x")).isEqualTo(302);

        assertThat(events(AuditAction.LOGIN)).anySatisfy(event -> {
            assertThat(event.username()).isEqualTo("chef");
            assertThat(event.detail()).isEqualTo("Passwort");
        });
        assertThat(events(AuditAction.LOGIN_FAILED)).anySatisfy(event -> {
            assertThat(event.username()).isEqualTo("chef");
            assertThat(event.detail()).isEqualTo("falsches Passwort oder Passkey");
        }).anySatisfy(event -> assertThat(event.username()).isEqualTo("unbekannt"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_event WHERE username LIKE '%geheim%' "
                + "OR detail LIKE '%geheim%' OR detail LIKE '%falsch-falsch%'", Integer.class)).isZero();
    }

    @Test
    void recordsAChangeWithTheUserAndDropsItOnARollback() {
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("elena", "x", "ROLE_USER"));
        var created = customers.create(CustomerTestData.verlag());
        assertThat(events(AuditAction.CUSTOMER_CREATED)).anySatisfy(event -> {
            assertThat(event.username()).isEqualTo("elena");
            assertThat(event.detail()).isEqualTo("Verlag am Fluss GmbH");
        });
        customers.delete(created.id());

        int before = events(AuditAction.CUSTOMER_CREATED).size();
        assertThatThrownBy(() -> transactions.executeWithoutResult(status -> {
            customers.create(CustomerTestData.vhs());
            throw new IllegalStateException("a later step failed");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(events(AuditAction.CUSTOMER_CREATED)).hasSize(before);
    }
}
