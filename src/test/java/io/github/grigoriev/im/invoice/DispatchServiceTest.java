package io.github.grigoriev.im.invoice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.github.grigoriev.im.audit.AuditAction;
import io.github.grigoriev.im.audit.AuditService;
import io.github.grigoriev.im.einvoice.InvoiceFormat;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.core.simple.JdbcClient.StatementSpec;

class DispatchServiceTest {

    /** 23:30 in Berlin on March 18, already the 19th: today comes from the zone of the clock. */
    private static final Instant NOW = Instant.parse("2027-03-18T23:30:00Z");
    private static final LocalDate TODAY = LocalDate.of(2027, 3, 19);
    private static final LocalDate ISSUED_ON = LocalDate.of(2027, 3, 10);

    private final InvoiceService invoices = mock(InvoiceService.class);
    private final DispatchRepository repository = mock(DispatchRepository.class);
    private final JdbcClient jdbc = mock(JdbcClient.class);
    private final StatementSpec statement = mock(StatementSpec.class);
    private final AuditService audit = mock(AuditService.class);
    private final DispatchService service = new DispatchService(invoices, repository, jdbc, audit,
            Clock.fixed(NOW, ZoneId.of("Europe/Berlin")));

    private static Invoice invoice(InvoiceStatus status) {
        return new Invoice(5L, status, 2L, status == InvoiceStatus.DRAFT ? null : "2027-01",
                InvoiceFormat.ZUGFERD_EN16931, ISSUED_ON, ISSUED_ON, ISSUED_ON, 14, null, null, null, null, List.of(), NOW,
                NOW, 3L, status == InvoiceStatus.DRAFT ? null : NOW, status == InvoiceStatus.DRAFT ? null : "{}");
    }

    private static DispatchForm form(LocalDate sentOn, DispatchChannel channel, String recipient, String note) {
        DispatchForm form = new DispatchForm();
        form.setSentOn(sentOn);
        form.setChannel(channel);
        form.setRecipient(recipient);
        form.setNote(note);
        return form;
    }

    @BeforeEach
    void issued() {
        when(invoices.find(5)).thenReturn(Optional.of(invoice(InvoiceStatus.ISSUED)));
        when(repository.save(any())).thenAnswer(call -> call.getArgument(0));
        when(jdbc.sql(DispatchService.SENT)).thenReturn(statement);
        when(statement.param(anyString(), any())).thenReturn(statement);
    }

    @Test
    void recordsADispatchAndMovesTheStatus() {
        Dispatch saved = service.recordManual(5, form(TODAY, DispatchChannel.POST, "  Buchhaltung, Hafenweg 3 ", " "));
        assertThat(saved).isEqualTo(new Dispatch(null, 5L, TODAY, DispatchChannel.POST, "Buchhaltung, Hafenweg 3", null,
                "system", NOW));
        verify(statement).param("at", Timestamp.from(NOW));
        verify(statement).param("id", 5L);
        verify(statement).update();
        verify(audit).add(5L, AuditAction.INVOICE_SENT, "2027-01, 19.03.2027, Post, Buchhaltung, Hafenweg 3");
    }

    @Test
    void acceptsTheDayOfTheInvoiceAndNoRecipient() {
        Dispatch saved = service.recordManual(5, form(ISSUED_ON, DispatchChannel.OWN_MAIL, null, "per Mail an Frau M."));
        assertThat(saved.note()).isEqualTo("per Mail an Frau M.");
        assertThat(saved.recipient()).isNull();
        verify(audit).add(5L, AuditAction.INVOICE_SENT,
                "2027-01, 10.03.2027, Eigenes Mailprogramm, Notiz: per Mail an Frau M.");
    }

    @ParameterizedTest
    @EnumSource(value = InvoiceStatus.class, names = {"SENT", "PAID"})
    void recordsAnotherDispatchOfASentOrPaidInvoice(InvoiceStatus status) {
        when(invoices.find(5)).thenReturn(Optional.of(invoice(status)));
        service.recordManual(5, form(TODAY, DispatchChannel.PORTAL, null, null));
        verify(statement).update();
    }

    @Test
    void refusesWrongValues() {
        InvoiceException e = catchThrowableOfType(InvoiceException.class,
                () -> service.recordManual(5, form(null, null, "r".repeat(201), "n".repeat(501))));
        assertThat(e.problems()).containsExactly("Bitte das Versanddatum angeben.", "Bitte den Weg wählen.",
                "Der Empfänger hat höchstens 200 Zeichen.", "Die Notiz hat höchstens 500 Zeichen.");
        assertThat(catchThrowableOfType(InvoiceException.class,
                () -> service.recordManual(5, form(TODAY.plusDays(1), DispatchChannel.EMAIL_APP, "r".repeat(200),
                        "n".repeat(500)))).problems())
                .containsExactly("Das Versanddatum liegt in der Zukunft.", "Bitte den Weg wählen.");
        assertThat(catchThrowableOfType(InvoiceException.class,
                () -> service.recordManual(5, form(ISSUED_ON.minusDays(1), DispatchChannel.POST, null, null))).problems())
                .containsExactly("Das Versanddatum liegt vor dem Rechnungsdatum.");
        verifyNoInteractions(repository, jdbc, audit);
    }

    @Test
    void refusesADraftACancelledAndAnUnknownInvoice() {
        DispatchForm form = form(TODAY, DispatchChannel.POST, null, null);
        when(invoices.find(5)).thenReturn(Optional.of(invoice(InvoiceStatus.DRAFT)));
        assertThatThrownBy(() -> service.recordManual(5, form)).hasMessage("Erst ausstellen, dann versenden.");
        when(invoices.find(5)).thenReturn(Optional.of(invoice(InvoiceStatus.CANCELLED)));
        assertThatThrownBy(() -> service.recordManual(5, form))
                .hasMessage("Eine stornierte Rechnung wird nicht mehr versendet.");
        when(invoices.find(6)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.recordManual(6, form)).hasMessage("Diese Rechnung gibt es nicht.");
        verifyNoInteractions(repository, jdbc, audit);
    }

    @Test
    void recordsAnEmailOfTheAppToday() {
        Dispatch saved = service.recordEmail(5, "a@b.example", "PDF");
        assertThat(saved).isEqualTo(new Dispatch(null, 5L, TODAY, DispatchChannel.EMAIL_APP, "a@b.example", "PDF",
                "system", NOW));
        verify(statement).update();
        verify(audit).add(5L, AuditAction.INVOICE_SENT, "2027-01, 19.03.2027, E-Mail aus der App, a@b.example, Notiz: PDF");
        when(invoices.find(5)).thenReturn(Optional.of(invoice(InvoiceStatus.CANCELLED)));
        assertThatThrownBy(() -> service.recordEmail(5, "a@b.example", "PDF"))
                .hasMessage("Eine stornierte Rechnung wird nicht mehr versendet.");
    }

    @Test
    void listsTheDispatchesAndKnowsToday() {
        List<Dispatch> dispatches = List.of(new Dispatch(1L, 5L, TODAY, DispatchChannel.POST, null, null, "elena", NOW));
        when(repository.findByInvoiceIdOrderBySentOnAscIdAsc(5)).thenReturn(dispatches);
        assertThat(service.list(5)).isEqualTo(dispatches);
        assertThat(service.today()).isEqualTo(TODAY);
    }

    @Test
    void labelsTheChannels() {
        assertThat(DispatchChannel.values()).allSatisfy(channel -> assertThat(channel.label()).isNotBlank());
        assertThat(DispatchChannel.EMAIL_APP.isManual()).isFalse();
        assertThat(DispatchChannel.POST.isManual()).isTrue();
        assertThat(DispatchChannel.IN_PERSON.label()).isEqualTo("Persönlich übergeben");
    }
}
