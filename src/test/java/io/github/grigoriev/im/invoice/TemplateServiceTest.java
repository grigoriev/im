package io.github.grigoriev.im.invoice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.github.grigoriev.im.audit.AuditAction;
import io.github.grigoriev.im.audit.AuditService;
import io.github.grigoriev.im.customer.CustomerService;
import io.github.grigoriev.im.customer.CustomerTestData;
import io.github.grigoriev.im.einvoice.InvoiceFormat;
import io.github.grigoriev.im.einvoice.TaxCategory;
import io.github.grigoriev.im.seller.TaxNoteService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.OptimisticLockingFailureException;

class TemplateServiceTest {

    private static final Instant NOW = Instant.parse("2027-03-18T08:00:00Z");

    private final TemplateRepository repository = mock(TemplateRepository.class);
    private final CustomerService customers = mock(CustomerService.class);
    private final InvoiceService invoices = mock(InvoiceService.class);
    private final TaxNoteService notes = mock(TaxNoteService.class);
    private final AuditService audit = mock(AuditService.class);
    private final TemplateService service = new TemplateService(repository, customers, invoices, notes, audit,
            Clock.fixed(NOW, ZoneId.of("Europe/Berlin")));

    @BeforeEach
    void defaults() {
        when(customers.find(2)).thenReturn(Optional.of(CustomerTestData.vhs().withId(2L)));
        when(invoices.defaultCategory()).thenReturn(TaxCategory.EXEMPT_4_21);
        when(notes.note(TaxCategory.EXEMPT_4_21)).thenReturn("Steuerfrei gemäß § 4 Nr. 21 UStG.");
        when(repository.save(any())).thenAnswer(call -> call.getArgument(0));
    }

    private static InvoiceTemplate template(long customerId) {
        return new InvoiceTemplate(7L, customerId, "Kurs", InvoiceFormat.XRECHNUNG, 30, "Hallo", "Danke", "Steuerfrei.",
                "Rechnung {Rechnungsnummer}", "Anbei", List.of(new TemplateLine("Kurs", false, new BigDecimal("24"),
                        new BigDecimal("41"), TaxCategory.EXEMPT_4_21)), NOW, NOW, 0L);
    }

    private static LineForm row(String text, String amount) {
        LineForm row = new LineForm();
        row.setText(text);
        row.setAmount(amount);
        row.setCategory(TaxCategory.EXEMPT_4_21);
        return row;
    }

    @Test
    void listsAndFindsOnlyTheTemplatesOfTheCustomer() {
        when(repository.findByCustomerIdOrderByNameAsc(2L)).thenReturn(List.of(template(2)));
        assertThat(service.list(2)).hasSize(1);
        when(repository.findById(7L)).thenReturn(Optional.of(template(2)));
        assertThat(service.find(2, 7)).isPresent();
        assertThat(service.find(3, 7)).as("a template of another customer").isEmpty();
    }

    @Test
    void createsATemplateWithTheDefaultsOfADraft() {
        InvoiceTemplate created = service.create(2);
        assertThat(created.customerId()).isEqualTo(2L);
        assertThat(created.name()).isEqualTo("Neue Vorlage");
        assertThat(created.format()).isEqualTo(InvoiceFormat.XRECHNUNG);
        assertThat(created.paymentDays()).isEqualTo(14);
        assertThat(created.intro()).isEqualTo(InvoiceService.DEFAULT_INTRO);
        assertThat(created.closing()).isEqualTo(InvoiceService.DEFAULT_CLOSING);
        assertThat(created.taxNote()).isEqualTo("Steuerfrei gemäß § 4 Nr. 21 UStG.");
        assertThat(created.emailSubject()).isEqualTo("Rechnung {Rechnungsnummer}");
        assertThat(created.emailBody()).contains("{Leistungszeitraum}");
        assertThat(created.lines()).containsExactly(TemplateLine.of(DraftLine.empty(TaxCategory.EXEMPT_4_21)));
        assertThat(created.createdAt()).isEqualTo(NOW);
        assertThat(created.version()).isNull();
        verify(audit).add(AuditAction.TEMPLATE_CREATED, "Volkshochschule Musterstadt");
        when(customers.find(9)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.create(9)).isInstanceOf(InvoiceException.class)
                .hasMessage("Diesen Kunden gibt es nicht.");
    }

    @Test
    void savesTheEnteredTemplate() {
        when(repository.findById(7L)).thenReturn(Optional.of(template(2)));
        TemplateForm form = TemplateForm.of(template(2));
        form.setName("  Integrationskurs ");
        form.setIntro(" ");
        form.setEmailBody("Anbei die Rechnung.");
        form.setTaxNote(null);
        form.getLines().add(row("Prüfung", "120"));
        InvoiceTemplate saved = service.save(2, 7, form);
        assertThat(saved.name()).isEqualTo("Integrationskurs");
        assertThat(saved.intro()).isNull();
        assertThat(saved.closing()).isEqualTo("Danke");
        assertThat(saved.taxNote()).isNull();
        assertThat(saved.emailSubject()).isEqualTo("Rechnung {Rechnungsnummer}");
        assertThat(saved.emailBody()).isEqualTo("Anbei die Rechnung.");
        assertThat(saved.paymentDays()).isEqualTo(30);
        assertThat(saved.format()).isEqualTo(InvoiceFormat.XRECHNUNG);
        assertThat(saved.lines()).extracting(TemplateLine::text).containsExactly("Kurs", "Prüfung");
        assertThat(saved.lines().get(1).unitPrice()).isEqualByComparingTo("120");
        assertThat(saved.createdAt()).isEqualTo(NOW);
        assertThat(saved.updatedAt()).isEqualTo(NOW);
        assertThat(saved.version()).isZero();
    }

    @Test
    void refusesEveryProblem() {
        when(repository.findById(7L)).thenReturn(Optional.of(template(2)));
        TemplateForm form = TemplateForm.of(template(2));
        form.setName(" ");
        form.setFormat(null);
        form.setPaymentDays(400);
        form.setIntro("i".repeat(2001));
        form.setClosing("c".repeat(2001));
        form.setTaxNote("t".repeat(1001));
        form.setEmailSubject("s".repeat(201));
        form.setEmailBody("b".repeat(4001));
        form.getLines().add(row("Prüfung", "viel"));
        InvoiceException e = catchThrowableOfType(InvoiceException.class, () -> service.save(2, 7, form));
        assertThat(e.problems()).containsExactly(
                "Position 2: Der Betrag ist keine Zahl, zum Beispiel 45,00.",
                "Bitte der Vorlage einen Namen geben.",
                "Die Einleitung hat höchstens 2000 Zeichen.",
                "Der Schlusstext hat höchstens 2000 Zeichen.",
                "Der Steuerhinweis hat höchstens 1000 Zeichen.",
                "Der Betreff hat höchstens 200 Zeichen.",
                "Der Text der E-Mail hat höchstens 4000 Zeichen.",
                "Das Zahlungsziel liegt zwischen 0 und 365 Tagen.",
                "Bitte das E-Rechnungsformat wählen.");
        TemplateForm longName = TemplateForm.of(template(2));
        longName.setName("n".repeat(101));
        longName.setPaymentDays(-1);
        assertThat(catchThrowableOfType(InvoiceException.class, () -> service.save(2, 7, longName)).problems())
                .containsExactly("Der Name hat höchstens 100 Zeichen.", "Das Zahlungsziel liegt zwischen 0 und 365 Tagen.");
        TemplateForm nullName = TemplateForm.of(template(2));
        nullName.setName(null);
        assertThat(catchThrowableOfType(InvoiceException.class, () -> service.save(2, 7, nullName)).problems())
                .containsExactly("Bitte der Vorlage einen Namen geben.");
        verify(repository, never()).save(any());
    }

    @Test
    void acceptsTheLimits() {
        when(repository.findById(7L)).thenReturn(Optional.of(template(2)));
        TemplateForm form = TemplateForm.of(template(2));
        form.setName("n".repeat(100));
        form.setPaymentDays(365);
        form.setEmailSubject("s".repeat(200));
        form.setEmailBody("b".repeat(4000));
        assertThat(service.save(2, 7, form).name()).hasSize(100);
        form.setPaymentDays(0);
        assertThat(service.save(2, 7, form).paymentDays()).isZero();
    }

    @Test
    void refusesAnUnknownTemplateAndAnOlderVersion() {
        when(repository.findById(7L)).thenReturn(Optional.of(template(3)));
        TemplateForm form = TemplateForm.of(template(3));
        assertThatThrownBy(() -> service.save(2, 7, form)).isInstanceOf(InvoiceException.class)
                .hasMessage("Diese Vorlage gibt es nicht.");
        assertThatThrownBy(() -> service.delete(2, 7)).isInstanceOf(InvoiceException.class);
        when(repository.findById(7L)).thenReturn(Optional.of(template(2)));
        form.setVersion(-1L);
        assertThatThrownBy(() -> service.save(2, 7, form)).isInstanceOf(InvoiceException.class)
                .hasMessage(TemplateService.CHANGED_ELSEWHERE);
        form.setVersion(null);
        assertThatThrownBy(() -> service.save(2, 7, form)).isInstanceOf(InvoiceException.class);
        form.setVersion(0L);
        when(repository.save(any())).thenThrow(new OptimisticLockingFailureException("race"));
        assertThatThrownBy(() -> service.save(2, 7, form)).isInstanceOf(InvoiceException.class)
                .hasMessage(TemplateService.CHANGED_ELSEWHERE);
        verify(repository, never()).deleteById(7L);
        verifyNoInteractions(audit);
    }

    @Test
    void deletesATemplate() {
        when(repository.findById(7L)).thenReturn(Optional.of(template(2)));
        assertThat(service.delete(2, 7).name()).isEqualTo("Kurs");
        verify(repository).deleteById(7L);
        verify(audit).add(AuditAction.TEMPLATE_DELETED, "Kurs");
    }
}
