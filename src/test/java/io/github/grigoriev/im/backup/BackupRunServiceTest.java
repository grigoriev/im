package io.github.grigoriev.im.backup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.core.simple.JdbcClient.MappedQuerySpec;
import org.springframework.jdbc.core.simple.JdbcClient.StatementSpec;

class BackupRunServiceTest {

    private static final Instant NOW = Instant.parse("2027-03-18T08:00:00Z");

    private final JdbcClient jdbc = mock(JdbcClient.class);
    private final StatementSpec statement = mock(StatementSpec.class);
    @SuppressWarnings("unchecked")
    private final MappedQuerySpec<BackupRun> query = mock(MappedQuerySpec.class);
    private final BackupRunService service = new BackupRunService(jdbc, Clock.fixed(NOW, ZoneOffset.UTC));

    private static BackupRun run(String kind, Duration age) {
        return new BackupRun(kind, OffsetDateTime.ofInstant(NOW.minus(age), ZoneOffset.UTC), "");
    }

    private BackupStatus statusOf(List<BackupRun> runs) {
        when(jdbc.sql(BackupRunService.RUNS)).thenReturn(statement);
        when(statement.query(BackupRun.class)).thenReturn(query);
        when(query.list()).thenReturn(runs);
        return service.status();
    }

    @Test
    void withoutRunsBothAreOverdue() {
        assertThat(statusOf(List.of())).isEqualTo(new BackupStatus(null, null, true, true));
    }

    @Test
    void readsBothRunsAndJudgesEachByItsOwnLimit() {
        BackupRun backup = run("BACKUP", Duration.ofHours(5));
        BackupRun test = run("RESTORE_TEST", Duration.ofDays(20));
        assertThat(statusOf(List.of(test, backup))).isEqualTo(new BackupStatus(backup, test, false, false));

        BackupRun oldBackup = run("BACKUP", Duration.ofHours(40));
        BackupRun oldTest = run("RESTORE_TEST", Duration.ofDays(36));
        assertThat(statusOf(List.of(oldBackup, oldTest))).isEqualTo(new BackupStatus(oldBackup, oldTest, true, true));
    }

    @ParameterizedTest
    @CsvSource({"PT36H, false", "PT36H0.001S, true", "PT35H59M, false"})
    void aRunIsOverdueOnlyAfterTheLimit(Duration age, boolean overdue) {
        assertThat(BackupRunService.overdue(run("BACKUP", age), BackupRunService.BACKUP_DUE, NOW)).isEqualTo(overdue);
        assertThat(BackupRunService.overdue(null, BackupRunService.BACKUP_DUE, NOW)).isTrue();
    }
}
