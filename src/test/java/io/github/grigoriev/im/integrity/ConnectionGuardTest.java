package io.github.grigoriev.im.integrity;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.core.simple.JdbcClient.MappedQuerySpec;
import org.springframework.jdbc.core.simple.JdbcClient.StatementSpec;

class ConnectionGuardTest {

    private final JdbcClient jdbc = mock(JdbcClient.class);
    @SuppressWarnings("unchecked")
    private final MappedQuerySpec<Boolean> answer = mock(MappedQuerySpec.class);

    private ConnectionGuard guard(boolean mighty) {
        StatementSpec statement = mock(StatementSpec.class);
        when(jdbc.sql(ConnectionGuard.MIGHTY)).thenReturn(statement);
        when(statement.query(Boolean.class)).thenReturn(answer);
        when(answer.single()).thenReturn(mighty);
        return new ConnectionGuard(jdbc);
    }

    @Test
    void startsWithARoleThatOnlyReadsAndWritesRows() {
        assertThatCode(() -> guard(false).run(null)).doesNotThrowAnyException();
    }

    @Test
    void refusesTheOwnerOrASuperuser() {
        assertThatThrownBy(() -> guard(true).run(null)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("IM_DB_USER").hasMessageContaining("IM_DB_OWNER");
    }
}
