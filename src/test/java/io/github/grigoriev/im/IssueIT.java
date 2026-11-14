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
import io.github.grigoriev.im.invoice.PaymentForm;
import io.github.grigoriev.im.invoice.PaymentService;
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

    @Autowired
    private PaymentService payments;

    @Autowired
    private io.github.grigoriev.im.invoice.ArchiveService archive;

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

        java.io.ByteArrayOutputStream zip = new java.io.ByteArrayOutputStream();
        archive.write(2043, zip);
        java.util.Map<String, byte[]> entries = new java.util.LinkedHashMap<>();
        try (java.util.zip.ZipInputStream in = new java.util.zip.ZipInputStream(
                new java.io.ByteArrayInputStream(zip.toByteArray()))) {
            for (var entry = in.getNextEntry(); entry != null; entry = in.getNextEntry()) {
                entries.put(entry.getName(), in.readAllBytes());
            }
        }
        assertThat(entries.keySet()).containsExactly("Rechnung_2043-01.pdf", "Rechnung_2043-01.xml", "Inhalt.csv");
        assertThat(entries.get("Rechnung_2043-01.pdf")).as("the archive holds the stored PDF").isEqualTo(files.get(0).content());
        assertThat(new String(entries.get("Inhalt.csv"), StandardCharsets.UTF_8))
                .contains("2043-01;18.03.2043;\"Verlag am Fluss GmbH\";Rechnung_2043-01.pdf;" + files.get(0).size() + ";"
                        + files.get(0).sha256());

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
        form.setRecipient("r".repeat(200));
        form.setNote("n".repeat(499) + "!");
        dispatches.recordManual(id, form);
        assertThat(invoices.find(id).orElseThrow().status()).isEqualTo(InvoiceStatus.SENT);
        assertThat(dispatches.list(id)).extracting(d -> d.channel())
                .containsExactly(DispatchChannel.POST, DispatchChannel.OWN_MAIL);
        assertThat(audit.history(id)).extracting(AuditEvent::action).containsExactly(AuditAction.DRAFT_CREATED,
                AuditAction.INVOICE_ISSUED, AuditAction.INVOICE_SENT, AuditAction.INVOICE_SENT);
        assertThat(audit.history(id).get(2).detail()).isEqualTo("2019-01, 02.04.2019, Post, Buchhaltung, Notiz: mit Begleitbrief");
        assertThat(audit.history(id).get(3).detail()).as("the longest recipient and note stay whole")
                .contains("r".repeat(200)).endsWith("n".repeat(499) + "!");

        String longest = "r".repeat(240) + "@kunde.example";
        assertThat(longest).hasSize(254);
        assertThat(dispatches.recordEmail(id, longest, "PDF").recipient()).as("the longest address fits").isEqualTo(longest);
        assertThatThrownBy(() -> jdbc.update("UPDATE dispatch SET note = 'neu' WHERE id = ?", first))
                .hasMessageContaining("immutable");
        assertThatThrownBy(() -> jdbc.update("DELETE FROM dispatch WHERE id = ?", first)).hasMessageContaining("immutable");
    }

    private static PaymentForm payment(LocalDate paidOn, String amount) {
        PaymentForm form = new PaymentForm();
        form.setPaidOn(paidOn);
        form.setAmount(amount);
        return form;
    }

    @Test
    void paymentsMoveTheStatusAndNeverChange() {
        Invoice draft = draft(verlag, LocalDate.of(2018, 5, 2));
        long id = draft.id();
        issues.issue(id, draft.version());
        LocalDate day = LocalDate.of(2018, 5, 20);
        assertThat(payments.add(id, payment(day, "150,00"))).isEqualTo(InvoiceStatus.ISSUED);
        assertThat(payments.add(id, payment(day, "250,00"))).isEqualTo(InvoiceStatus.PAID);
        assertThat(invoices.find(id).orElseThrow().status()).isEqualTo(InvoiceStatus.PAID);
        assertThat(payments.add(id, payment(day, "-50,00"))).as("a refund reopens it").isEqualTo(InvoiceStatus.ISSUED);
        assertThat(invoices.find(id).orElseThrow().status()).isEqualTo(InvoiceStatus.ISSUED);
        PaymentService.Payments summary = payments.summary(invoices.find(id).orElseThrow());
        assertThat(summary.entries()).hasSize(3);
        assertThat(summary.paid()).isEqualByComparingTo("350.00");
        assertThat(summary.open()).isEqualByComparingTo("50.00");
        assertThat(audit.history(id)).extracting(AuditEvent::action).containsSubsequence(AuditAction.INVOICE_ISSUED,
                AuditAction.PAYMENT_RECORDED, AuditAction.PAYMENT_RECORDED, AuditAction.PAYMENT_RECORDED);

        long first = summary.entries().getFirst().id();
        assertThatThrownBy(() -> jdbc.update("UPDATE payment SET amount = 1 WHERE id = ?", first))
                .hasMessageContaining("immutable");
        assertThatThrownBy(() -> jdbc.update("DELETE FROM payment WHERE id = ?", first)).hasMessageContaining("immutable");
        assertThatThrownBy(() -> jdbc.update("INSERT INTO payment (invoice_id, paid_on, amount, username, created_at) "
                + "VALUES (?, '2018-05-21', 0, 'x', now())", id)).hasMessageContaining("check");
    }

    @Test
    void aStornorechnungCancelsItsInvoiceInTheSameTransaction() {
        Invoice paid = draft(verlag, LocalDate.of(2017, 3, 1));
        long id = paid.id();
        issues.issue(id, paid.version());
        payments.add(id, payment(LocalDate.of(2017, 3, 10), "400,00"));
        assertThat(invoices.find(id).orElseThrow().status()).isEqualTo(InvoiceStatus.PAID);

        Invoice storno = invoices.createCancellation(id);
        assertThatThrownBy(() -> invoices.createCancellation(id)).as("one Stornorechnung per invoice")
                .hasMessage("Zu dieser Rechnung gibt es schon eine Stornorechnung.");
        assertThatThrownBy(() -> invoices.createCancellation(storno.id())).hasMessageContaining("Stornieren lässt sich nur");
        assertThat(invoices.cancellation(invoices.find(id).orElseThrow())).map(Invoice::id).contains(storno.id());

        DraftForm form = DraftForm.of(storno);
        form.setIssueDate(LocalDate.of(2017, 4, 3));
        form.getLines().getFirst().setAmount("1");
        Invoice saved = invoices.saveDraft(storno.id(), form);
        assertThat(saved.lines()).as("the lines stay those of the invoice").isEqualTo(paid.lines());

        String number = issues.issue(saved.id(), saved.version());
        assertThat(number).isEqualTo("2017-02");
        assertThat(invoices.find(id).orElseThrow().status()).isEqualTo(InvoiceStatus.CANCELLED);
        InvoiceDocument snapshot = documents.restore(invoices.find(storno.id()).orElseThrow().snapshot());
        assertThat(snapshot.cancels()).isEqualTo(new io.github.grigoriev.im.einvoice.InvoiceReference("2017-01",
                LocalDate.of(2017, 3, 1)));
        assertThat(snapshot.total()).isEqualByComparingTo("400.00");
        assertThat(new String(documents.find(storno.id(), DocumentKind.XML).orElseThrow().content(),
                StandardCharsets.UTF_8)).contains("<ram:TypeCode>381</ram:TypeCode>");
        assertThat(audit.history(id)).extracting(AuditEvent::action).endsWith(AuditAction.INVOICE_CANCELLED);

        assertThatThrownBy(() -> payments.add(storno.id(), payment(LocalDate.of(2017, 4, 5), "-400,00")))
                .hasMessage("Eine Stornorechnung bekommt keine Zahlungen. Erstattungen gehören zur stornierten Rechnung.");
        assertThat(payments.add(id, payment(LocalDate.of(2017, 4, 5), "-400,00"))).as("the refund on the invoice")
                .isEqualTo(InvoiceStatus.CANCELLED);
        assertThatThrownBy(() -> jdbc.update("UPDATE invoice SET cancels_id = NULL WHERE id = ?", storno.id()))
                .hasMessageContaining("immutable");
    }

    @Test
    void aDeletedStornoDraftFreesTheInvoice() {
        Invoice issued = draft(verlag, LocalDate.of(2016, 5, 1));
        issues.issue(issued.id(), issued.version());
        Invoice storno = invoices.createCancellation(issued.id());
        invoices.deleteDraft(storno.id());
        assertThat(invoices.cancellation(invoices.find(issued.id()).orElseThrow())).isEmpty();
        assertThat(invoices.createCancellation(issued.id()).id()).isNotEqualTo(storno.id());
    }

    @Test
    void anInvoiceCancelledInTheMeantimeKeepsTheStornoADraft() {
        Invoice issued = draft(verlag, LocalDate.of(2015, 6, 1));
        issues.issue(issued.id(), issued.version());
        Invoice storno = invoices.createCancellation(issued.id());
        Invoice complete = invoices.find(storno.id()).orElseThrow();
        jdbc.update("UPDATE invoice SET status = 'CANCELLED', version = version + 1 WHERE id = ?", issued.id());
        assertThatThrownBy(() -> issues.issue(complete.id(), complete.version()))
                .hasMessage("Die Rechnung 2015-01 ist schon storniert. Die Stornorechnung bleibt ein Entwurf.");
        assertThat(invoices.find(storno.id()).orElseThrow().isDraft()).isTrue();
        assertThat(jdbc.queryForObject("SELECT next_number FROM number_sequence WHERE year = 2015", Integer.class))
                .as("no number lost").isEqualTo(2);
    }
}
