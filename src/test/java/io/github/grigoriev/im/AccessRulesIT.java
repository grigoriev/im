package io.github.grigoriev.im;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * The access rule of every URL of the app, for an anonymous visitor, a user and the admin. The rule table
 * must name every URL that a controller serves: a new URL without a rule fails {@link #everyUrlHasARule()}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestDatabase.class)
class AccessRulesIT {

    /** Who may use a URL. */
    enum Access {
        /** Everybody, also without login. */
        PUBLIC,
        /** Every logged-in user, both roles. */
        USER,
        /** The admin only. */
        ADMIN
    }

    /** One rule per method and URL pattern of a controller. Path variables get harmless values, see {@link #url}. */
    static final Map<String, Access> RULES = Map.ofEntries(
            Map.entry("GET /login", Access.PUBLIC),
            Map.entry("ANY /error", Access.PUBLIC),
            Map.entry("GET /", Access.USER),
            Map.entry("GET /konto", Access.USER),
            Map.entry("POST /konto/passkeys/{id}/name", Access.USER),
            Map.entry("POST /konto/passkeys/{id}/loeschen", Access.USER),
            Map.entry("POST /konto/passwort", Access.USER),
            Map.entry("GET /kunden", Access.USER),
            Map.entry("GET /kunden/neu", Access.USER),
            Map.entry("GET /kunden/{id}", Access.USER),
            Map.entry("POST /kunden", Access.USER),
            Map.entry("POST /kunden/{id}", Access.USER),
            Map.entry("POST /kunden/{id}/loeschen", Access.USER),
            Map.entry("POST /kunden/{customerId}/vorlagen", Access.USER),
            Map.entry("GET /kunden/{customerId}/vorlagen/{id}", Access.USER),
            Map.entry("POST /kunden/{customerId}/vorlagen/{id}", Access.USER),
            Map.entry("POST /kunden/{customerId}/vorlagen/{id}/loeschen", Access.USER),
            Map.entry("GET /rechnungen", Access.USER),
            Map.entry("POST /rechnungen", Access.USER),
            Map.entry("GET /rechnungen/{id}", Access.USER),
            Map.entry("POST /rechnungen/{id}", Access.USER),
            Map.entry("POST /rechnungen/{id}/loeschen", Access.USER),
            Map.entry("GET /rechnungen/{id}/vorschau", Access.USER),
            Map.entry("POST /rechnungen/{id}/ausstellen", Access.USER),
            Map.entry("POST /rechnungen/{id}/versand", Access.USER),
            Map.entry("POST /rechnungen/{id}/zahlungen", Access.USER),
            Map.entry("POST /rechnungen/{id}/senden", Access.USER),
            Map.entry("GET /rechnungen/{id}/dokumente/{kind}", Access.USER),
            Map.entry("GET /einstellungen", Access.USER),
            Map.entry("POST /einstellungen", Access.USER),
            Map.entry("GET /admin/benutzer", Access.ADMIN),
            Map.entry("POST /admin/benutzer", Access.ADMIN),
            Map.entry("POST /admin/benutzer/{id}/passwort", Access.ADMIN),
            Map.entry("POST /admin/benutzer/{id}/passkeys/loeschen", Access.ADMIN),
            Map.entry("GET /admin/steuerhinweise", Access.ADMIN),
            Map.entry("POST /admin/steuerhinweise", Access.ADMIN),
            Map.entry("GET /admin/e-mail", Access.ADMIN),
            Map.entry("POST /admin/e-mail", Access.ADMIN),
            Map.entry("GET /admin/nummernkreis", Access.ADMIN),
            Map.entry("POST /admin/nummernkreis/muster", Access.ADMIN),
            Map.entry("POST /admin/nummernkreis/start", Access.ADMIN),
            Map.entry("GET /admin/protokoll", Access.ADMIN),
            Map.entry("GET /admin/laender", Access.ADMIN),
            Map.entry("POST /admin/laender", Access.ADMIN),
            Map.entry("POST /admin/laender/{code}/entfernen", Access.ADMIN));

    /** The actors. Nobody exists in the database, so no request changes data that another test reads. */
    private static final Map<String, RequestPostProcessor> ACTORS = Map.of(
            "user", user("zugriff-user").roles("USER"),
            "admin", user("zugriff-admin").roles("ADMIN"));

    @Autowired
    private MockMvc mvc;

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    private RequestMappingHandlerMapping mappings;

    /** Every method and URL pattern that a controller serves, as "GET /kunden/{id}". */
    private Set<String> served() {
        Set<String> served = new TreeSet<>();
        for (RequestMappingInfo info : mappings.getHandlerMethods().keySet()) {
            Set<?> methods = info.getMethodsCondition().getMethods();
            for (String pattern : info.getPatternValues()) {
                if (methods.isEmpty()) {
                    served.add("ANY " + pattern);
                }
                methods.forEach(method -> served.add(method + " " + pattern));
            }
        }
        return served;
    }

    /** A URL for a pattern. The values exist nowhere: a request that passes the rules changes nothing. */
    private static String url(String pattern) {
        return pattern.replace("{id}", "999999").replace("{customerId}", "999998").replace("{code}", "ZZ")
                .replace("{kind}", "pdf");
    }

    private static MockHttpServletRequestBuilder request(String rule) {
        String[] parts = rule.split(" ", 2);
        HttpMethod method = parts[0].equals("ANY") ? HttpMethod.GET : HttpMethod.valueOf(parts[0]);
        return MockMvcRequestBuilders.request(method, url(parts[1]));
    }

    private MockHttpServletResponse send(String rule, RequestPostProcessor actor) throws Exception {
        MockHttpServletRequestBuilder request = request(rule).with(csrf());
        if (actor != null) {
            request.with(actor);
        }
        return mvc.perform(request).andReturn().getResponse();
    }

    /** The error page itself: called directly, it shows a 500 without a real error behind it. */
    private static final String ERROR_PAGE = "ANY /error";

    /** Passed the rules: no 401 or 403, and no redirect to the login page. */
    private static boolean reached(MockHttpServletResponse response) {
        String location = response.getHeader("Location");
        return response.getStatus() != 401 && response.getStatus() != 403
                && (location == null || !location.contains("/login"));
    }

    /** Passed the rules and worked: no server error either, except on the error page itself. */
    private static boolean admitted(String rule, MockHttpServletResponse response) {
        return reached(response) && (response.getStatus() < 500 || rule.equals(ERROR_PAGE));
    }

    @Test
    void everyUrlHasARule() {
        assertThat(served()).as("Give each new URL a rule in RULES, each removed URL leaves it")
                .containsExactlyInAnyOrderElementsOf(RULES.keySet());
    }

    @TestFactory
    Stream<DynamicTest> theRulesHoldForEveryActor() {
        return RULES.entrySet().stream().sorted(Map.Entry.comparingByKey()).flatMap(rule -> Stream.of(
                DynamicTest.dynamicTest(rule.getKey() + " anonymous", () -> {
                    MockHttpServletResponse response = send(rule.getKey(), null);
                    if (rule.getValue() == Access.PUBLIC) {
                        assertThat(admitted(rule.getKey(), response)).as("admitted").isTrue();
                    } else {
                        assertThat(response.getStatus()).isEqualTo(302);
                        assertThat(response.getHeader("Location")).endsWith("/login");
                    }
                }),
                DynamicTest.dynamicTest(rule.getKey() + " user", () -> {
                    MockHttpServletResponse response = send(rule.getKey(), ACTORS.get("user"));
                    if (rule.getValue() == Access.ADMIN) {
                        assertThat(response.getStatus()).isEqualTo(403);
                    } else {
                        assertThat(admitted(rule.getKey(), response)).as("admitted, status %s", response.getStatus()).isTrue();
                    }
                }),
                DynamicTest.dynamicTest(rule.getKey() + " admin", () -> {
                    MockHttpServletResponse response = send(rule.getKey(), ACTORS.get("admin"));
                    assertThat(admitted(rule.getKey(), response)).as("admitted, status %s", response.getStatus()).isTrue();
                })));
    }

    @TestFactory
    Stream<DynamicTest> everyFormNeedsTheCsrfToken() {
        Stream<String> posts = Stream.concat(RULES.keySet().stream().filter(rule -> rule.startsWith("POST ")),
                Stream.of("POST /logout", "POST /webauthn/register/options", "POST /webauthn/register"));
        return posts.sorted().map(rule -> DynamicTest.dynamicTest(rule, () -> {
            MockHttpServletResponse response = mvc.perform(request(rule).with(ACTORS.get("admin")))
                    .andReturn().getResponse();
            assertThat(response.getStatus()).isEqualTo(302);
            assertThat(response.getHeader("Location")).endsWith("/login?abgelaufen");
        }));
    }

    @TestFactory
    Stream<DynamicTest> theLoginFormsNeedTheCsrfTokenToo() {
        return Stream.of("POST /login", "POST /login/webauthn", "POST /webauthn/authenticate/options")
                .map(rule -> DynamicTest.dynamicTest(rule, () -> {
                    MockHttpServletResponse response = mvc.perform(request(rule)).andReturn().getResponse();
                    assertThat(response.getStatus()).isEqualTo(302);
                    assertThat(response.getHeader("Location")).endsWith("/login?abgelaufen");
                }));
    }

    @Test
    void thePasskeyEndpointsOfSpringSecurityFollowTheRules() throws Exception {
        assertThat(send("POST /webauthn/authenticate/options", null).getStatus()).as("login challenge").isEqualTo(200);
        assertThat(send("POST /login/webauthn", null).getStatus()).as("an empty passkey login fails, not blocked")
                .isEqualTo(401);
        assertThat(send("POST /webauthn/register/options", null).getStatus()).as("no registration without login")
                .isNotEqualTo(200);
        assertThat(send("POST /webauthn/register/options", ACTORS.get("user")).getStatus()).isEqualTo(200);
        assertThat(send("POST /login", null).getHeader("Location")).as("an empty password login fails, not blocked")
                .endsWith("/login?error");
    }

    @Test
    void onlyTheHealthOfTheActuatorIsOpen() throws Exception {
        assertThat(send("GET /actuator/health", null).getStatus()).isEqualTo(200);
        assertThat(send("GET /actuator/health/liveness", null).getStatus()).isEqualTo(200);
        assertThat(send("GET /actuator", ACTORS.get("admin")).getContentAsString()).as("the index lists health only")
                .contains("/actuator/health").doesNotContain("env", "beans", "info", "heapdump");
        for (String endpoint : List.of("/actuator", "/actuator/env", "/actuator/beans", "/actuator/configprops",
                "/actuator/heapdump", "/actuator/info", "/actuator/mappings")) {
            assertThat(send("GET " + endpoint, null).getHeader("Location")).as(endpoint).endsWith("/login");
        }
        for (String endpoint : List.of("/actuator/env", "/actuator/beans", "/actuator/configprops", "/actuator/heapdump",
                "/actuator/info", "/actuator/mappings")) {
            assertThat(send("GET " + endpoint, ACTORS.get("admin")).getStatus()).as(endpoint).isEqualTo(404);
        }
    }

    @Test
    void theAssetsAreOpenAndNothingElse() throws Exception {
        for (String asset : List.of("/css/app.css", "/js/passkeys.js", "/vendor/lucide.min.js")) {
            assertThat(send("GET " + asset, null).getStatus()).as(asset).isEqualTo(200);
        }
        assertThat(send("GET /unbekannt", null).getHeader("Location")).endsWith("/login");
        assertThat(send("GET /admin/unbekannt", ACTORS.get("user")).getStatus()).isEqualTo(403);
    }
}
