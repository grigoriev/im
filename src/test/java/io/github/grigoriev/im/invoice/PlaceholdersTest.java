package io.github.grigoriev.im.invoice;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.grigoriev.im.einvoice.InvoiceFormat;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class PlaceholdersTest {

    private static final LocalDate DAY = LocalDate.of(2027, 3, 18);

    @Test
    void fillsEveryPlaceholderAsThePdfShowsIt() {
        Placeholders values = new Placeholders("2027-07", DAY, LocalDate.of(2027, 3, 1), LocalDate.of(2027, 3, 31),
                LocalDate.of(2027, 4, 1));
        assertThat(values.fill("Rechnung {Rechnungsnummer} vom {Rechnungsdatum} für {Leistungszeitraum}, "
                + "zahlbar bis {Zahlungsziel}. {Unbekannt} bleibt."))
                .isEqualTo("Rechnung 2027-07 vom 18.03.2027 für 01.03.2027 bis 31.03.2027, zahlbar bis 01.04.2027. "
                        + "{Unbekannt} bleibt.");
        assertThat(values.fill("{Rechnungsnummer} und {Rechnungsnummer}")).isEqualTo("2027-07 und 2027-07");
        assertThat(values.fill(null)).isNull();
    }

    @Test
    void takesTheValuesOfAnInvoice() {
        Invoice invoice = new Invoice(1L, InvoiceStatus.DRAFT, 2L, null, InvoiceFormat.ZUGFERD_EN16931, DAY, DAY, DAY, 30,
                null, null, null, null, List.of(), Instant.EPOCH, Instant.EPOCH, 0L, null, null);
        Placeholders values = Placeholders.of(invoice, "Entwurf");
        assertThat(values).isEqualTo(new Placeholders("Entwurf", DAY, DAY, DAY, LocalDate.of(2027, 4, 17)));
        assertThat(values.fill("{Leistungszeitraum}")).isEqualTo("18.03.2027");
    }
}
