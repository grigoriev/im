package io.github.grigoriev.im.invoice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.grigoriev.im.audit.AuditAction;
import io.github.grigoriev.im.audit.AuditService;
import io.github.grigoriev.im.einvoice.InvoiceFormat;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.core.simple.JdbcClient.StatementSpec;

class ReminderServiceTest {

    /** 23:30 in Berlin on April 4, already the 5th: today comes from the zone of the clock. */
    private static final Instant NOW = Instant.parse("2027-04-04T23:30:00Z");
    private static final LocalDate TODAY = LocalDate.of(2027, 4, 5);
    private static final LocalDate ISSUED_ON = LocalDate.of(2027, 3, 10);

    private final InvoiceService invoices = mock(InvoiceService.class);
    private final PaymentService payments = mock(PaymentService.class);
    private final ReminderRepository repository = mock(ReminderRepository.class);
    private final JdbcClient jdbc = mock(JdbcClient.class);
    private final AuditService audit = mock(AuditService.class);
    private final ReminderService service = new ReminderService(invoices, payments, repository, jdbc, audit,
            Clock.fixed(NOW, ZoneId.of("Europe/Berlin")));

    private static Invoice invoice(InvoiceStatus status, int paymentDays) {
        return new Invoice(5L, status, 2L, status == InvoiceStatus.DRAFT ? null : "2027-01",
                InvoiceFormat.ZUGFERD_EN16931, ISSUED_ON, ISSUED_ON, ISSUED_ON, paymentDays, null, null, null, null,
                List.of(), NOW, NOW, 3L, status == InvoiceStatus.DRAFT ? null : NOW,
                status == InvoiceStatus.DRAFT ? null : "{}", null);
    }

    private void open(String amount) {
        when(payments.summary(any())).thenReturn(new PaymentService.Payments(List.of(), BigDecimal.ZERO,
                new BigDecimal(amount)));
    }

    private static ReminderForm form(LocalDate remindedOn, String note) {
        ReminderForm form = new ReminderForm();
        form.setRemindedOn(remindedOn);
        form.setNote(note);
        return form;
    }

    @BeforeEach
    void sent() {
        when(invoices.find(5)).thenReturn(Optional.of(invoice(InvoiceStatus.SENT, 14)));
        when(invoices.cancelled(any())).thenReturn(Optional.empty());
        open("480.00");
        when(repository.save(any())).thenAnswer(call -> call.getArgument(0));
    }

    @Test
    void recordsAReminderByHandWithItsNote() {
        Reminder saved = service.recordManual(5, form(TODAY, "  telefonisch erinnert "));
        assertThat(saved).isEqualTo(new Reminder(null, 5L, TODAY, ReminderChannel.MANUAL, null, "telefonisch erinnert",
                "system", NOW));
        verify(audit).add(5L, AuditAction.REMINDER_RECORDED, "2027-01, 05.04.2027, Von Hand vermerkt, "
                + "Notiz: telefonisch erinnert");
        assertThat(service.recordManual(5, form(ISSUED_ON, " ")).note()).as("the day of the invoice, no note").isNull();
        verify(audit).add(5L, AuditAction.REMINDER_RECORDED, "2027-01, 10.03.2027, Von Hand vermerkt");
    }

    @Test
    void recordsAReminderTheAppSent() {
        Reminder saved = service.recordEmail(5, "a@b.example");
        assertThat(saved).isEqualTo(new Reminder(null, 5L, TODAY, ReminderChannel.EMAIL_APP, "a@b.example", null,
                "system", NOW));
        verify(audit).add(5L, AuditAction.REMINDER_RECORDED, "2027-01, 05.04.2027, E-Mail aus der App, a@b.example");
        open("0.00");
        assertThat(service.recordEmail(5, "a@b.example").channel())
                .as("a payment during the send keeps the sent e-mail in the history").isEqualTo(ReminderChannel.EMAIL_APP);
    }

    @Test
    void refusesAWrongDayOrNote() {
        assertThat(problems(form(null, null))).containsExactly("Bitte das Datum der Erinnerung angeben.");
        assertThat(problems(form(TODAY.plusDays(1), null))).containsExactly("Das Datum der Erinnerung liegt in der Zukunft.");
        assertThat(problems(form(ISSUED_ON.minusDays(1), "n".repeat(501)))).containsExactly(
                "Das Datum der Erinnerung liegt vor dem Rechnungsdatum.", "Die Notiz hat höchstens 500 Zeichen.");
        verify(repository, never()).save(any());
    }

    private List<String> problems(ReminderForm form) {
        try {
            service.recordManual(5, form);
            return List.of();
        } catch (InvoiceException e) {
            return e.problems();
        }
    }

    @Test
    void remindsOnlyOfAnIssuedInvoiceWithMoneyOpen() {
        assertThat(service.remindable(5).id()).isEqualTo(5L);
        assertThat(service.isRemindable(5)).isTrue();
        open("0.00");
        assertThatThrownBy(() -> service.remindable(5)).hasMessage(ReminderService.NOTHING_OPEN);
        assertThat(service.isRemindable(5)).isFalse();
        open("-10.00");
        assertThatThrownBy(() -> service.remindable(5)).as("overpaid").hasMessage(ReminderService.NOTHING_OPEN);
        open("0.01");
        assertThat(service.isRemindable(5)).isTrue();
        when(invoices.cancelled(any())).thenReturn(Optional.of(invoice(InvoiceStatus.CANCELLED, 14)));
        assertThatThrownBy(() -> service.remindable(5)).hasMessage(ReminderService.STORNO);
        when(invoices.find(5)).thenReturn(Optional.of(invoice(InvoiceStatus.CANCELLED, 14)));
        assertThatThrownBy(() -> service.remindable(5)).hasMessage(ReminderService.CANCELLED);
        when(invoices.find(5)).thenReturn(Optional.of(invoice(InvoiceStatus.DRAFT, 14)));
        assertThatThrownBy(() -> service.remindable(5)).hasMessage(ReminderService.NOT_ISSUED);
        when(invoices.find(5)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.remindable(5)).hasMessage(InvoiceService.UNKNOWN);
        assertThatThrownBy(() -> service.recordEmail(5, "a@b.example")).hasMessage(InvoiceService.UNKNOWN);
        verify(repository, never()).save(any());
    }

    @Test
    void knowsTheOpenAmountTheDueDateAndToday() {
        assertThat(service.open(invoice(InvoiceStatus.SENT, 14))).isEqualByComparingTo("480.00");
        assertThat(service.today()).isEqualTo(TODAY);
        assertThat(service.overdue(invoice(InvoiceStatus.SENT, 25))).as("due on April 4").isTrue();
        assertThat(service.overdue(invoice(InvoiceStatus.SENT, 26))).as("due today").isFalse();
    }

    @Test
    void listsTheRemindersAndTheLastOfEachInvoice() throws Exception {
        List<Reminder> list = List.of(new Reminder(1L, 5L, TODAY, ReminderChannel.MANUAL, null, null, "elena", NOW));
        when(repository.findByInvoiceIdOrderByRemindedOnAscIdAsc(5)).thenReturn(list);
        assertThat(service.list(5)).isEqualTo(list);
        StatementSpec statement = mock(StatementSpec.class);
        when(jdbc.sql(ReminderService.LAST)).thenReturn(statement);
        ResultSet rs = mock(ResultSet.class);
        when(rs.getLong(1)).thenReturn(5L);
        when(rs.getObject(2, LocalDate.class)).thenReturn(TODAY);
        doAnswer(call -> {
            call.<RowCallbackHandler>getArgument(0).processRow(rs);
            return null;
        }).when(statement).query(any(RowCallbackHandler.class));
        assertThat(service.last()).containsExactly(Map.entry(5L, TODAY));
    }
}
