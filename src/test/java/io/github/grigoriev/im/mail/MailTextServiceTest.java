package io.github.grigoriev.im.mail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
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
import io.github.grigoriev.im.setting.SettingKey;
import io.github.grigoriev.im.setting.SettingRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MailTextServiceTest {

    private static final LocalDate DAY = LocalDate.of(2027, 3, 18);

    private final SettingRepository settingRepository = mock(SettingRepository.class);
    private final InvoiceService invoices = mock(InvoiceService.class);
    private final AuditService audit = mock(AuditService.class);
    private final MailTextService service = new MailTextService(settingRepository, invoices, audit);
    /** The stored settings by their key in the database. */
    private final Map<String, String> settings = new HashMap<>();

    private final Invoice invoice = new Invoice(5L, InvoiceStatus.ISSUED, 2L, "2027-07", InvoiceFormat.ZUGFERD_EN16931,
            DAY, LocalDate.of(2027, 3, 1), LocalDate.of(2027, 3, 16), 14, null, null, null, null, List.of(),
            Instant.EPOCH, Instant.EPOCH, 1L, Instant.EPOCH, "{}", null);

    private static InvoiceTemplate template(String subject, String body) {
        return new InvoiceTemplate(7L, 2L, "Lesung", InvoiceFormat.ZUGFERD_EN16931, 14, null, null, null, subject, body,
                List.of(), Instant.EPOCH, Instant.EPOCH, 0L, null);
    }

    @BeforeEach
    void statements() {
        when(settingRepository.read(any())).thenAnswer(
                call -> Optional.ofNullable(settings.get(call.<SettingKey>getArgument(0).stored())));
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
        verify(settingRepository).write(SettingKey.MAIL_SUBJECT, "Rechnung {Rechnungsnummer} von Elena");
        verify(settingRepository).write(SettingKey.MAIL_BODY, "Hallo,\nanbei die Rechnung.");
        verify(audit).add(AuditAction.MAIL_TEXT_SAVED, "");

        service.save(" ", MailTextService.DEFAULT_BODY);
        verify(settingRepository).reset(SettingKey.MAIL_SUBJECT);
        verify(settingRepository).reset(SettingKey.MAIL_BODY);
        service.save(null, null);
        verify(settingRepository, times(2)).write(any(), any());
        verify(settingRepository, times(4)).reset(any());
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

    @Test
    void keepsTheReminderTextApartAndFillsTheOpenAmount() {
        assertThat(service.reminderDefaults()).isEqualTo(new MailText(MailTextService.DEFAULT_REMINDER_SUBJECT,
                MailTextService.DEFAULT_REMINDER_BODY));
        MailText filled = service.forReminder(invoice, new BigDecimal("80"));
        assertThat(filled.subject()).isEqualTo("Zahlungserinnerung zur Rechnung 2027-07");
        assertThat(filled.body()).contains("meine Rechnung 2027-07 vom 18.03.2027 war am 01.04.2027 fällig. Offen sind "
                + "noch 80,00\u00a0€.").endsWith("Mit freundlichen Grüßen");
        settings.put("reminder-subject", "Offen: {Offener Betrag}");
        settings.put("reminder-body", "Bitte {Offener Betrag} für {Rechnungsnummer}");
        assertThat(service.forReminder(invoice, new BigDecimal("12.5"))).isEqualTo(new MailText("Offen: 12,50\u00a0€",
                "Bitte 12,50\u00a0€ für 2027-07"));
        assertThat(service.defaults()).as("the invoice text stays its own")
                .isEqualTo(new MailText(MailTextService.DEFAULT_SUBJECT, MailTextService.DEFAULT_BODY));

        service.saveReminder(" Erinnerung {Rechnungsnummer} ", "Bitte zahlen.\r\n");
        verify(settingRepository).write(SettingKey.REMINDER_SUBJECT, "Erinnerung {Rechnungsnummer}");
        verify(settingRepository).write(SettingKey.REMINDER_BODY, "Bitte zahlen.");
        verify(audit).add(AuditAction.MAIL_TEXT_SAVED, "Zahlungserinnerung");
        service.saveReminder(MailTextService.DEFAULT_REMINDER_SUBJECT, " ");
        verify(settingRepository).reset(SettingKey.REMINDER_BODY);
        assertThat(catchThrowableOfType(SettingsException.class, () -> service.saveReminder("s".repeat(201), null))
                .problems()).containsExactly("Der Betreff hat höchstens 200 Zeichen.");
    }
}
