package io.github.grigoriev.im;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.grigoriev.im.user.Passkey;
import io.github.grigoriev.im.user.PasskeyService;
import io.github.grigoriev.im.user.PasskeyTestData;
import java.net.CookieManager;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.web.webauthn.api.CredentialRecord;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialUserEntity;
import org.springframework.security.web.webauthn.management.PublicKeyCredentialUserEntityRepository;
import org.springframework.security.web.webauthn.management.UserCredentialRepository;

/** Passkeys against PostgreSQL: the stores of Spring Security, the service and the login options. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestDatabase.class)
class PasskeyIT {

    private static final Pattern CSRF = Pattern.compile("name=\"_csrf\" content=\"([^\"]+)\"");

    private static final Instant CREATED = Instant.parse("2027-01-02T08:00:00Z");

    @LocalServerPort
    private int port;

    @Autowired
    private PublicKeyCredentialUserEntityRepository entities;

    @Autowired
    private UserCredentialRepository credentials;

    @Autowired
    private PasskeyService service;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void storesRenamesAndRemovesPasskeys() {
        PublicKeyCredentialUserEntity owner = PasskeyTestData.entity(entities, "anna");
        CredentialRecord older = PasskeyTestData.passkey(credentials, owner, "MacBook Air", CREATED, CREATED);
        Instant later = CREATED.plus(1, ChronoUnit.DAYS);
        PasskeyTestData.passkey(credentials, owner, "iPhone", later, later);
        PublicKeyCredentialUserEntity other = PasskeyTestData.entity(entities, "bert");
        PasskeyTestData.passkey(credentials, other, "iPad", CREATED, CREATED);

        assertThat(service.list("anna")).extracting(Passkey::label).containsExactly("iPhone", "MacBook Air");
        String id = older.getCredentialId().toBase64UrlString();
        service.rename("anna", id, "  Büro-Mac ");
        assertThat(credentials.findByCredentialId(older.getCredentialId()).getLabel()).isEqualTo("Büro-Mac");
        assertThat(service.list("anna")).extracting(Passkey::lastUsed).containsExactly(later, CREATED);

        service.remove("anna", id);
        assertThat(service.count("anna")).isEqualTo(1);
        assertThat(service.removeAll("anna")).isEqualTo(1);
        assertThat(service.count("anna")).isZero();
        assertThat(service.count("bert")).as("other users keep their passkeys").isEqualTo(1);
    }

    @Test
    void removesThePasskeysWithTheirUserEntity() {
        PublicKeyCredentialUserEntity owner = PasskeyTestData.entity(entities, "carla");
        PasskeyTestData.passkey(credentials, owner, "MacBook Air", CREATED, CREATED);
        entities.delete(owner.getId());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM user_credentials WHERE user_entity_user_id = ?",
                Integer.class, owner.getId().toBase64UrlString())).isZero();
    }

    @Test
    void givesAnonymousVisitorsALoginChallenge() throws Exception {
        HttpClient client = HttpClient.newBuilder().cookieHandler(new CookieManager()).build();
        String page = client.send(HttpRequest.newBuilder(uri("/login")).build(), HttpResponse.BodyHandlers.ofString()).body();
        Matcher token = CSRF.matcher(page);
        assertThat(token.find()).as("CSRF token in the page head").isTrue();
        HttpResponse<String> options = client.send(HttpRequest.newBuilder(uri("/webauthn/authenticate/options"))
                .header("X-CSRF-TOKEN", token.group(1)).POST(HttpRequest.BodyPublishers.noBody()).build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(options.statusCode()).isEqualTo(200);
        assertThat(options.body()).contains("\"challenge\"").contains("\"rpId\":\"localhost\"");
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + port + path);
    }
}
