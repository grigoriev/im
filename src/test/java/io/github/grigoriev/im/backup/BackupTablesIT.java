package io.github.grigoriev.im.backup;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.grigoriev.im.TestDatabase;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * The table lists of the backup against the schema: a new table goes into the export, or into the list of tables left
 * out here with its reason. The tables that make an installation not empty are those that keep the audit log.
 */
@SpringBootTest
@Import(TestDatabase.class)
class BackupTablesIT {

    /**
     * The tables the export leaves out: remember-me logins end with the import, the Flyway history and the schema
     * marker belong to the installation, and the host scripts write the backup runs of this installation.
     */
    private static final Set<String> LEFT_OUT = Set.of("persistent_logins", "api_token", "flyway_schema_history", "app_info",
            "backup_run");

    /** The tables that hang on an invoice or a template: empty while their parents are. */
    private static final Set<String> CHILDREN = Set.of("invoice_template_line", "invoice_line", "invoice_document",
            "invoice_original", "dispatch", "payment", "reminder");

    private static final Pattern FROM = Pattern.compile("FROM (\\w+)");

    @Autowired
    private JdbcTemplate jdbc;

    private List<String> tables() {
        return jdbc.queryForList("SELECT tablename FROM pg_tables WHERE schemaname = current_schema()", String.class);
    }

    @Test
    void theExportHoldsEveryTableButThoseLeftOut() {
        List<String> tables = tables();
        assertThat(tables).containsAll(LEFT_OUT).containsAll(CHILDREN);
        assertThat(BackupService.TABLES).doesNotHaveDuplicates()
                .containsExactlyInAnyOrderElementsOf(tables.stream().filter(table -> !LEFT_OUT.contains(table)).toList());
        assertThat(BackupService.SETUP).doesNotHaveDuplicates().isSubsetOf(tables);
        assertThat(BackupService.BUSINESS).doesNotHaveDuplicates().isSubsetOf(BackupService.TABLES)
                .doesNotContainAnyElementsOf(BackupService.SETUP);
        List<String> known = new ArrayList<>(BackupService.SETUP);
        known.addAll(BackupService.BUSINESS);
        known.addAll(CHILDREN);
        assertThat(BackupService.TABLES).as("each table is setup, business, or hangs on business").isSubsetOf(known);
    }

    @Test
    void theTablesThatKeepTheAuditLogAreTheBusinessTablesOfTheImport() {
        String function = jdbc.queryForObject("SELECT pg_get_functiondef('audit_event_kept'::regproc)", String.class);
        List<String> keeping = new ArrayList<>();
        Matcher from = FROM.matcher(function);
        while (from.find()) {
            keeping.add(from.group(1));
        }
        assertThat(keeping).containsExactlyInAnyOrderElementsOf(BackupService.BUSINESS);
    }
}
