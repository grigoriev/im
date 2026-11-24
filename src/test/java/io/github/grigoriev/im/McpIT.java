package io.github.grigoriev.im;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.grigoriev.im.api.ApiTokenScope;
import io.github.grigoriev.im.api.ApiTokenService;
import io.github.grigoriev.im.api.CreatedToken;
import io.github.grigoriev.im.customer.CustomerService;
import io.github.grigoriev.im.user.AppUser;
import io.github.grigoriev.im.user.Role;
import io.github.grigoriev.im.user.UserService;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** The MCP server over HTTP with real tokens against PostgreSQL (docs/adr/0007-api-tokens-and-mcp.md). */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestDatabase.class)
class McpIT {

    @LocalServerPort
    private int port;

    @Autowired
    private UserService users;

    @Autowired
    private ApiTokenService tokens;

    @Autowired
    private CustomerService customers;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private JsonMapper json;

    private final HttpClient client = HttpClient.newHttpClient();

    private HttpResponse<String> send(String token, String method, String params) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/mcp"))
                .header("Content-Type", "application/json").header("Accept", "application/json, text/event-stream")
                .POST(HttpRequest.BodyPublishers.ofString("{\"jsonrpc\": \"2.0\", \"id\": 1, \"method\": \"" + method
                        + "\", \"params\": " + params + "}"));
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private JsonNode call(CreatedToken token, String tool, String arguments) throws Exception {
        HttpResponse<String> response = send(token.secret(), "tools/call",
                "{\"name\": \"" + tool + "\", \"arguments\": " + arguments + "}");
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        return json.readTree(response.body()).get("result");
    }

    @Test
    void anAssistantSetsUpCustomersThroughTheToolsOfItsToken() throws Exception {
        String suffix = Long.toString(System.nanoTime() % 1_000_000);
        AppUser user = users.create("mcp" + suffix, "Mcp", "lang-genug-123", Role.USER);
        CreatedToken writer = tokens.create(user.username(), "Claude", ApiTokenScope.WRITE, 30);
        CreatedToken reader = tokens.create(user.username(), "Leser", ApiTokenScope.READ, 30);

        assertThat(send(null, "tools/list", "{}").statusCode()).isEqualTo(401);
        HttpResponse<String> init = send(reader.secret(), "initialize", """
                {"protocolVersion": "2025-06-18", "capabilities": {}, "clientInfo": {"name": "test", "version": "1"}}""");
        assertThat(init.statusCode()).as(init.body()).isEqualTo(200);
        assertThat(json.readTree(init.body()).at("/result/serverInfo/name").asString()).isEqualTo("im");

        JsonNode list = json.readTree(send(reader.secret(), "tools/list", "{}").body()).at("/result/tools");
        List<String> names = new ArrayList<>();
        list.forEach(tool -> names.add(tool.get("name").asString()));
        assertThat(names).contains("list_customers", "create_customer", "save_template", "save_number_pattern")
                .hasSize(16);

        String customer = """
                {"customer": {"name": "Sprachschule am Kanal %s", "street": "Kanalweg 3", "postcode": "12345",
                 "city": "Musterstadt", "countryCode": "DE", "format": "ZUGFERD_EN16931"}}""".formatted(suffix);
        JsonNode refused = call(reader, "create_customer", customer);
        assertThat(refused.get("isError").asBoolean()).isTrue();
        assertThat(refused.at("/content/0/text").asString()).isEqualTo("Der API-Token darf nur lesen.");

        JsonNode created = call(writer, "create_customer", customer);
        assertThat(created.has("isError") && created.get("isError").asBoolean()).as(created.toString()).isFalse();
        long id = json.readTree(created.at("/content/0/text").asString()).get("id").asLong();
        assertThat(customers.find(id)).isPresent();
        assertThat(jdbc.queryForObject("SELECT detail FROM audit_event WHERE username = ? AND action = 'CUSTOMER_CREATED' "
                + "ORDER BY id DESC LIMIT 1", String.class, user.username())).endsWith("· API-Token „Claude“");

        JsonNode incomplete = call(writer, "create_customer", "{\"customer\": {\"name\": \"Ohne Ort\"}}");
        assertThat(incomplete.get("isError").asBoolean()).as("the schema refuses it").isTrue();
        assertThat(incomplete.at("/content/0/text").asString()).contains("required property 'format'");
        JsonNode invalid = call(writer, "create_customer", customer.replace("\"DE\"", "\"XX\""));
        assertThat(invalid.get("isError").asBoolean()).as("the service refuses it").isTrue();
        assertThat(invalid.at("/content/0/text").asString()).contains("Land");
        assertThat(call(reader, "get_tax_notes", "{}").at("/content/0/text").asString())
                .isEqualTo("Nur mit einem API-Token des Administrators.");

        customers.delete(id);
        users.delete(user.id());
    }
}
