package io.github.grigoriev.im.invoice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.grigoriev.im.einvoice.InvoiceFormat;
import io.github.grigoriev.im.einvoice.TaxCategory;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class TemplateModelTest {

    @Test
    void aTemplateLineMirrorsADraftLine() {
        DraftLine line = new DraftLine("Kurs", false, new BigDecimal("24"), new BigDecimal("41"), TaxCategory.EXEMPT_4_21);
        TemplateLine template = TemplateLine.of(line);
        assertThat(template.quantity()).isEqualTo(new BigDecimal("24.0000"));
        assertThat(template.unitPrice()).isEqualTo(new BigDecimal("41.0000"));
        assertThat(template.text()).isEqualTo("Kurs");
        assertThat(template.lumpSum()).isFalse();
        assertThat(template.category()).isEqualTo(TaxCategory.EXEMPT_4_21);
        assertThat(template.toDraftLine()).isEqualTo(line);
    }

    @Test
    void summarizesItsLinesAndKeepsACopy() {
        List<TemplateLine> lines = new ArrayList<>(List.of(
                new TemplateLine("Lesung", true, BigDecimal.ONE, new BigDecimal("400"), TaxCategory.KLEINUNTERNEHMER),
                new TemplateLine(" ", true, BigDecimal.ONE, BigDecimal.ZERO, TaxCategory.KLEINUNTERNEHMER),
                new TemplateLine("Reisekosten", true, BigDecimal.ONE, new BigDecimal("80"), TaxCategory.KLEINUNTERNEHMER)));
        InvoiceTemplate template = new InvoiceTemplate(1L, 2L, "Lesung", InvoiceFormat.ZUGFERD_EN16931, 14, null, null,
                null, null, null, lines, Instant.EPOCH, Instant.EPOCH, 0L);
        lines.clear();
        assertThat(template.summary()).isEqualTo("Lesung · Reisekosten");
        List<TemplateLine> stored = template.lines();
        assertThatThrownBy(stored::clear).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void fillsTheFormFromATemplate() {
        InvoiceTemplate template = new InvoiceTemplate(1L, 2L, "Lesung", InvoiceFormat.ZUGFERD_XRECHNUNG, 21, "Hallo",
                "Danke", "Steuer", "Betreff", "Text", List.of(new TemplateLine("Lesung", true, BigDecimal.ONE,
                        new BigDecimal("400"), TaxCategory.KLEINUNTERNEHMER)), Instant.EPOCH, Instant.EPOCH, 3L);
        TemplateForm form = TemplateForm.of(template);
        assertThat(form.getVersion()).isEqualTo(3L);
        assertThat(form.getName()).isEqualTo("Lesung");
        assertThat(form.getFormat()).isEqualTo(InvoiceFormat.ZUGFERD_XRECHNUNG);
        assertThat(form.getPaymentDays()).isEqualTo(21);
        assertThat(form.getIntro()).isEqualTo("Hallo");
        assertThat(form.getClosing()).isEqualTo("Danke");
        assertThat(form.getTaxNote()).isEqualTo("Steuer");
        assertThat(form.getEmailSubject()).isEqualTo("Betreff");
        assertThat(form.getEmailBody()).isEqualTo("Text");
        assertThat(form.getLines()).singleElement().satisfies(row -> assertThat(row.getAmount()).isEqualTo("400,00"));
        form.setLines(new ArrayList<>());
        assertThat(form.getLines()).isEmpty();
    }
}
