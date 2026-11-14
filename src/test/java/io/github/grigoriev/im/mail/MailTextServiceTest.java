package io.github.grigoriev.im.mail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.grigoriev.im.audit.AuditAction;
import io.github.grigoriev.im.audit.AuditService;
import io.github.grigoriev.im.einvoice.InvoiceFormat;
import io.github.grigoriev.im.invoice.Invoice;
import io.github.grigoriev.im.invoice.InvoiceService;
import io.github.grigoriev.im.invoice.InvoiceStatus;
import io.github.grigoriev.im.invoice.InvoiceTemplate;
import io.github.grigoriev.im.seller.SettingsException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.core.simple.JdbcClient.MappedQuerySpec;
import org.springframework.jdbc.core.simple.JdbcClient.StatementSpec;

class MailTextServiceTest {

    private static final LocalDate DAY = LocalDate.of(2027, 3, 18);

    private final JdbcClient jdbc = mock(JdbcClient.class);
    private final InvoiceService invoices = mock(InvoiceService.class);
    private final AuditService audit = mock(AuditService.class);
    private final MailTextService service = new MailTextService(jdbc, invoices, audit);
    private final Map<String, String> settings = new HashMap<>();
    private final Map<String, StatementSpec> statements = new HashMap<>();

    private final Invoice invoice = new Invoice(5L, InvoiceStatus.ISSUED, 2L, "2027-07", InvoiceFormat.ZUGFERD_EN16931,
            DAY, LocalDate.of(2027, 3, 1), LocalDate.of(2027, 3, 16), 14, null, null, null, null, List.of(),
            Instant.EPOCH, Instant.EPOCH, 1L, Instant.EPOCH, "{}");

    private static InvoiceTemplate template(String subject, String body) {
        return new InvoiceTemplate(7L, 2L, "Lesung", InvoiceFormat.ZUGFERD_EN16931, 14, null, null, null, subject, body,
                List.of(), Instant.EPOCH, Instant.EPOCH, 0L);
    }

    /** A statement per SQL that keeps its parameters, and a query that reads the settings map. */
    @SuppressWarnings("unchecked")
    private StatementSpec statement(String sql) {
        return statements.computeIfAbsent(sql, key -> {
            StatementSpec spec = mock(StatementSpec.class);
            Map<String, Object> params = new HashMap<>();
            when(spec.param(anyString(), any())).thenAnswer(call -> {
                params.put(call.getArgument(0), call.getArgument(1));
                return spec;
            });
            MappedQuerySpec<String> query = mock(MappedQuerySpec.class);
            when(spec.query(String.class)).thenReturn(query);
            when(query.optional()).thenAnswer(call -> Optional.ofNullable(settings.get((String) params.get("key"))));
            return spec;
        });
    }

    @BeforeEach
    void statements() {
        when(jdbc.sql(anyString())).thenAnswer(call -> statement(call.getArgument(0)));
        when(invoices.template(invoice)).thenReturn(Optional.empty());
    }

    @Test
    void startsWithTheBuiltInText() {
        assertThat(service.defaults()).isEqualTo(new MailText(MailTextService.DEFAULT_SUBJECT, MailTextService.DEFAULT_BODY));
        settings.put("mail-subject", "Meine Rechnung {Rechnungsnummer}");
        settings.put("mail-body", "Hallo");
        assertThat(service.defaults()).isEqualTo(new MailText("Meine Rechnung {Rechnungsnummer}", "Hallo"));
    }

    @Test
    void savesAChangedTextAndResetsABlankOrBuiltInOne() {
        service.save("  Rechnung {Rechnungsnummer} von Elena ", "Hallo,\r\nanbei die Rechnung.\r\n");
        verify(statements.get(MailTextService.WRITE)).param("value", "Rechnung {Rechnungsnummer} von Elena");
        verify(statements.get(MailTextService.WRITE)).param("value", "Hallo,\nanbei die Rechnung.");
        verify(audit).add(AuditAction.MAIL_TEXT_SAVED, "");

        service.save(" ", MailTextService.DEFAULT_BODY);
        verify(statements.get(MailTextService.RESET)).param("key", "mail-subject");
        verify(statements.get(MailTextService.RESET)).param("key", "mail-body");
        service.save(null, null);
        verify(statements.get(MailTextService.WRITE), org.mockito.Mockito.times(2)).update();
        verify(statements.get(MailTextService.RESET), org.mockito.Mockito.times(4)).update();
    }

    @Test
    void refusesTooLongTexts() {
        SettingsException e = catchThrowableOfType(SettingsException.class,
                () -> service.save("s".repeat(201), "b".repeat(4001)));
        assertThat(e.problems()).containsExactly("Der Betreff hat höchstens 200 Zeichen.",
                "Der Text hat höchstens 4000 Zeichen.");
        service.save("s".repeat(200), "b".repeat(4000));
        verify(audit).add(AuditAction.MAIL_TEXT_SAVED, "");
    }

    @Test
    void fillsTheDefaultTextForAnInvoice() {
        MailText text = service.forInvoice(invoice);
        assertThat(text.subject()).isEqualTo("Rechnung 2027-07");
        assertThat(text.body()).contains("meine Rechnung 2027-07 vom 18.03.2027, zahlbar bis 01.04.2027.");
    }

    @Test
    void takesTheTextOfTheTemplateFieldByField() {
        when(invoices.template(invoice)).thenReturn(Optional.of(template("Lesung {Leistungszeitraum}", null)));
        MailText text = service.forInvoice(invoice);
        assertThat(text.subject()).isEqualTo("Lesung 01.03.2027 bis 16.03.2027");
        assertThat(text.body()).startsWith("Sehr geehrte Damen und Herren,");
        when(invoices.template(invoice)).thenReturn(Optional.of(template(" ", "Liebe Frau M., {Rechnungsnummer}")));
        assertThat(service.forInvoice(invoice)).isEqualTo(new MailText("Rechnung 2027-07", "Liebe Frau M., 2027-07"));
        when(invoices.template(invoice)).thenReturn(Optional.of(template(null, " ")));
        assertThat(service.forInvoice(invoice).body()).startsWith("Sehr geehrte Damen und Herren,");
    }
}
