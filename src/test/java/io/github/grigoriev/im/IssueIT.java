package io.github.grigoriev.im;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

import io.github.grigoriev.im.audit.AuditAction;
import io.github.grigoriev.im.audit.AuditEvent;
import io.github.grigoriev.im.audit.AuditService;
import io.github.grigoriev.im.customer.Customer;
import io.github.grigoriev.im.customer.CustomerService;
import io.github.grigoriev.im.customer.CustomerTestData;
import io.github.grigoriev.im.einvoice.EInvoiceException;
import io.github.grigoriev.im.einvoice.EInvoiceService;
import io.github.grigoriev.im.einvoice.InvoiceDocument;
import io.github.grigoriev.im.einvoice.TaxCategory;
import io.github.grigoriev.im.invoice.DispatchChannel;
import io.github.grigoriev.im.invoice.DispatchForm;
import io.github.grigoriev.im.invoice.DispatchService;
import io.github.grigoriev.im.invoice.DocumentKind;
import io.github.grigoriev.im.invoice.DocumentStore;
import io.github.grigoriev.im.invoice.DraftForm;
import io.github.grigoriev.im.invoice.Invoice;
import io.github.grigoriev.im.invoice.InvoiceException;
import io.github.grigoriev.im.invoice.InvoiceService;
import io.github.grigoriev.im.invoice.InvoiceStatus;
import io.github.grigoriev.im.invoice.IssueService;
import io.github.grigoriev.im.invoice.LineForm;
import io.github.grigoriev.im.invoice.StoredDocument;
import io.github.grigoriev.im.numbering.NumberService;
import io.github.grigoriev.im.seller.SellerProfile;
import io.github.grigoriev.im.seller.SellerService;
import io.github.grigoriev.im.seller.SellerTestData;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/**
 * The issue against PostgreSQL with the real validators: the number, the snapshot, the documents, the rollback without
 * a gap and the immutability rules of the database. Each test uses its own year. The dispatch test uses a past year:
 * a dispatch is never in the future.
 */
@SpringBootTest
@Import(TestDatabase.class)
class IssueIT {

    @Autowired
    private IssueService issues;

    @Autowired
    private InvoiceService invoices;

    @Autowired
    private DocumentStore documents;

    @Autowired
    private CustomerService customers;

    @Autowired
    private SellerService sellers;

    @Autowired
    private NumberService numbers;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private AuditService audit;

    @Autowired
    private DispatchService dispatches;

    @MockitoSpyBean
    private EInvoiceService einvoices;

    private Customer verlag;

    @BeforeEach
    void profileAndCustomer() {
        sellers.save(SellerTestData.profile());
        verlag = customers.create(CustomerTestData.verlag());
        numbers.savePattern("{YYYY}-{NN}");
    }

    /** A complete draft of a customer, dated in the given year. */
    private Invoice draft(Customer customer, LocalDate date) {
        Invoice draft = invoices.createDraft(customer.id(), null);
        DraftForm form = DraftForm.of(draft);
        form.setIssueDate(date);
        form.setServiceFrom(date);
        form.setServiceTo(date);
        form.setBuyerReference(customer.buyerReference());
        LineForm line = form.getLines().getFirst();
        line.setText("Lesung am {Leistungszeitraum}");
        line.setLumpSum(true);
        line.setAmount("400");
        line.setCategory(TaxCategory.KLEINUNTERNEHMER);
        return invoices.saveDraft(draft.id(), form);
    }

    private static String sha256(byte[] content) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
    }

    @Test
    void issuesWithNumberSnapshotAndDocuments() throws Exception {
        Invoice draft = draft(verlag, LocalDate.of(2043, 3, 18));
        assertThat(issues.issue(draft.id(), draft.version())).isEqualTo("2043-01");

        Invoice issued = invoices.find(draft.id()).orElseThrow();
        assertThat(issued.status()).isEqualTo(InvoiceStatus.ISSUED);
        assertThat(issued.number()).isEqualTo("2043-01");
        assertThat(issued.issuedAt()).isNotNull();
        InvoiceDocument snapshot = documents.restore(issued.snapshot());
        assertThat(snapshot.number()).isEqualTo("2043-01");
        assertThat(snapshot.buyer().name()).isEqualTo("Verlag am Fluss GmbH");
        assertThat(snapshot.total()).isEqualByComparingTo("400.00");

        List<StoredDocument> files = documents.list(draft.id());
        assertThat(files).extracting(StoredDocument::kind).containsExactly(DocumentKind.PDF, DocumentKind.XML);
        assertThat(files).extracting(StoredDocument::filename).containsExactly("Rechnung_2043-01.pdf", "Rechnung_2043-01.xml");
        for (StoredDocument file : files) {
            assertThat(file.sha256()).isEqualTo(sha256(file.content()));
            assertThat(file.size()).isEqualTo(file.content().length);
        }
        assertThat(new String(files.get(0).content(), 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
        assertThat(new String(files.get(1).content(), StandardCharsets.UTF_8)).contains("2043-01");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_event WHERE action = 'INVOICE_ISSUED' AND detail LIKE ?",
                Integer.class, "2043-01%")).isEqualTo(1);
        assertThat(audit.history(draft.id())).as("the history of the invoice, oldest first").extracting(AuditEvent::action)
                .containsExactly(AuditAction.DRAFT_CREATED, AuditAction.INVOICE_ISSUED);
        assertThat(audit.history(draft.id() + 1000)).isEmpty();

        SellerProfile original = SellerTestData.profile();
        sellers.save(new SellerProfile("Anderer Name", original.street(), original.postcode(), original.city(),
                original.countryCode(), original.contactName(), original.email(), original.phone(), original.taxNumber(),
                original.vatId(), original.iban(), original.bic(), original.taxStatus(), null, null, null, null));
        assertThat(documents.restore(invoices.find(draft.id()).orElseThrow().snapshot()).seller().name())
                .as("the snapshot keeps the seller of the issue").isEqualTo(original.name());
    }

    @Test
    void anIssuedInvoiceNeverChanges() {
        Invoice draft = draft(verlag, LocalDate.of(2044, 5, 2));
        long id = draft.id();
        Long version = draft.version();
        issues.issue(id, version);
        DraftForm form = DraftForm.of(draft);
        long other = draft(verlag, LocalDate.of(2044, 5, 3)).id();

        assertThatThrownBy(() -> issues.issue(id, version)).isInstanceOf(InvoiceException.class);
        assertThatThrownBy(() -> invoices.saveDraft(id, form)).isInstanceOf(InvoiceException.class);
        assertThatThrownBy(() -> invoices.deleteDraft(id)).isInstanceOf(InvoiceException.class);
        assertThatThrownBy(() -> jdbc.update("UPDATE invoice SET number = '2044-99' WHERE id = ?", id))
                .hasMessageContaining("immutable");
        assertThatThrownBy(() -> jdbc.update("UPDATE invoice SET intro = 'neu' WHERE id = ?", id))
                .hasMessageContaining("immutable");
        assertThatThrownBy(() -> jdbc.update("UPDATE invoice SET status = 'DRAFT', number = NULL, issued_at = NULL, "
                + "snapshot = NULL WHERE id = ?", id)).hasMessageContaining("immutable");
        assertThatThrownBy(() -> jdbc.update("DELETE FROM invoice WHERE id = ?", id)).hasMessageContaining("immutable");
        assertThatThrownBy(() -> jdbc.update("UPDATE invoice_line SET text = 'neu' WHERE invoice_id = ?", id))
                .hasMessageContaining("immutable");
        assertThatThrownBy(() -> jdbc.update("DELETE FROM invoice_line WHERE invoice_id = ?", id))
                .hasMessageContaining("immutable");
        assertThatThrownBy(() -> jdbc.update("UPDATE invoice_line SET invoice_id = ?, position = 99 WHERE invoice_id = ?",
                other, id)).as("a line never leaves its issued invoice").hasMessageContaining("immutable");
        assertThatThrownBy(() -> jdbc.update("INSERT INTO invoice_line (invoice_id, position, text, lump_sum, quantity, "
                + "unit_price, category) SELECT invoice_id, 99, text, lump_sum, quantity, unit_price, category "
                + "FROM invoice_line WHERE invoice_id = ?", id)).hasMessageContaining("immutable");
        assertThatThrownBy(() -> jdbc.update("UPDATE invoice_document SET filename = 'x.pdf' WHERE invoice_id = ?", id))
                .hasMessageContaining("immutable");
        assertThatThrownBy(() -> jdbc.update("DELETE FROM invoice_document WHERE invoice_id = ?", id))
                .hasMessageContaining("immutable");

        assertThat(jdbc.update("UPDATE invoice SET status = 'SENT', version = version + 1 WHERE id = ?", id))
                .as("the status moves on").isEqualTo(1);
        assertThat(invoices.find(id).orElseThrow().number()).isEqualTo("2044-01");
    }

    @Test
    void aFailedIssueLeavesNoGap() {
        Invoice first = draft(verlag, LocalDate.of(2045, 1, 10));
        long id = first.id();
        Long version = first.version();
        doThrow(new EInvoiceException("KoSIT said no")).when(einvoices).issue(any());
        assertThatThrownBy(() -> issues.issue(id, version)).isInstanceOf(InvoiceException.class)
                .hasMessageContaining("Die Rechnung bleibt ein Entwurf.");
        Invoice still = invoices.find(first.id()).orElseThrow();
        assertThat(still.isDraft()).isTrue();
        assertThat(still.number()).isNull();
        assertThat(documents.list(first.id())).isEmpty();

        org.mockito.Mockito.reset(einvoices);
        assertThat(issues.issue(id, version)).as("the number came back").isEqualTo("2045-01");
    }

    @Test
    void aTakenNumberKeepsTheDraft() {
        Invoice first = draft(verlag, LocalDate.of(2046, 2, 1));
        issues.issue(first.id(), first.version());
        jdbc.update("UPDATE number_sequence SET next_number = 1 WHERE year = 2046");
        Invoice second = draft(verlag, LocalDate.of(2046, 2, 2));
        long id = second.id();
        Long version = second.version();
        assertThatThrownBy(() -> issues.issue(id, version)).isInstanceOf(InvoiceException.class)
                .hasMessageStartingWith("Die Rechnungsnummer 2046-01 gibt es schon.");
        assertThat(invoices.find(second.id()).orElseThrow().isDraft()).isTrue();
        assertThat(jdbc.queryForObject("SELECT next_number FROM number_sequence WHERE year = 2046", Integer.class))
                .as("the rollback returns the counter").isEqualTo(1);
    }

    @Test
    void parallelIssuesGetEveryNumberOnce() throws Exception {
        Customer vhs = customers.create(CustomerTestData.vhs());
        LocalDate date = LocalDate.of(2047, 7, 1);
        List<Invoice> drafts = IntStream.range(0, 4).mapToObj(n -> draft(n % 2 == 0 ? verlag : vhs, date)).toList();
        List<Callable<String>> calls = drafts.stream().<Callable<String>>map(d -> () -> issues.issue(d.id(), d.version()))
                .toList();
        List<String> issued = new ArrayList<>();
        try (ExecutorService pool = Executors.newFixedThreadPool(4)) {
            for (Future<String> call : pool.invokeAll(calls)) {
                issued.add(call.get());
            }
        }
        assertThat(issued).containsExactlyInAnyOrder("2047-01", "2047-02", "2047-03", "2047-04");
    }

    @Test
    void aDispatchMovesTheStatusOnceAndNeverChanges() {
        Invoice draft = draft(verlag, LocalDate.of(2019, 4, 1));
        long id = draft.id();
        issues.issue(id, draft.version());
        DispatchForm form = new DispatchForm();
        form.setSentOn(LocalDate.of(2019, 4, 2));
        form.setChannel(DispatchChannel.POST);
        form.setRecipient("Buchhaltung");
        form.setNote("mit Begleitbrief");
        long first = dispatches.recordManual(id, form).id();
        assertThat(invoices.find(id).orElseThrow().status()).isEqualTo(InvoiceStatus.SENT);
        form.setChannel(DispatchChannel.OWN_MAIL);
        dispatches.recordManual(id, form);
        assertThat(invoices.find(id).orElseThrow().status()).isEqualTo(InvoiceStatus.SENT);
        assertThat(dispatches.list(id)).extracting(d -> d.channel())
                .containsExactly(DispatchChannel.POST, DispatchChannel.OWN_MAIL);
        assertThat(audit.history(id)).extracting(AuditEvent::action).containsExactly(AuditAction.DRAFT_CREATED,
                AuditAction.INVOICE_ISSUED, AuditAction.INVOICE_SENT, AuditAction.INVOICE_SENT);
        assertThat(audit.history(id).get(2).detail()).isEqualTo("2019-01, 02.04.2019, Post, Buchhaltung, Notiz: mit Begleitbrief");

        assertThatThrownBy(() -> jdbc.update("UPDATE dispatch SET note = 'neu' WHERE id = ?", first))
                .hasMessageContaining("immutable");
        assertThatThrownBy(() -> jdbc.update("DELETE FROM dispatch WHERE id = ?", first)).hasMessageContaining("immutable");
    }
}
