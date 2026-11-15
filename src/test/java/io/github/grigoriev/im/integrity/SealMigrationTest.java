package io.github.grigoriev.im.integrity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;
import org.flywaydb.core.api.MigrationVersion;
import org.flywaydb.core.api.migration.Context;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;

class SealMigrationTest {

    private static final byte[] PDF = "%PDF".getBytes(StandardCharsets.US_ASCII);

    private final Seal seal = new Seal("test-only-integrity-key-0123456789abcdef");
    private final SealMigration migration = new SealMigration(seal);
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);

    @Test
    void isMigration29InItsTransaction() {
        assertThat(migration.getVersion()).isEqualTo(MigrationVersion.fromVersion("29"));
        assertThat(migration.getDescription()).isEqualTo("seal documents");
        assertThat(migration.getChecksum()).isNull();
        assertThat(migration.canExecuteInTransaction()).isTrue();
    }

    private void row(String sql, long id, ResultSet rs) {
        doAnswer(call -> {
            call.<RowCallbackHandler>getArgument(1).processRow(rs);
            return null;
        }).when(jdbc).query(eq(sql), any(RowCallbackHandler.class), eq(id));
    }

    @Test
    void sealsEveryDocumentAndIssuedInvoiceWithTheTriggersOff() throws Exception {
        when(jdbc.queryForList(SealMigration.DOCUMENT_IDS, Long.class)).thenReturn(List.of(3L));
        when(jdbc.queryForList(SealMigration.BUSINESS_IDS, Long.class)).thenReturn(List.of(4L));
        when(jdbc.queryForList(SealMigration.INVOICE_IDS, Long.class)).thenReturn(List.of(5L));
        ResultSet document = mock(ResultSet.class);
        when(document.getLong(1)).thenReturn(5L);
        when(document.getString(2)).thenReturn("PDF");
        when(document.getBytes(3)).thenReturn(PDF);
        when(document.getString(4)).thenReturn(Seal.sha256(PDF));
        row(SealMigration.DOCUMENT, 3L, document);
        ResultSet business = mock(ResultSet.class);
        when(business.getBytes(1)).thenReturn(PDF);
        when(business.getString(2)).thenReturn(Seal.sha256(PDF));
        row(SealMigration.BUSINESS, 4L, business);
        ResultSet invoice = mock(ResultSet.class);
        when(invoice.getString(1)).thenReturn("2027-01");
        when(invoice.getString(2)).thenReturn("{}");
        row(SealMigration.INVOICE, 5L, invoice);

        migration.migrate(jdbc);

        InOrder order = inOrder(jdbc);
        order.verify(jdbc).execute("ALTER TABLE invoice_document DISABLE TRIGGER invoice_document_frozen");
        order.verify(jdbc).execute("ALTER TABLE business_document DISABLE TRIGGER business_document_frozen");
        order.verify(jdbc).execute("ALTER TABLE invoice DISABLE TRIGGER invoice_frozen");
        order.verify(jdbc).update(SealMigration.SEAL_DOCUMENT, seal.invoiceDocument(5, "PDF", Seal.sha256(PDF)), 3L);
        order.verify(jdbc).update(SealMigration.SEAL_BUSINESS, seal.businessDocument(4, Seal.sha256(PDF)), 4L);
        order.verify(jdbc).update(SealMigration.SEAL_INVOICE, seal.snapshot(5, "2027-01", "{}"), 5L);
        order.verify(jdbc).execute("ALTER TABLE invoice_document ENABLE TRIGGER invoice_document_frozen");
        order.verify(jdbc).execute("ALTER TABLE business_document ENABLE TRIGGER business_document_frozen");
        order.verify(jdbc).execute("ALTER TABLE invoice ENABLE TRIGGER invoice_frozen");
    }

    @Test
    void refusesToSealAFileThatNoLongerMatchesItsHash() throws Exception {
        when(jdbc.queryForList(SealMigration.DOCUMENT_IDS, Long.class)).thenReturn(List.of());
        when(jdbc.queryForList(SealMigration.BUSINESS_IDS, Long.class)).thenReturn(List.of(4L));
        ResultSet business = mock(ResultSet.class);
        when(business.getBytes(1)).thenReturn(PDF);
        when(business.getString(2)).thenReturn("0".repeat(64));
        row(SealMigration.BUSINESS, 4L, business);
        assertThatThrownBy(() -> migration.migrate(jdbc)).isInstanceOf(IllegalStateException.class)
                .hasMessage("business_document 4 does not match its SHA-256; restore it from a backup before the "
                        + "update");
        verify(jdbc, never()).update(eq(SealMigration.SEAL_BUSINESS), any(), any());
    }

    @Test
    void runsOnTheConnectionOfFlyway() throws Exception {
        Connection connection = mock(Connection.class);
        Statement statement = mock(Statement.class);
        ResultSet empty = mock(ResultSet.class);
        when(connection.createStatement()).thenReturn(statement);
        when(statement.executeQuery(anyString())).thenReturn(empty);
        Context context = mock(Context.class);
        when(context.getConnection()).thenReturn(connection);
        migration.migrate(context);
        verify(statement).execute("ALTER TABLE invoice DISABLE TRIGGER invoice_frozen");
        verify(statement).execute("ALTER TABLE invoice ENABLE TRIGGER invoice_frozen");
        verify(connection, never()).close();
    }
}
