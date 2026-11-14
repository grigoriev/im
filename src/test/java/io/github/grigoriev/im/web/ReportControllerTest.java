package io.github.grigoriev.im.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.github.grigoriev.im.einvoice.TaxCategory;
import io.github.grigoriev.im.threshold.RevenueRow;
import io.github.grigoriev.im.threshold.ThresholdService;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.ui.ExtendedModelMap;

class ReportControllerTest {

    private final ThresholdService threshold = mock(ThresholdService.class);
    private final ReportController controller = new ReportController(threshold);

    private static RevenueRow row(String amount, String counted, String note) {
        BigDecimal value = new BigDecimal(amount);
        BigDecimal part = new BigDecimal(counted);
        return new RevenueRow(LocalDate.of(2027, 3, 18), "2027-01", "Verlag \"am\" Fluss", value, part,
                value.subtract(part), Map.of(TaxCategory.KLEINUNTERNEHMER, part, TaxCategory.EXEMPT_4_21,
                        value.subtract(part)), note);
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
    }

    @Test
    void writesTheCsvForASpreadsheet() {
        when(threshold.rows(2027)).thenReturn(List.of(row("1234.50", "1234.50", "=SUMME(A1)"), row("-80.00", "-80.00",
                null)));
        ResponseEntity<byte[]> csv = controller.csv(2027);
        assertThat(csv.getHeaders().getContentType()).hasToString("text/csv;charset=UTF-8");
        assertThat(csv.getHeaders().getContentDisposition().getFilename()).isEqualTo("Zahlungen_2027.csv");
        assertThat(new String(csv.getBody(), StandardCharsets.UTF_8)).isEqualTo("\uFEFF" + ReportController.CSV_HEAD
                + "\r\n18.03.2027;\"2027-01\";\"Verlag \"\"am\"\" Fluss\";1234,50;1234,50;0,00;\"'=SUMME(A1)\"\r\n"
                + "18.03.2027;\"2027-01\";\"Verlag \"\"am\"\" Fluss\";-80,00;-80,00;0,00;\r\n");
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
}
