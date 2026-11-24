package io.github.grigoriev.im.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.grigoriev.im.customer.Customer;
import io.github.grigoriev.im.customer.CustomerException;
import io.github.grigoriev.im.customer.CustomerTestData;
import io.github.grigoriev.im.einvoice.TaxCategory;
import io.github.grigoriev.im.invoice.InvoiceException;
import io.github.grigoriev.im.invoice.TemplateForm;
import io.github.grigoriev.im.mail.MailText;
import io.github.grigoriev.im.seller.SellerProfile;
import io.github.grigoriev.im.seller.SettingsException;
import io.modelcontextprotocol.json.jackson3.JacksonMcpJsonMapper;
import io.modelcontextprotocol.server.McpStatelessServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import tools.jackson.databind.json.JsonMapper;

class McpToolsTest {

    private final ApiCustomerController customers = mock(ApiCustomerController.class);
    private final ApiTemplateController templates = mock(ApiTemplateController.class);
    private final ApiSettingsController settings = mock(ApiSettingsController.class);
    private final JsonMapper json = JsonMapper.builder().build();
    private final McpTools tools = new McpTools(customers, templates, settings, new JacksonMcpJsonMapper(json), json);

    @AfterEach
    void clearTheLogin() {
        SecurityContextHolder.clearContext();
    }

    private static void login(String... authorities) {
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("elena", null, authorities));
    }

    private CallToolResult call(String name, Map<String, Object> args) {
        SyncToolSpecification tool = tools.specifications().stream().filter(spec -> spec.tool().name().equals(name))
                .findFirst().orElseThrow();
        return tool.callHandler().apply(null, new CallToolRequest(name, args));
    }

    private static String text(CallToolResult result) {
        return ((TextContent) result.content().getFirst()).text();
    }

    private static final Map<String, Object> CUSTOMER = Map.of("name", "Volkshochschule", "street", "Am Markt 1",
            "postcode", "12345", "city", "Musterstadt", "countryCode", "DE", "format", "ZUGFERD_EN16931");

    @Test
    void offersSixteenToolsAndMarksTheReadingOnes() {
        List<SyncToolSpecification> all = tools.specifications();
        assertThat(all).hasSize(16);
        assertThat(all).filteredOn(spec -> spec.tool().annotations().readOnlyHint())
                .extracting(spec -> spec.tool().name()).containsExactly("list_customers", "get_customer",
                        "list_templates", "get_template", "get_profile", "get_mail_texts", "get_tax_notes",
                        "get_number_pattern");
        assertThat(all).allSatisfy(spec -> assertThat(spec.tool().inputSchema()).containsEntry("type", "object"));
    }

    @Test
    void everyToolCallsItsController() {
        login("ROLE_ADMIN", ApiTokenScope.READ_AUTHORITY, ApiTokenScope.WRITE_AUTHORITY);
        Customer vhs = CustomerTestData.vhs().withId(4L);
        when(customers.list()).thenReturn(List.of(vhs));
        when(customers.get(4L)).thenReturn(vhs);
        when(customers.create(any())).thenReturn(ResponseEntity.ok(vhs));
        when(customers.update(eq(4L), any())).thenReturn(vhs);
        assertThat(text(call("list_customers", Map.of()))).contains("\"id\":4");
        assertThat(text(call("get_customer", Map.of("id", 4)))).contains("Musterstadt");
        assertThat(call("create_customer", Map.of("customer", CUSTOMER)).isError()).isFalse();
        verify(customers).create(any(Customer.class));
        call("update_customer", Map.of("id", 4, "customer", CUSTOMER));
        verify(customers).update(eq(4L), any(Customer.class));

        call("list_templates", Map.of("customerId", 4));
        verify(templates).list(4L);
        call("get_template", Map.of("customerId", 4, "id", 7));
        verify(templates).get(4L, 7L);
        when(templates.create(4L)).thenReturn(ResponseEntity.ok().build());
        call("create_template", Map.of("customerId", 4));
        verify(templates).create(4L);
        call("save_template", Map.of("customerId", 4, "id", 7, "form", Map.of("version", 3, "name", "Lesung")));
        verify(templates).save(eq(4L), eq(7L), any(TemplateForm.class));

        call("get_profile", Map.of());
        verify(settings).profile();
        call("save_profile", Map.of("part", "bank", "profile", Map.of("iban", "DE89370400440532013000")));
        verify(settings).saveProfile(eq("bank"), any(SellerProfile.class));
        call("get_mail_texts", Map.of());
        verify(settings).mailTexts();
        call("save_mail_text", Map.of("kind", "invoice", "subject", "S", "body", "B"));
        verify(settings).saveInvoiceText(new MailText("S", "B"));
        call("save_mail_text", Map.of("kind", "reminder", "subject", "E", "body", "O"));
        verify(settings).saveReminderText(new MailText("E", "O"));
        assertThat(text(call("save_mail_text", Map.of("kind", "brief", "subject", "S", "body", "B"))))
                .isEqualTo("Die Art ist invoice oder reminder.");
        call("get_tax_notes", Map.of());
        verify(settings).taxNotes();
        call("save_tax_notes", Map.of("notes", Map.of("KLEINUNTERNEHMER", "Neu")));
        verify(settings).saveTaxNotes(Map.of(TaxCategory.KLEINUNTERNEHMER, "Neu"));
        assertThat(text(call("save_tax_notes", Map.of("notes", Map.of("UNBEKANNT", "x")))))
                .startsWith("Das Argument „notes“ passt nicht zum Schema: ");
        call("get_number_pattern", Map.of());
        verify(settings).numberPattern();
        call("save_number_pattern", Map.of("pattern", "RE-{YYYY}-{NN}"));
        verify(settings).saveNumberPattern(new ApiSettingsController.NumberPatternView("RE-{YYYY}-{NN}", null));
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
            READ        | SCOPE_READ                         |
            WRITE       | SCOPE_READ                         | Der API-Token darf nur lesen.
            WRITE       | SCOPE_READ,SCOPE_WRITE             |
            ADMIN_READ  | ROLE_USER,SCOPE_READ,SCOPE_WRITE   | Nur mit einem API-Token des Administrators.
            ADMIN_READ  | ROLE_ADMIN,SCOPE_READ              |
            ADMIN_WRITE | ROLE_ADMIN,SCOPE_READ              | Der API-Token darf nur lesen.
            ADMIN_WRITE | ROLE_USER,SCOPE_READ,SCOPE_WRITE   | Nur mit einem API-Token des Administrators.
            ADMIN_WRITE | ROLE_ADMIN,SCOPE_READ,SCOPE_WRITE  |
            """)
    void checksTheTokenPerTool(McpTools.Access access, String authorities, String refusal) {
        login(authorities.split(","));
        if (refusal == null) {
            McpTools.check(access);
        } else {
            assertThatThrownBy(() -> McpTools.check(access)).isInstanceOf(McpTools.Refused.class).hasMessage(refusal);
        }
    }

    @Test
    void withoutALoginOnlyNothing() {
        assertThatThrownBy(() -> McpTools.check(McpTools.Access.WRITE)).hasMessage("Der API-Token darf nur lesen.");
        McpTools.check(McpTools.Access.READ);
    }

    @Test
    void answersEveryRefusalAsAnError() {
        login("ROLE_USER", ApiTokenScope.READ_AUTHORITY, ApiTokenScope.WRITE_AUTHORITY);
        assertError(args -> {
            throw new CustomerException(List.of("Name fehlt.", "Ort fehlt."));
        }, "Name fehlt.\nOrt fehlt.");
        assertError(args -> {
            throw new SettingsException(List.of("Zu lang."));
        }, "Zu lang.");
        assertError(args -> {
            throw new InvoiceException(List.of("Diese Vorlage gibt es nicht."));
        }, "Diese Vorlage gibt es nicht.");
        assertError(args -> {
            throw new NotFoundException("Diesen Kunden gibt es nicht.");
        }, "Diesen Kunden gibt es nicht.");
        assertError(args -> {
            throw new IllegalStateException("intern");
        }, "Ein Fehler im Server. Das Protokoll nennt ihn.");
        assertThat(text(call("get_customer", Map.of()))).isEqualTo("Das Argument „id“ fehlt.");
        assertThat(text(call("get_customer", Map.of("id", "vier")))).startsWith("Das Argument „id“ passt nicht zum Schema: ");
        assertThat(text(call("get_tax_notes", Map.of()))).isEqualTo("Nur mit einem API-Token des Administrators.");
    }

    @Test
    void takesNoArgumentsAsEmpty() {
        login(ApiTokenScope.READ_AUTHORITY);
        CallToolResult result = tools.call(McpTools.Access.READ, Map::size, null);
        assertThat(result.isError()).isFalse();
        assertThat(text(result)).isEqualTo("0");
    }

    @Test
    void refusesAResultThatDoesNotWriteAsJson() {
        login(ApiTokenScope.READ_AUTHORITY);
        CallToolResult result = tools.call(McpTools.Access.READ, args -> new Unwritable(), Map.of());
        assertThat(result.isError()).isTrue();
        assertThat(text(result)).isEqualTo("Ein Fehler im Server. Das Protokoll nennt ihn.");
    }

    /** A result whose field fails to read. */
    static final class Unwritable {
        public String getValue() {
            throw new IllegalStateException("kaputt");
        }
    }

    private void assertError(Function<Map<String, Object>, Object> call, String message) {
        CallToolResult result = tools.call(McpTools.Access.READ, call, Map.of());
        assertThat(result.isError()).isTrue();
        assertThat(text(result)).isEqualTo(message);
    }
}
