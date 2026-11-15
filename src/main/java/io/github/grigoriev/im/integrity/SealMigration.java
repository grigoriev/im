package io.github.grigoriev.im.integrity;

import java.util.List;
import org.flywaydb.core.api.MigrationVersion;
import org.flywaydb.core.api.migration.Context;
import org.flywaydb.core.api.migration.JavaMigration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.stereotype.Component;

/**
 * Migration 29: seals every document and issued invoice that exists. It runs as the owner of the tables, so it may
 * switch the triggers off for the time it writes the seals. A file whose SHA-256 does not match stops the migration:
 * the seal would confirm a change.
 */
@Component
public class SealMigration implements JavaMigration {

    static final List<String> DISABLE = List.of(
            "ALTER TABLE invoice_document DISABLE TRIGGER invoice_document_frozen",
            "ALTER TABLE business_document DISABLE TRIGGER business_document_frozen",
            "ALTER TABLE invoice DISABLE TRIGGER invoice_frozen");
    static final List<String> ENABLE = List.of(
            "ALTER TABLE invoice_document ENABLE TRIGGER invoice_document_frozen",
            "ALTER TABLE business_document ENABLE TRIGGER business_document_frozen",
            "ALTER TABLE invoice ENABLE TRIGGER invoice_frozen");
    static final String DOCUMENT_IDS = "SELECT id FROM invoice_document ORDER BY id";
    static final String DOCUMENT = "SELECT invoice_id, kind, content, sha256 FROM invoice_document WHERE id = ?";
    static final String SEAL_DOCUMENT = "UPDATE invoice_document SET seal = ? WHERE id = ?";
    static final String BUSINESS_IDS = "SELECT id FROM business_document ORDER BY id";
    static final String BUSINESS = "SELECT content, sha256 FROM business_document WHERE id = ?";
    static final String SEAL_BUSINESS = "UPDATE business_document SET seal = ? WHERE id = ?";
    static final String INVOICE_IDS = "SELECT id FROM invoice WHERE status <> 'DRAFT' ORDER BY id";
    static final String INVOICE = "SELECT number, snapshot FROM invoice WHERE id = ?";
    static final String SEAL_INVOICE = "UPDATE invoice SET seal = ? WHERE id = ?";

    private final Seal seal;

    /**
     * Creates the migration.
     *
     * @param seal computes the seals
     */
    public SealMigration(Seal seal) {
        this.seal = seal;
    }

    @Override
    public MigrationVersion getVersion() {
        return MigrationVersion.fromVersion("29");
    }

    @Override
    public String getDescription() {
        return "seal documents";
    }

    @Override
    public Integer getChecksum() {
        return null;
    }

    @Override
    public boolean canExecuteInTransaction() {
        return true;
    }

    @Override
    public void migrate(Context context) {
        migrate(new JdbcTemplate(new SingleConnectionDataSource(context.getConnection(), true)));
    }

    /**
     * Writes the seals.
     *
     * @param jdbc the connection of the migration
     * @throws IllegalStateException if a file does not match its SHA-256
     */
    void migrate(JdbcTemplate jdbc) {
        DISABLE.forEach(jdbc::execute);
        for (Long id : jdbc.queryForList(DOCUMENT_IDS, Long.class)) {
            jdbc.query(DOCUMENT, rs -> {
                String sha256 = checked("invoice_document", id, rs.getBytes(3), rs.getString(4));
                jdbc.update(SEAL_DOCUMENT, seal.invoiceDocument(rs.getLong(1), rs.getString(2), sha256), id);
            }, id);
        }
        for (Long id : jdbc.queryForList(BUSINESS_IDS, Long.class)) {
            jdbc.query(BUSINESS, rs -> {
                String sha256 = checked("business_document", id, rs.getBytes(1), rs.getString(2));
                jdbc.update(SEAL_BUSINESS, seal.businessDocument(id, sha256), id);
            }, id);
        }
        for (Long id : jdbc.queryForList(INVOICE_IDS, Long.class)) {
            jdbc.query(INVOICE, (RowCallbackHandler) rs -> jdbc.update(SEAL_INVOICE,
                    seal.snapshot(id, rs.getString(1), rs.getString(2)), id), id);
        }
        ENABLE.forEach(jdbc::execute);
    }

    private static String checked(String table, long id, byte[] content, String sha256) {
        if (!Seal.sha256(content).equals(sha256)) {
            throw new IllegalStateException(table + " " + id + " does not match its SHA-256; restore it from a backup "
                    + "before the update");
        }
        return sha256;
    }
}
