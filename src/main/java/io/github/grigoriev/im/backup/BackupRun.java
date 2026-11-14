package io.github.grigoriev.im.backup;

import java.time.OffsetDateTime;

/**
 * The last successful run of a backup job on the host.
 *
 * @param kind {@code BACKUP} or {@code RESTORE_TEST}
 * @param succeededAt when it finished
 * @param detail what the script reports, for example the snapshot and its size
 */
public record BackupRun(String kind, OffsetDateTime succeededAt, String detail) {
}
