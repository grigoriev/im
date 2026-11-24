package io.github.grigoriev.im.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.github.grigoriev.im.system.AppVersion;
import io.github.grigoriev.im.system.SystemService;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.server.McpStatelessSyncServer;
import io.modelcontextprotocol.server.transport.HttpServletStatelessServerTransport;
import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import tools.jackson.databind.json.JsonMapper;

class McpConfigTest {

    @ParameterizedTest
    @CsvSource(value = {"0.1.16, 0.1.16", "NULL, dev"}, nullValues = "NULL")
    void buildsTheServerWithTheVersionOfTheApp(String number, String version) {
        McpConfig config = new McpConfig();
        McpJsonMapper json = config.mcpJsonMapper(JsonMapper.builder().build());
        HttpServletStatelessServerTransport transport = config.mcpTransport(json);
        McpTools tools = mock(McpTools.class);
        when(tools.specifications()).thenReturn(List.of());
        SystemService system = mock(SystemService.class);
        when(system.version()).thenReturn(new AppVersion(number, null));
        McpStatelessSyncServer server = config.mcpServer(transport, json, tools, system);
        assertThat(server.getServerInfo().name()).isEqualTo("im");
        assertThat(server.getServerInfo().version()).isEqualTo(version);
        server.close();
        assertThat(config.mcpServlet(transport).getUrlMappings()).containsExactly("/mcp");
    }
}
