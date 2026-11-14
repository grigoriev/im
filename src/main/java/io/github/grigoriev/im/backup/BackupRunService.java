package io.github.grigoriev.im.backup;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

/**
 * Reads the last successful backup and restore test (docs/PLAN.md, section 9). The scripts on the host write them
 * into {@code backup_run}; the app only reads.
 */
@Service
public class BackupRunService {

    static final String BACKUP = "BACKUP";
    static final String RESTORE_TEST = "RESTORE_TEST";

    /** A nightly backup older than this is overdue: one night may fail, two may not. */
    static final Duration BACKUP_DUE = Duration.ofHours(36);
    /** A monthly restore test older than this is overdue. */
    static final Duration RESTORE_TEST_DUE = Duration.ofDays(35);

    static final String RUNS = "SELECT kind, succeeded_at, detail FROM backup_run";

    private final JdbcClient jdbc;
    private final Clock clock;

    /**
     * Creates the service.
     *
     * @param jdbc reads the runs
     * @param clock the time to judge the age of a run
     */
    public BackupRunService(JdbcClient jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    /**
     * Returns the last runs and whether they are overdue.
     *
     * @return the status
     */
    public BackupStatus status() {
        Map<String, BackupRun> runs = jdbc.sql(RUNS).query(BackupRun.class).list().stream()
                .collect(Collectors.toMap(BackupRun::kind, Function.identity()));
        BackupRun backup = runs.get(BACKUP);
        BackupRun restoreTest = runs.get(RESTORE_TEST);
        Instant now = clock.instant();
        return new BackupStatus(backup, restoreTest, overdue(backup, BACKUP_DUE, now),
                overdue(restoreTest, RESTORE_TEST_DUE, now));
    }

    /** A missing run is overdue, and so is one that finished longer ago than allowed. */
    static boolean overdue(BackupRun run, Duration due, Instant now) {
        return run == null || run.succeededAt().toInstant().plus(due).isBefore(now);
    }
}
