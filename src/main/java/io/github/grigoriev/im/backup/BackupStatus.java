package io.github.grigoriev.im.backup;

/**
 * The state of the automatic backup for the admin page.
 *
 * @param backup the last successful nightly backup, {@code null} before the first
 * @param restoreTest the last successful restore test, {@code null} before the first
 * @param backupOverdue whether the last backup is missing or too old
 * @param restoreTestOverdue whether the last restore test is missing or too old
 */
public record BackupStatus(BackupRun backup, BackupRun restoreTest, boolean backupOverdue,
        boolean restoreTestOverdue) {
}
