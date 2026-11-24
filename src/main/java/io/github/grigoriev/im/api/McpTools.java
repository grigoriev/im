package io.github.grigoriev.im.api;

import io.github.grigoriev.im.customer.Customer;
import io.github.grigoriev.im.customer.CustomerException;
import io.github.grigoriev.im.einvoice.TaxCategory;
import io.github.grigoriev.im.invoice.InvoiceException;
import io.github.grigoriev.im.invoice.TemplateForm;
import io.github.grigoriev.im.mail.MailText;
import io.github.grigoriev.im.seller.SellerProfile;
import io.github.grigoriev.im.seller.SettingsException;
import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.server.McpStatelessServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import io.modelcontextprotocol.spec.McpSchema.ToolAnnotations;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * The tools of the MCP server. Each calls the controller of the REST API, so both share the checks; the access rules
 * of the URLs apply here per tool: a write needs the scope {@code WRITE}, the settings of the admin the role.
 */
@Component
public class McpTools {

    private static final Logger LOG = LoggerFactory.getLogger(McpTools.class);

    /** Who may call a tool. */
    enum Access {
        /** Every token. */
        READ,
        /** A token that writes. */
        WRITE,
        /** A token of the admin. */
        ADMIN_READ,
        /** A token of the admin that writes. */
        ADMIN_WRITE
    }

    /** A tool call that cannot go on: the message is the answer. */
    static final class Refused extends RuntimeException {

        private static final long serialVersionUID = 1L;

        Refused(String message) {
            super(message);
        }
    }

    private static final String CUSTOMER = """
            {"type": "object", "description": "A customer, the fields of the form \\"Kunden\\"",
             "properties": {
              "name": {"type": "string"}, "addressAddition": {"type": ["string", "null"],
               "description": "Abteilung / Zusatz, below the name"},
              "street": {"type": "string"}, "postcode": {"type": "string"}, "city": {"type": "string"},
              "countryCode": {"type": "string", "description": "ISO 3166-1 alpha-2, for example DE"},
              "contactName": {"type": ["string", "null"], "description": "Ansprechpartner, for example Frau Weber"},
              "email": {"type": ["string", "null"]}, "vatId": {"type": ["string", "null"]},
              "buyerReference": {"type": ["string", "null"], "description": "Leitweg-ID, XRechnung needs it"},
              "sellerIdentifier": {"type": ["string", "null"], "description": "Geschäftspartnernummer"},
              "format": {"type": "string", "enum": ["ZUGFERD_EN16931", "ZUGFERD_XRECHNUNG", "XRECHNUNG", "EXTERNAL"]},
              "activity": {"type": ["string", "null"], "enum": ["TEACHING", "OTHER", null],
               "description": "TEACHING for §4 Nr. 21 UStG, OTHER, or null to follow the lines"}},
             "required": ["name", "street", "postcode", "city", "countryCode", "format"]}""";
    private static final String NONE = """
            {"type": "object", "properties": {}}""";
    private static final String ID = """
            {"type": "object", "properties": {"id": {"type": "integer"}}, "required": ["id"]}""";
    private static final String CUSTOMER_ID = """
            {"type": "object", "properties": {"customerId": {"type": "integer"}}, "required": ["customerId"]}""";
    private static final String TEMPLATE_ID = """
            {"type": "object", "properties": {"customerId": {"type": "integer"}, "id": {"type": "integer"}},
             "required": ["customerId", "id"]}""";

    private final Map<String, Access> access = new HashMap<>();
    private final ApiCustomerController customers;
    private final ApiTemplateController templates;
    private final ApiSettingsController settings;
    private final McpJsonMapper mcpJson;
    private final JsonMapper json;

    /**
     * Creates the tools.
     *
     * @param customers the customers of the API
     * @param templates the templates of the API
     * @param settings the settings of the API
     * @param mcpJson reads the schemas
     * @param json reads the arguments and writes the results
     */
    public McpTools(ApiCustomerController customers, ApiTemplateController templates, ApiSettingsController settings,
            McpJsonMapper mcpJson, JsonMapper json) {
        this.customers = customers;
        this.templates = templates;
        this.settings = settings;
        this.mcpJson = mcpJson;
        this.json = json;
    }

    /**
     * Returns every tool.
     *
     * @return the tools with their handlers
     */
    public List<SyncToolSpecification> specifications() {
        return List.of(
                tool("list_customers", "Lists all customers by name and contact person.", NONE, Access.READ,
                        args -> customers.list()),
                tool("get_customer", "Returns one customer.", ID, Access.READ, args -> customers.get(id(args, "id"))),
                tool("create_customer", "Creates a customer. Returns it with its id.", wrap("customer", CUSTOMER),
                        Access.WRITE, args -> customers.create(arg(args, "customer", Customer.class)).getBody()),
                tool("update_customer", "Changes every field of a customer. Read it first with get_customer.",
                        """
                        {"type": "object", "properties": {"id": {"type": "integer"}, "customer": %s},
                         "required": ["id", "customer"]}""".formatted(CUSTOMER), Access.WRITE,
                        args -> customers.update(id(args, "id"), arg(args, "customer", Customer.class))),
                tool("list_templates", "Lists the invoice templates of a customer.", CUSTOMER_ID, Access.READ,
                        args -> templates.list(id(args, "customerId"))),
                tool("get_template", """
                        Returns an invoice template as the form of its editor. Amounts are German text, for example \
                        "1.234,56". Change it and send it back with save_template, with its version.""",
                        TEMPLATE_ID, Access.READ, args -> templates.get(id(args, "customerId"), id(args, "id"))),
                tool("create_template", "Creates an invoice template with the defaults of the customer.", CUSTOMER_ID,
                        Access.WRITE, args -> templates.create(id(args, "customerId")).getBody()),
                tool("save_template", """
                        Saves an invoice template: the form that get_template returned, changed. A version that \
                        changed meanwhile is refused.""", """
                        {"type": "object", "properties": {"customerId": {"type": "integer"}, "id": {"type": "integer"},
                         "form": {"type": "object", "description": "The form of get_template"}},
                         "required": ["customerId", "id", "form"]}""", Access.WRITE,
                        args -> templates.save(id(args, "customerId"), id(args, "id"),
                                arg(args, "form", TemplateForm.class))),
                tool("get_profile", "Returns the profile of the seller: address, contact, tax, bank.", NONE,
                        Access.READ, args -> settings.profile()),
                tool("save_profile", """
                        Saves one tab of the profile of the seller: address, contact, tax or bank. Read it first with \
                        get_profile and send all fields; those of other tabs are ignored.""", """
                        {"type": "object", "properties": {
                          "part": {"type": "string", "enum": ["address", "contact", "tax", "bank"]},
                          "profile": {"type": "object", "description": "The profile of get_profile"}},
                         "required": ["part", "profile"]}""", Access.WRITE,
                        args -> settings.saveProfile(arg(args, "part", String.class),
                                arg(args, "profile", SellerProfile.class))),
                tool("get_mail_texts", "Returns the default e-mail texts of an invoice and of a payment reminder.",
                        NONE, Access.READ, args -> settings.mailTexts()),
                tool("save_mail_text", "Saves the default e-mail text of an invoice or of a payment reminder.", """
                        {"type": "object", "properties": {
                          "kind": {"type": "string", "enum": ["invoice", "reminder"]},
                          "subject": {"type": "string"}, "body": {"type": "string"}},
                         "required": ["kind", "subject", "body"]}""", Access.WRITE, this::saveMailText),
                tool("get_tax_notes", "Returns the default tax note per tax category. Admin only.", NONE,
                        Access.ADMIN_READ, args -> settings.taxNotes()),
                tool("save_tax_notes", "Changes default tax notes per tax category. Admin only.", """
                        {"type": "object", "properties": {"notes": {"type": "object",
                          "additionalProperties": {"type": "string"},
                          "description": "Notes by category, for example KLEINUNTERNEHMER"}},
                         "required": ["notes"]}""", Access.ADMIN_WRITE,
                        args -> settings.saveTaxNotes(arg(args, "notes", new TypeReference<Map<TaxCategory, String>>() {
                        }))),
                tool("get_number_pattern", "Returns the invoice number pattern and the next numbers. Admin only.",
                        NONE, Access.ADMIN_READ, args -> settings.numberPattern()),
                tool("save_number_pattern", "Changes the invoice number pattern, for example {YYYY}-{NN}. Admin only.",
                        """
                        {"type": "object", "properties": {"pattern": {"type": "string"}}, "required": ["pattern"]}""",
                        Access.ADMIN_WRITE, args -> settings.saveNumberPattern(
                                new ApiSettingsController.NumberPatternView(arg(args, "pattern", String.class), null))));
    }

    private Object saveMailText(Map<String, Object> args) {
        MailText text = new MailText(arg(args, "subject", String.class), arg(args, "body", String.class));
        return switch (arg(args, "kind", String.class)) {
            case "invoice" -> settings.saveInvoiceText(text);
            case "reminder" -> settings.saveReminderText(text);
            default -> throw new Refused("Die Art ist invoice oder reminder.");
        };
    }

    private static String wrap(String name, String schema) {
        return """
                {"type": "object", "properties": {"%s": %s}, "required": ["%s"]}""".formatted(name, schema, name);
    }

    /**
     * Returns a tool with its schema and handler.
     *
     * @param name the name
     * @param description what it does, for the assistant
     * @param schema the JSON schema of its arguments
     * @param access who may call it
     * @param call what it does with the arguments
     * @return the specification
     */
    SyncToolSpecification tool(String name, String description, String schema, Access access,
            Function<Map<String, Object>, Object> call) {
        this.access.put(name, access);
        Tool tool = Tool.builder().name(name).description(description).inputSchema(mcpJson, schema)
                .annotations(annotations(name)).build();
        return SyncToolSpecification.builder().tool(tool)
                .callHandler((context, request) -> call(access, call, request.arguments())).build();
    }

    /**
     * Returns the hints of a tool for the assistant: {@code get_} and {@code list_} only read, {@code create_} adds
     * something new each time, the others overwrite what is there.
     *
     * @param name the name of the tool
     * @return the hints
     */
    static ToolAnnotations annotations(String name) {
        if (name.startsWith("get_") || name.startsWith("list_")) {
            return new ToolAnnotations(null, true, false, true, false, null);
        }
        if (name.startsWith("create_")) {
            return new ToolAnnotations(null, false, false, false, false, null);
        }
        return new ToolAnnotations(null, false, true, true, false, null);
    }

    /**
     * Filters the list of tools for the token of the request.
     *
     * @param context the transport, unused: the login sits in the security context
     * @param tool the tool
     * @return {@code true} if the token may call it
     */
    public boolean visible(McpTransportContext context, Tool tool) {
        return visible(tool.name());
    }

    /**
     * Tells whether the token of the request may call a tool, so the list shows it only what it may use.
     *
     * @param name the name of the tool
     * @return {@code true} if the check passes
     */
    public boolean visible(String name) {
        try {
            check(access.getOrDefault(name, Access.ADMIN_WRITE));
            return true;
        } catch (Refused _) {
            return false;
        }
    }

    /**
     * Runs a tool call: checks the access, calls, and answers the result or the refusal as text.
     *
     * @param access who may call it
     * @param call what it does
     * @param args the arguments, may be {@code null}
     * @return the result
     */
    CallToolResult call(Access access, Function<Map<String, Object>, Object> call, Map<String, Object> args) {
        try {
            check(access);
            return CallToolResult.builder().addTextContent(json.writeValueAsString(call.apply(args == null ? Map.of() : args)))
                    .build();
        } catch (CustomerException e) {
            return refused(e.problems());
        } catch (SettingsException e) {
            return refused(e.problems());
        } catch (InvoiceException e) {
            return refused(e.problems());
        } catch (Refused | NotFoundException e) {
            return refused(List.of(e.getMessage()));
        } catch (RuntimeException e) {
            LOG.error("MCP tool failed", e);
            return refused(List.of(ProblemResponse.detail(500)));
        }
    }

    private static CallToolResult refused(List<String> problems) {
        return CallToolResult.builder().addTextContent(String.join("\n", problems)).isError(true).build();
    }

    /**
     * Checks the access of the login of the request.
     *
     * @param access who may call the tool
     * @throws Refused if the token may not
     */
    static void check(Access access) {
        Authentication login = SecurityContextHolder.getContext().getAuthentication();
        List<String> authorities = login == null ? List.of()
                : login.getAuthorities().stream().map(GrantedAuthority::getAuthority).toList();
        boolean admin = access == Access.ADMIN_READ || access == Access.ADMIN_WRITE;
        boolean write = access == Access.WRITE || access == Access.ADMIN_WRITE;
        if (admin && !authorities.contains("ROLE_ADMIN")) {
            throw new Refused("Nur mit einem API-Token des Administrators.");
        }
        if (write && !authorities.contains(ApiTokenScope.WRITE_AUTHORITY)) {
            throw new Refused("Der API-Token darf nur lesen.");
        }
    }

    private <T> T arg(Map<String, Object> args, String name, Class<T> type) {
        Object value = present(args, name);
        try {
            return json.convertValue(value, type);
        } catch (JacksonException e) {
            throw new Refused(unfit(name, e));
        }
    }

    private <T> T arg(Map<String, Object> args, String name, TypeReference<T> type) {
        Object value = present(args, name);
        try {
            return json.convertValue(value, type);
        } catch (JacksonException e) {
            throw new Refused(unfit(name, e));
        }
    }

    private static String unfit(String name, JacksonException e) {
        LOG.debug("MCP argument {} does not convert", name, e);
        return "Das Argument „" + name + "“ passt nicht zum Schema.";
    }

    private long id(Map<String, Object> args, String name) {
        return arg(args, name, Long.class);
    }

    private static Object present(Map<String, Object> args, String name) {
        Object value = args.get(name);
        if (value == null) {
            throw new Refused("Das Argument „" + name + "“ fehlt.");
        }
        return value;
    }
}
