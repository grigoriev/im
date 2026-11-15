package io.github.grigoriev.im.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.grigoriev.im.einvoice.TaxCategory;
import io.github.grigoriev.im.invoice.ArchiveService;
import io.github.grigoriev.im.seller.Activity;
import io.github.grigoriev.im.seller.SellerProfile;
import io.github.grigoriev.im.seller.SellerService;
import io.github.grigoriev.im.seller.SellerTestData;
import io.github.grigoriev.im.threshold.RevenueRow;
import io.github.grigoriev.im.threshold.ThresholdService;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

class ReportControllerTest {

    private final ThresholdService threshold = mock(ThresholdService.class);
    private final ArchiveService archive = mock(ArchiveService.class);
    private final SellerService sellers = mock(SellerService.class);
    private final ReportController controller = new ReportController(threshold, archive, sellers);

    private static RevenueRow row(String amount, String counted, String note) {
        BigDecimal value = new BigDecimal(amount);
        BigDecimal part = new BigDecimal(counted);
        return new RevenueRow(LocalDate.of(2027, 3, 18), "2027-01", "Verlag \"am\" Fluss", value, part,
                value.subtract(part), Map.of(TaxCategory.KLEINUNTERNEHMER, part, TaxCategory.EXEMPT_4_21,
                        value.subtract(part)), note, null);
    }

    @Test
    @SuppressWarnings("unchecked")
    void sumsTheYearForTheAnlageEuer() {
        when(threshold.today()).thenReturn(LocalDate.of(2027, 5, 1));
        when(threshold.years()).thenReturn(List.of(2027, 2026));
        when(threshold.rows(2027)).thenReturn(List.of(row("560.00", "200.00", null), row("-100.00", "-35.71", null)));
        ExtendedModelMap model = new ExtendedModelMap();
        assertThat(controller.show(null, model)).isEqualTo("reports");
        ReportController.Totals totals = (ReportController.Totals) model.get("totals");
        assertThat(totals.total()).isEqualByComparingTo("460.00");
        assertThat(totals.excluded()).isEqualByComparingTo("295.71");
        assertThat(totals.counted()).isEqualByComparingTo("164.29");
        assertThat(totals.byCategory()).containsEntry(TaxCategory.KLEINUNTERNEHMER, new BigDecimal("164.29"))
                .containsEntry(TaxCategory.EXEMPT_4_21, new BigDecimal("295.71"));
        assertThat(model).containsEntry("year", 2027);
        assertThat((Iterable<Object>) model.get("years")).containsExactly(2027, 2026);

        controller.show(2019, model);
        assertThat(model).containsEntry("year", 2019);
        assertThat((Iterable<Object>) model.get("years")).as("the asked year joins the list").containsExactly(2027, 2026, 2019);

        when(archive.years()).thenReturn(Set.of(2024, 2026));
        controller.show(null, model);
        assertThat((Iterable<Object>) model.get("years")).as("a year with documents only joins too")
                .containsExactly(2027, 2026, 2024);
    }

    @Test
    void keepsSalesWithVatOutOfTheKleinunternehmerFields() {
        RevenueRow books = new RevenueRow(LocalDate.of(2027, 3, 18), "2027-02", "Schule", new BigDecimal("100.00"),
                BigDecimal.ZERO, new BigDecimal("100.00"), Map.of(TaxCategory.EXEMPT_4_21, new BigDecimal("93.83"),
                        TaxCategory.STANDARD_19, new BigDecimal("6.17")), null, null);
        ReportController.Totals totals = ReportController.totals(List.of(row("560.00", "200.00", null), books));
        assertThat(totals.vatSales()).isEqualByComparingTo("6.17");
        assertThat(totals.total()).isEqualByComparingTo("653.83");
        assertThat(totals.excluded()).isEqualByComparingTo("453.83");
        assertThat(totals.counted()).isEqualByComparingTo("200.00");
        assertThat(ReportController.totals(List.of()).vatSales()).isEqualByComparingTo("0");
    }

    @Test
    void writesTheCsvForASpreadsheet() {
        when(threshold.rows(2027)).thenReturn(List.of(row("1234.50", "1234.50", "=SUMME(A1)"), row("-80.00", "-80.00",
                null)));
        ResponseEntity<byte[]> csv = controller.csv(2027);
        assertThat(csv.getHeaders().getContentType()).hasToString("text/csv;charset=UTF-8");
        assertThat(csv.getHeaders().getContentDisposition().getFilename()).isEqualTo("Zahlungen_2027.csv");
        assertThat(new String(csv.getBody(), StandardCharsets.UTF_8)).isEqualTo(ReportController.BOM + ReportController.CSV_HEAD
                + "\r\n18.03.2027;\"2027-01\";\"Verlag \"\"am\"\" Fluss\";1234,50;1234,50;0,00;0,00;\"'=SUMME(A1)\"\r\n"
                + "18.03.2027;\"2027-01\";\"Verlag \"\"am\"\" Fluss\";-80,00;-80,00;0,00;0,00;\r\n");
        when(threshold.rows(2027)).thenReturn(List.of(row("300.00", "100.00", null)));
        assertThat(new String(controller.csv(2027).getBody(), StandardCharsets.UTF_8)).as("the teaching column")
                .endsWith(";300,00;100,00;200,00;200,00;\r\n");
    }

    @Test
    void writesOneAnlageEuerPerTaxNumberWhenTeachingHasItsOwn() {
        List<RevenueRow> rows = List.of(row("560.00", "200.00", null), row("-100.00", "-35.71", null));
        SellerProfile single = SellerTestData.profile();
        List<ReportController.Business> one = ReportController.businesses(rows, single);
        assertThat(one).singleElement().satisfies(business -> {
            assertThat(business.key()).isEmpty();
            assertThat(business.label()).isNull();
            assertThat(business.taxNumber()).isEqualTo("12/345/67890");
            assertThat(business.totals().total()).isEqualByComparingTo("460.00");
        });
        assertThat(ReportController.businesses(rows, null)).singleElement()
                .satisfies(business -> assertThat(business.taxNumber()).isNull());

        SellerProfile two = new SellerProfile(single.name(),
                single.street(), single.postcode(), single.city(), single.countryCode(), null, single.email(),
                single.phone(), single.taxNumber(), null, single.iban(), single.bic(), single.taxStatus(), null, null,
                null, null, "98/765/43210", false);
        // Teaching abroad is not taxable in Germany, yet it belongs to teaching: the business splits, not the category.
        TaxCategory abroad = TaxCategory.NOT_TAXABLE_ABROAD;
        RevenueRow uzbekistan = new RevenueRow(LocalDate.of(2027, 4, 1), "2027-02", "Goethe-Zentrum", new BigDecimal("300.00"),
                BigDecimal.ZERO, new BigDecimal("300.00"), Map.of(abroad, new BigDecimal("300.00")), null,
                Activity.TEACHING);
        RevenueRow reading = new RevenueRow(LocalDate.of(2027, 4, 2), "2027-03", "Verlag", new BigDecimal("100.00"),
                new BigDecimal("100.00"), BigDecimal.ZERO, Map.of(TaxCategory.KLEINUNTERNEHMER, new BigDecimal("100.00")),
                null, Activity.OTHER);
        List<ReportController.Business> split = ReportController.businesses(List.of(uzbekistan, reading), two);
        assertThat(split).extracting(ReportController.Business::key).containsExactly("-teaching", "-other");
        assertThat(split).extracting(ReportController.Business::label).containsExactly("Unterricht", "Übrige Leistungen");
        ReportController.Business teaching = split.get(0);
        assertThat(teaching.taxNumber()).isEqualTo("98/765/43210");
        assertThat(teaching.totals().total()).isEqualByComparingTo("300.00");
        assertThat(teaching.totals().excluded()).as("not taxable, Zeile 13").isEqualByComparingTo("300.00");
        assertThat(teaching.totals().counted()).isEqualByComparingTo("0");
        ReportController.Business other = split.get(1);
        assertThat(other.taxNumber()).isEqualTo("12/345/67890");
        assertThat(other.totals().total()).isEqualByComparingTo("100.00");
        assertThat(other.totals().excluded()).isEqualByComparingTo("0");
        assertThat(other.totals().counted()).isEqualByComparingTo("100.00");

    }

    @Test
    void neutralizesFormulasInTextFields() {
        assertThat(ReportController.field("+49")).isEqualTo("\"'+49\"");
        assertThat(ReportController.field("-1")).isEqualTo("\"'-1\"");
        assertThat(ReportController.field("@x")).isEqualTo("\"'@x\"");
        assertThat(ReportController.field("\tx")).isEqualTo("\"'\tx\"");
        assertThat(ReportController.field("\rx")).isEqualTo("\"'\rx\"");
        assertThat(ReportController.field("")).isEqualTo("\"\"");
        assertThat(ReportController.field("Lesung")).isEqualTo("\"Lesung\"");
        assertThat(ReportController.number(new BigDecimal("1234567.5"))).isEqualTo("1234567,50");
    }

    @Test
    void streamsTheArchiveOfAYear() throws Exception {
        ResponseEntity<StreamingResponseBody> zip = controller.zip(2027);
        assertThat(zip.getHeaders().getContentType()).hasToString("application/zip");
        assertThat(zip.getHeaders().getContentDisposition().getFilename()).isEqualTo("Rechnungen_2027.zip");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        zip.getBody().writeTo(out);
        verify(archive).write(2027, out);
    }
}
