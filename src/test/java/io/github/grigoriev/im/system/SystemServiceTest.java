package io.github.grigoriev.im.system;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.info.BuildProperties;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.core.simple.JdbcClient.MappedQuerySpec;
import org.springframework.jdbc.core.simple.JdbcClient.StatementSpec;

class SystemServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-23T06:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    private final JdbcClient jdbc = mock(JdbcClient.class);

    @SuppressWarnings("unchecked")
    private static ObjectProvider<BuildProperties> provider(BuildProperties properties) {
        ObjectProvider<BuildProperties> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(properties);
        return provider;
    }

    private static BuildProperties build(String version, String time) {
        Properties entries = new Properties();
        entries.setProperty("version", version);
        entries.setProperty("time", time);
        return new BuildProperties(entries);
    }

    @SuppressWarnings("unchecked")
    private void answer(String sql, String value) {
        StatementSpec statement = mock(StatementSpec.class);
        MappedQuerySpec<String> query = mock(MappedQuerySpec.class);
        when(jdbc.sql(sql)).thenReturn(statement);
        when(statement.query(String.class)).thenReturn(query);
        when(query.single()).thenReturn(value);
    }

    @Test
    void namesTheVersionOfTheBuild() {
        SystemService system = new SystemService(provider(build("0.1.12", "2026-11-23T05:24:00Z")), jdbc, CLOCK);
        assertThat(system.version()).isEqualTo(new AppVersion("0.1.12", Instant.parse("2026-11-23T05:24:00Z")));
    }

    @Test
    void namesNoVersionWithoutBuildInfo() {
        SystemService system = new SystemService(provider(null), jdbc, CLOCK);
        assertThat(system.version()).isEqualTo(new AppVersion(null, null));
    }

    @Test
    void collectsWhatRuns() {
        answer(SystemService.SCHEMA, "36");
        answer(SystemService.DATABASE, "17.2");
        SystemService system = new SystemService(provider(build("0.1.12", "2026-11-23T05:24:00Z")), jdbc, CLOCK);
        SystemInfo info = system.info();
        assertThat(info.version()).isEqualTo(system.version());
        assertThat(info.schema()).isEqualTo("36");
        assertThat(info.database()).isEqualTo("17.2");
        assertThat(info.java()).isEqualTo(Runtime.version().toString());
        assertThat(info.startedAt()).isEqualTo(NOW);
    }
}
