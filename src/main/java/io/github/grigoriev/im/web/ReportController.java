package io.github.grigoriev.im.web;

import io.github.grigoriev.im.einvoice.TaxCategory;
import io.github.grigoriev.im.threshold.RevenueRow;
import io.github.grigoriev.im.threshold.ThresholdService;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * The page "Berichte": the income of a year by payment received (Zuflussprinzip), with the fields of the Anlage EÜR,
 * and the CSV of its payments. Both roles use it.
 */
@Controller
@RequestMapping("/berichte")
public class ReportController {

    static final String VIEW = "reports";

    /** The CSV head. A spreadsheet opens it with semicolons, German numbers and the BOM for UTF-8. */
    static final String CSV_HEAD = "Zahlungseingang;Rechnungsnummer;Kunde;Betrag;davon § 19 UStG;"
            + "davon nicht für die Grenze;Notiz";

    /** The byte order mark that tells a spreadsheet the CSV is UTF-8. */
    static final char BOM = (char) 0xFEFF;

    private static final DateTimeFormatter GERMAN = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    /**
     * The totals of a year.
     *
     * @param total the income received as Kleinunternehmer, refunds subtracted: Anlage EÜR, Zeile 12
     * @param excluded the part outside the §19 threshold: Zeile 13
     * @param counted the part that counts for §19
     * @param vatSales the income with VAT, gross: it belongs in Zeilen 15 and 17, not in 12 and 13
     * @param byCategory the income per tax category
     */
    public record Totals(BigDecimal total, BigDecimal excluded, BigDecimal counted, BigDecimal vatSales,
            Map<TaxCategory, BigDecimal> byCategory) {
    }

    private final ThresholdService threshold;

    /**
     * Creates the controller.
     *
     * @param threshold the payments of a year
     */
    public ReportController(ThresholdService threshold) {
        this.threshold = threshold;
    }

    /**
     * Shows the report of a year.
     *
     * @param jahr the year, this year if not given
     * @param model the page model
     * @return the template name
     */
    @GetMapping
    public String show(@RequestParam(required = false) Integer jahr, Model model) {
        int year = jahr == null ? threshold.today().getYear() : jahr;
        List<RevenueRow> rows = threshold.rows(year);
        model.addAttribute("year", year);
        SortedSet<Integer> years = new TreeSet<>(Comparator.reverseOrder());
        years.addAll(threshold.years());
        years.add(year);
        model.addAttribute("years", years);
        model.addAttribute("rows", rows);
        model.addAttribute("totals", totals(rows));
        model.addAttribute("categories", InvoiceController.CATEGORIES);
        return VIEW;
    }

    /**
     * Downloads the payments of a year as CSV.
     *
     * @param jahr the year
     * @return the CSV file
     */
    @GetMapping("/zahlungen.csv")
    public ResponseEntity<byte[]> csv(@RequestParam int jahr) {
        StringBuilder csv = new StringBuilder().append(BOM).append(CSV_HEAD).append("\r\n");
        for (RevenueRow row : threshold.rows(jahr)) {
            csv.append(String.join(";", GERMAN.format(row.paidOn()), field(row.number()), field(row.customer()),
                    number(row.amount()), number(row.counted()), number(row.excluded()), field(row.note())))
                    .append("\r\n");
        }
        return ResponseEntity.ok().contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename("Zahlungen_" + jahr + ".csv").build().toString())
                .body(csv.toString().getBytes(StandardCharsets.UTF_8));
    }

    static Totals totals(List<RevenueRow> rows) {
        Map<TaxCategory, BigDecimal> byCategory = new EnumMap<>(TaxCategory.class);
        rows.forEach(row -> row.byCategory().forEach((category, amount) -> byCategory.merge(category, amount,
                BigDecimal::add)));
        BigDecimal vatSales = sum(byCategory.entrySet().stream().filter(entry -> entry.getKey().chargesVat())
                .map(Map.Entry::getValue).toList());
        return new Totals(sum(rows.stream().map(RevenueRow::amount).toList()).subtract(vatSales),
                sum(rows.stream().map(RevenueRow::excluded).toList()).subtract(vatSales),
                sum(rows.stream().map(RevenueRow::counted).toList()), vatSales, byCategory);
    }

    private static BigDecimal sum(List<BigDecimal> amounts) {
        return amounts.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** A text field: quoted, with doubled quotes, and without a leading formula character. */
    static String field(String text) {
        if (text == null) {
            return "";
        }
        String safe = !text.isEmpty() && "=+-@\t\r".indexOf(text.charAt(0)) >= 0 ? "'" + text : text;
        return "\"" + safe.replace("\"", "\"\"") + "\"";
    }

    /** A German number with two decimals and no grouping, for a spreadsheet: {@code -1234,50}. */
    static String number(BigDecimal amount) {
        return new DecimalFormat("0.00", DecimalFormatSymbols.getInstance(Locale.GERMANY)).format(amount);
    }
}
