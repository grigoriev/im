package io.github.grigoriev.im.invoice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.grigoriev.im.audit.AuditAction;
import io.github.grigoriev.im.audit.AuditService;
import io.github.grigoriev.im.einvoice.InvoiceFormat;
import io.github.grigoriev.im.einvoice.SampleInvoices;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.core.simple.JdbcClient.MappedQuerySpec;
import org.springframework.jdbc.core.simple.JdbcClient.StatementSpec;

/** The sample invoice "reading" has a total of 480,00 €. */
class PaymentServiceTest {

    /** 23:30 in Berlin on March 18, already the 19th: today comes from the zone of the clock. */
    private static final Instant NOW = Instant.parse("2027-03-18T23:30:00Z");
    private static final LocalDate TODAY = LocalDate.of(2027, 3, 19);
    private static final LocalDate ISSUED_ON = LocalDate.of(2027, 3, 10);

    private final InvoiceService invoices = mock(InvoiceService.class);
    private final PaymentRepository repository = mock(PaymentRepository.class);
    private final DispatchRepository dispatches = mock(DispatchRepository.class);
    private final DocumentStore documents = mock(DocumentStore.class);
    private final JdbcClient jdbc = mock(JdbcClient.class);
    private final StatementSpec lock = mock(StatementSpec.class);
    @SuppressWarnings("unchecked")
    private final MappedQuerySpec<Long> locked = mock(MappedQuerySpec.class);
    private final StatementSpec status = mock(StatementSpec.class);
    private final AuditService audit = mock(AuditService.class);
    private final PaymentService service = new PaymentService(invoices, repository, dispatches, documents, jdbc, audit,
            Clock.fixed(NOW, ZoneId.of("Europe/Berlin")));
    private final List<Payment> stored = new ArrayList<>();

    private static Invoice invoice(InvoiceStatus status) {
        return new Invoice(5L, status, 2L, status == InvoiceStatus.DRAFT ? null : "2027-01",
                InvoiceFormat.ZUGFERD_EN16931, ISSUED_ON, ISSUED_ON, ISSUED_ON, 14, null, null, null, null, List.of(), NOW,
                NOW, 3L, status == InvoiceStatus.DRAFT ? null : NOW, status == InvoiceStatus.DRAFT ? null : "{}");
    }

    private static PaymentForm form(LocalDate paidOn, String amount, String note) {
        PaymentForm form = new PaymentForm();
        form.setPaidOn(paidOn);
        form.setAmount(amount);
        form.setNote(note);
        return form;
    }

    private void paid(String... amounts) {
        for (String amount : amounts) {
            stored.add(new Payment(1L, 5L, ISSUED_ON, new BigDecimal(amount), null, "elena", NOW));
        }
    }

    private void is(InvoiceStatus state) {
        when(invoices.find(5)).thenReturn(Optional.of(invoice(state)));
    }

    @BeforeEach
    void issued() {
        is(InvoiceStatus.ISSUED);
        when(documents.restore("{}")).thenReturn(SampleInvoices.reading(InvoiceFormat.ZUGFERD_EN16931));
        when(repository.findByInvoiceIdOrderByPaidOnAscIdAsc(5)).thenAnswer(call -> List.copyOf(stored));
        when(jdbc.sql(PaymentService.LOCK)).thenReturn(lock);
        when(lock.param(anyString(), any())).thenReturn(lock);
        when(lock.query(Long.class)).thenReturn(locked);
        when(jdbc.sql(PaymentService.STATUS)).thenReturn(status);
        when(status.param(anyString(), any())).thenReturn(status);
    }

    @Test
    void aFullPaymentMakesTheInvoicePaid() {
        assertThat(service.add(5, form(TODAY, "480,00", " Überweisung "))).isEqualTo(InvoiceStatus.PAID);
        verify(lock).param("id", 5L);
        verify(locked).single();
        verify(repository).save(new Payment(null, 5L, TODAY, new BigDecimal("480.00"), "Überweisung", "system", NOW));
        verify(status).param("status", "PAID");
        verify(status).param("at", Timestamp.from(NOW));
        verify(status).param("id", 5L);
        verify(status).update();
        verify(audit).add(5L, AuditAction.PAYMENT_RECORDED, "2027-01, 19.03.2027, 480,00 €, Notiz: Überweisung");
    }

    @ParameterizedTest
    @CsvSource({
        "ISSUED, false, 400, ISSUED",
        "SENT, true, 400, SENT",
        "ISSUED, false, '1.000,00', PAID",
        "PAID, true, -80, SENT",
        "PAID, false, -0.01, ISSUED"})
    void theStatusFollowsTheSum(InvoiceStatus before, boolean sent, String amount, InvoiceStatus after) {
        is(before);
        if (before == InvoiceStatus.PAID) {
            paid("480.00");
        }
        when(dispatches.existsByInvoiceId(5)).thenReturn(sent);
        assertThat(service.add(5, form(TODAY, amount, null))).isEqualTo(after);
        verify(status).param("status", after.name());
    }

    @Test
    void partialPaymentsAddUp() {
        paid("200.00");
        assertThat(service.add(5, form(ISSUED_ON, "279,99", null))).isEqualTo(InvoiceStatus.ISSUED);
        paid("279.99");
        assertThat(service.add(5, form(ISSUED_ON, "0,01", null))).isEqualTo(InvoiceStatus.PAID);
        verify(audit).add(5L, AuditAction.PAYMENT_RECORDED, "2027-01, 10.03.2027, 0,01 €");
    }

    @Test
    void aCancelledInvoiceTakesRefundsOnly() {
        is(InvoiceStatus.CANCELLED);
        paid("480.00");
        assertThat(service.add(5, form(TODAY, "-480,00", "Rückzahlung"))).isEqualTo(InvoiceStatus.CANCELLED);
        verify(status, never()).update();
        assertThatThrownBy(() -> service.add(5, form(TODAY, "10", null)))
                .hasMessage("Eine stornierte Rechnung bekommt nur noch Rückzahlungen, mit Minus.");
    }

    @Test
    void aRefundNeverExceedsThePayments() {
        paid("100.00");
        assertThatThrownBy(() -> service.add(5, form(TODAY, "-100,01", null)))
                .hasMessage("Die Rückzahlung ist höher als die Zahlungen bisher (100,00 €).");
        assertThat(service.add(5, form(TODAY, "-100", null))).isEqualTo(InvoiceStatus.ISSUED);
    }

    @Test
    void refusesWrongValues() {
        InvoiceException e = catchThrowableOfType(InvoiceException.class,
                () -> service.add(5, form(null, " ", "n".repeat(501))));
        assertThat(e.problems()).containsExactly("Bitte den Tag des Zahlungseingangs angeben.", "Bitte den Betrag angeben.",
                "Die Notiz hat höchstens 500 Zeichen.");
        assertThat(problems(form(TODAY.plusDays(1), "1.500", "n".repeat(500))))
                .containsExactly("Der Zahlungseingang liegt in der Zukunft.",
                        "Der Betrag ist mehrdeutig: bitte mit Komma schreiben, zum Beispiel 1.500,00.");
        assertThat(problems(form(ISSUED_ON.minusDays(1), "viel", null)))
                .containsExactly("Der Zahlungseingang liegt vor dem Rechnungsdatum.",
                        "Der Betrag ist keine Zahl, zum Beispiel 400,00 oder -50,00.");
        assertThat(problems(form(TODAY, "0,00", null))).containsExactly("Der Betrag ist 0.");
        assertThat(problems(form(TODAY, "12345678901", null)))
                .containsExactly("Der Betrag hat höchstens 10 Stellen vor dem Komma.");
        assertThat(problems(form(TODAY, "1,001", null))).containsExactly("Der Betrag hat höchstens 2 Nachkommastellen.");
        assertThat(problems(form(TODAY, null, null))).containsExactly("Bitte den Betrag angeben.");
        verify(repository, never()).save(any());
    }

    @Test
    void keepsTheLargestAmountAndTrailingZeros() {
        service.add(5, form(TODAY, "1234567890,100", null));
        verify(repository).save(new Payment(null, 5L, TODAY, new BigDecimal("1234567890.10"), null, "system", NOW));
    }

    private List<String> problems(PaymentForm form) {
        return catchThrowableOfType(InvoiceException.class, () -> service.add(5, form)).problems();
    }

    @Test
    void refusesADraftAndAnUnknownInvoice() {
        PaymentForm form = form(TODAY, "10", null);
        is(InvoiceStatus.DRAFT);
        assertThatThrownBy(() -> service.add(5, form)).hasMessage("Erst ausstellen, dann Zahlungen erfassen.");
        when(invoices.find(6)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.add(6, form)).hasMessage("Diese Rechnung gibt es nicht.");
        verify(repository, never()).save(any());
    }

    @Test
    void summarizesThePayments() {
        assertThat(service.summary(invoice(InvoiceStatus.ISSUED)))
                .isEqualTo(new PaymentService.Payments(List.of(), BigDecimal.ZERO, new BigDecimal("480.00")));
        paid("300.00", "200.00");
        PaymentService.Payments payments = service.summary(invoice(InvoiceStatus.ISSUED));
        assertThat(payments.entries()).hasSize(2);
        assertThat(payments.paid()).isEqualByComparingTo("500");
        assertThat(payments.open()).isEqualByComparingTo("-20");
        assertThat(service.today()).isEqualTo(TODAY);
        assertThat(PaymentService.euros(new BigDecimal("-1234.5"))).isEqualTo("-1.234,50 €");
    }
}
