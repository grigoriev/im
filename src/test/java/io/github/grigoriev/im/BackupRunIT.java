package io.github.grigoriev.im;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import io.github.grigoriev.im.backup.BackupRunService;
import io.github.grigoriev.im.backup.BackupStatus;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * The status of the automatic backup against PostgreSQL: the statement of the host script writes a run, the service
 * reads it back with its time zone, and the table takes the two kinds only.
 */
@SpringBootTest
@Import(TestDatabase.class)
class BackupRunIT {

    /** The statement of the host script (intranet repository, lemming/backup/im-backup.sh), with fixed values. */
    private static final String UPSERT = """
            INSERT INTO backup_run (kind, succeeded_at, detail) VALUES (:kind, now(), :detail)
            ON CONFLICT (kind) DO UPDATE SET succeeded_at = EXCLUDED.succeeded_at, detail = EXCLUDED.detail""";

    @Autowired
    private BackupRunService runs;

    @Autowired
    private JdbcClient jdbc;

    @Test
    void readsWhatTheHostScriptWrites() {
        jdbc.sql("DELETE FROM backup_run").update();
        assertThat(runs.status()).isEqualTo(new BackupStatus(null, null, true, true));

        jdbc.sql(UPSERT).param("kind", "BACKUP").param("detail", "Snapshot 1a2b3c4d, 1,2 MB").update();
        jdbc.sql(UPSERT).param("kind", "BACKUP").param("detail", "Snapshot 5e6f7a8b, 1,3 MB").update();
        BackupStatus status = runs.status();
        assertThat(status.backup().detail()).isEqualTo("Snapshot 5e6f7a8b, 1,3 MB");
        assertThat(status.backup().succeededAt().toInstant()).isCloseTo(Instant.now(), within(1, ChronoUnit.MINUTES));
        assertThat(status.backupOverdue()).isFalse();
        assertThat(status.restoreTest()).isNull();
        assertThat(status.restoreTestOverdue()).isTrue();

        JdbcClient.StatementSpec other = jdbc.sql(UPSERT).param("kind", "OTHER").param("detail", "");
        assertThatThrownBy(other::update).isInstanceOf(DataIntegrityViolationException.class);
        jdbc.sql("DELETE FROM backup_run").update();
    }
}
