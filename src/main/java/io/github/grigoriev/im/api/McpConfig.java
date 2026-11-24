package io.github.grigoriev.im.api;

import io.github.grigoriev.im.config.PasskeyProperties;
import io.github.grigoriev.im.system.SystemService;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.json.jackson3.JacksonMcpJsonMapper;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpStatelessSyncServer;
import io.modelcontextprotocol.server.transport.DefaultServerTransportSecurityValidator;
import io.modelcontextprotocol.server.transport.HttpServletStatelessServerTransport;
import io.modelcontextprotocol.spec.McpSchema;
import java.util.Objects;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.json.JsonMapper;

/**
 * The MCP server under {@code /mcp} (docs/adr/0007-api-tokens-and-mcp.md). It uses the stateless servlet transport of
 * the MCP Java SDK and runs each tool on the thread of the request, so the tool sees the login of the API token and
 * joins its transactions. The filter chain of the API guards the URL.
 */
@Configuration(proxyBeanMethods = false)
public class McpConfig {

    /** The URL of the MCP server. */
    static final String PATH = "/mcp";
    /** The largest request: a template with its hundred lines fits many times over. */
    static final int MAX_REQUEST = 1024 * 1024;

    static final String INSTRUCTIONS = """
            Invoice Manager (im) of a German freelancer. The tools read and change customers, their invoice templates \
            and the settings, with the checks of the forms. Texts are German. Issuing, canceling and deleting stay in \
            the app. A write needs a token with "Lesen und schreiben"; tax notes and the number pattern need the admin.\
            """;

    /**
     * Returns the JSON mapper of the MCP server: the one of the app, so records read and write as in the REST API.
     *
     * @param json the JSON mapper of the app
     * @return the mapper
     */
    @Bean
    public McpJsonMapper mcpJsonMapper(JsonMapper json) {
        return new JacksonMcpJsonMapper(json);
    }

    /**
     * Returns the transport: a servlet that answers each request on its own, without a session. A request from a
     * browser page must come from an origin of the app, as for the passkeys; an assistant sends no origin.
     *
     * @param json the JSON mapper
     * @param passkeys the origins of the app
     * @return the transport
     */
    @Bean
    public HttpServletStatelessServerTransport mcpTransport(McpJsonMapper json, PasskeyProperties passkeys) {
        return HttpServletStatelessServerTransport.builder().jsonMapper(json).messageEndpoint(PATH)
                .securityValidator(DefaultServerTransportSecurityValidator.builder()
                        .allowedOrigins(passkeys.allowedOrigins()).build())
                .maxRequestSize(MAX_REQUEST)
                .build();
    }

    /**
     * Returns the MCP server with its tools.
     *
     * @param transport the transport
     * @param json the JSON mapper
     * @param tools the tools
     * @param system the version of the app
     * @return the server, closed with the app
     */
    @Bean(destroyMethod = "close")
    public McpStatelessSyncServer mcpServer(HttpServletStatelessServerTransport transport, McpJsonMapper json,
            McpTools tools, SystemService system) {
        return McpServer.sync(transport)
                .serverInfo("im", Objects.requireNonNullElse(system.version().number(), "dev"))
                .instructions(INSTRUCTIONS)
                .jsonMapper(json)
                .capabilities(McpSchema.ServerCapabilities.builder().tools(false).build())
                .immediateExecution(true)
                .addToolFilter(tools::visible)
                .tools(tools.specifications())
                .build();
    }

    /**
     * Registers the transport under {@code /mcp}.
     *
     * @param transport the transport
     * @return the registration
     */
    @Bean
    public ServletRegistrationBean<HttpServletStatelessServerTransport> mcpServlet(
            HttpServletStatelessServerTransport transport) {
        ServletRegistrationBean<HttpServletStatelessServerTransport> registration =
                new ServletRegistrationBean<>(transport, PATH);
        registration.setName("mcp");
        return registration;
    }
}
