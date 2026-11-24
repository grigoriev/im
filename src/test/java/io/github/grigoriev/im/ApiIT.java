package io.github.grigoriev.im;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.grigoriev.im.api.ApiToken;
import io.github.grigoriev.im.api.ApiTokenRepository;
import io.github.grigoriev.im.api.ApiTokenScope;
import io.github.grigoriev.im.api.ApiTokenService;
import io.github.grigoriev.im.api.CreatedToken;
import io.github.grigoriev.im.customer.CustomerService;
import io.github.grigoriev.im.user.AppUser;
import io.github.grigoriev.im.user.Role;
import io.github.grigoriev.im.user.UserService;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** The REST API with real tokens against PostgreSQL (docs/adr/0007-api-tokens-and-mcp.md). */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestDatabase.class)
class ApiIT {

    private static final String CUSTOMER = """
            {"name": "Sprachschule am Hafen", "street": "Hafenstraße 1", "postcode": "12345", "city": "Musterstadt",
             "countryCode": "de", "contactName": "Frau Weber", "format": "ZUGFERD_EN16931", "activity": "TEACHING"}
            """;

    @Autowired
    private MockMvc mvc;

    @Autowired
    private UserService users;

    @Autowired
    private ApiTokenService tokens;

    @Autowired
    private ApiTokenRepository repository;

    @Autowired
    private CustomerService customers;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private JsonMapper json;

    private static String bearer(CreatedToken token) {
        return "Bearer " + token.secret();
    }

    @Test
    void readsAndWritesCustomersWithATokenAndNamesItInTheAuditLog() throws Exception {
        AppUser user = users.create("api" + System.nanoTime() % 1_000_000, "Api", "lang-genug-123", Role.USER);
        CreatedToken reader = tokens.create(user.username(), "Leser", ApiTokenScope.READ, 30);
        CreatedToken writer = tokens.create(user.username(), "Claude", ApiTokenScope.WRITE, 30);

        mvc.perform(get("/api/v1/customers")).andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", "Bearer"));
        mvc.perform(get("/api/v1/customers").with(user(user.username()).roles("USER")))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/customers").header("Authorization", "Bearer im_" + "x".repeat(43)))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/customers").header("Authorization", bearer(reader))).andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
        mvc.perform(post("/api/v1/customers").header("Authorization", bearer(reader))
                .contentType(MediaType.APPLICATION_JSON).content(CUSTOMER)).andExpect(status().isForbidden());

        MvcResult created = mvc.perform(post("/api/v1/customers").header("Authorization", bearer(writer))
                        .contentType(MediaType.APPLICATION_JSON).content(CUSTOMER))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Sprachschule am Hafen"))
                .andExpect(jsonPath("$.countryCode").value("DE"))
                .andExpect(jsonPath("$.activity").value("TEACHING"))
                .andReturn();
        JsonNode body = json.readTree(created.getResponse().getContentAsString());
        long id = body.get("id").asLong();
        assertThat(created.getResponse().getHeader("Location")).isEqualTo("/api/v1/customers/" + id);
        assertThat(jdbc.queryForObject("SELECT detail FROM audit_event WHERE username = ? AND action = 'CUSTOMER_CREATED' "
                + "ORDER BY id DESC LIMIT 1", String.class, user.username()))
                .isEqualTo("Sprachschule am Hafen · API-Token „Claude“");

        mvc.perform(get("/api/v1/customers/" + id).header("Authorization", bearer(reader))).andExpect(status().isOk())
                .andExpect(jsonPath("$.contactName").value("Frau Weber"));
        mvc.perform(put("/api/v1/customers/" + id).header("Authorization", bearer(writer))
                        .contentType(MediaType.APPLICATION_JSON).content(CUSTOMER.replace("Hafenstraße 1", "Kai 7")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.street").value("Kai 7"));
        assertThat(customers.find(id).orElseThrow().street()).isEqualTo("Kai 7");

        mvc.perform(post("/api/v1/customers").header("Authorization", bearer(writer))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"countryCode\": \"DE\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.problems").isArray())
                .andExpect(jsonPath("$.problems", Matchers.hasItem("Bitte das E-Rechnungsformat wählen.")));
        mvc.perform(get("/api/v1/customers/999999").header("Authorization", bearer(reader)))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.detail").value("Diesen Kunden gibt es nicht."));
        mvc.perform(put("/api/v1/customers/999999").header("Authorization", bearer(writer))
                .contentType(MediaType.APPLICATION_JSON).content(CUSTOMER)).andExpect(status().isNotFound());

        ApiToken used = repository.findById(writer.token().id()).orElseThrow();
        assertThat(used.lastUsedAt()).isNotNull();
        assertThat(used.tokenHash()).isNotEqualTo(writer.secret());

        mvc.perform(post("/api/v1/customers").header("Authorization", bearer(writer))
                        .contentType("multipart/form-data; boundary=x").content("--x--"))
                .andExpect(status().isUnsupportedMediaType());

        tokens.delete(user.username(), writer.token().id());
        mvc.perform(get("/api/v1/customers").header("Authorization", bearer(writer))).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/customers").header("Authorization", bearer(reader))).andExpect(status().isOk());
        users.resetPassword(user.id(), "ganz-neues-passwort");
        // A new password ends the token.
        mvc.perform(get("/api/v1/customers").header("Authorization", bearer(reader)))
                .andExpect(status().isUnauthorized());
        CreatedToken later = tokens.create(user.username(), "Später", ApiTokenScope.READ, 30);
        customers.delete(id);
        users.delete(user.id());
        assertThat(repository.findById(later.token().id())).as("the tokens go with their user").isEmpty();
    }

    @Test
    void anExpiredTokenNoLongerWorks() throws Exception {
        AppUser user = users.create("alt" + System.nanoTime() % 1_000_000, "Alt", "lang-genug-123", Role.USER);
        CreatedToken token = tokens.create(user.username(), "Alt", ApiTokenScope.READ, 1);
        jdbc.update("UPDATE api_token SET created_at = now() - interval '2 days', expires_at = now() - interval '1 day' "
                + "WHERE id = ?", token.token().id());
        mvc.perform(get("/api/v1/customers").header("Authorization", bearer(token))).andExpect(status().isUnauthorized());
        users.delete(user.id());
    }
}
