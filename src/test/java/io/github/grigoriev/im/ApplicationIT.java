package io.github.grigoriev.im;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.grigoriev.im.einvoice.EInvoiceService;
import io.github.grigoriev.im.einvoice.InvoiceFormat;
import io.github.grigoriev.im.einvoice.IssuedEInvoice;
import io.github.grigoriev.im.einvoice.ItSamples;
import io.github.grigoriev.im.einvoice.ValidationResult;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/** Starts the whole application against PostgreSQL in a container. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestDatabase.class)
class ApplicationIT {

    private final HttpClient http = HttpClient.newHttpClient();

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private EInvoiceService einvoices;

    private HttpResponse<String> get(String path) throws Exception {
        return http.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void migratesTheSchema() {
        assertThat(jdbc.queryForObject("SELECT value FROM app_info WHERE key = 'schema'", String.class))
                .isEqualTo("baseline");
    }

    @Test
    void sendsAnonymousVisitorsToTheLoginPage() throws Exception {
        HttpResponse<String> start = get("/");
        assertThat(start.statusCode()).isEqualTo(302);
        assertThat(start.headers().firstValue("Location")).hasValueSatisfying(url -> assertThat(url).endsWith("/login"));
        HttpResponse<String> login = get("/login");
        assertThat(login.statusCode()).isEqualTo(200);
        assertThat(login.body()).contains("<html lang=\"de\"").contains("Anmelden");
    }

    @Test
    void reportsHealth() throws Exception {
        HttpResponse<String> response = get("/actuator/health");
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("\"status\":\"UP\"");
    }

    @Test
    void issuesAnEInvoiceWithTheBeansOfTheContext() {
        for (InvoiceFormat format : InvoiceFormat.values()) {
            IssuedEInvoice issued = einvoices.issue(ItSamples.reading(format));
            assertThat(issued.validations()).isNotEmpty().allMatch(ValidationResult::valid);
        }
        IssuedEInvoice noContact = einvoices.issue(ItSamples.readingWithoutContact(InvoiceFormat.ZUGFERD_EN16931));
        assertThat(noContact.validations()).as("EN16931 needs no seller e-mail or phone").isNotEmpty()
                .allMatch(ValidationResult::valid);
    }
}
