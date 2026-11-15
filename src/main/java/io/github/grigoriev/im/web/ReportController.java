package io.github.grigoriev.im.web;

import io.github.grigoriev.im.einvoice.TaxCategory;
import io.github.grigoriev.im.expense.Expense;
import io.github.grigoriev.im.expense.ExpenseCategory;
import io.github.grigoriev.im.expense.ExpenseService;
import io.github.grigoriev.im.invoice.ArchiveService;
import io.github.grigoriev.im.seller.Activity;
import io.github.grigoriev.im.seller.SellerProfile;
import io.github.grigoriev.im.seller.SellerService;
import io.github.grigoriev.im.threshold.RevenueRow;
import io.github.grigoriev.im.threshold.ThresholdService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.math.BigDecimal;
import java.net.URI;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.FlashMap;
import org.springframework.web.servlet.support.RequestContextUtils;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

/**
 * The page "Berichte": the income of a year by payment received (Zuflussprinzip), with the fields of the Anlage EÜR,
 * the expenses by payment made (Abflussprinzip) and kind, the CSV of both and the archive of its documents. Both roles
 * use it.
 */
@Controller
@RequestMapping("/reports")
public class ReportController {

    static final String VIEW = "reports";
    static final String PATH = "/reports";
    static final String DAMAGED = "Das Archiv bleibt aus, denn diese Dateien sind beschädigt oder wurden außerhalb "
            + "der App geändert: %s. Bitte die Seiten dieser Rechnungen und Dokumente ansehen.";

    /** The CSV head. A spreadsheet opens it with semicolons, German numbers and the BOM for UTF-8. */
    static final String CSV_HEAD = "Zahlungseingang;Rechnungsnummer;Kunde;Betrag;davon § 19 UStG;"
            + "davon nicht für die Grenze;davon § 4 Nr. 21 UStG;Notiz";

    /** The CSV head of the expenses, for the transfer into WISO Steuer. */
    static final String EXPENSE_HEAD = "Zahlung;Betrag;Art;Empfänger;Beschreibung;Anlagegut;Beleg;SHA-256";

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

    /**
     * One Anlage EÜR: all income, or the income of one tax number when teaching has its own.
     *
     * @param key the suffix of the element IDs, empty for the only one
     * @param label the name of the business, {@code null} for the only one
     * @param taxNumber the tax number of the business, {@code null} if none is set
     * @param totals its totals
     */
    public record Business(String key, String label, String taxNumber, Totals totals) {
    }

    private final ThresholdService threshold;
    private final ArchiveService archive;
    private final SellerService sellers;
    private final ExpenseService expenses;

    /**
     * Creates the controller.
     *
     * @param threshold the payments of a year
     * @param archive the documents of a year
     * @param sellers the tax numbers of the seller
     * @param expenses the expenses of a year
     */
    public ReportController(ThresholdService threshold, ArchiveService archive, SellerService sellers,
            ExpenseService expenses) {
        this.threshold = threshold;
        this.archive = archive;
        this.sellers = sellers;
        this.expenses = expenses;
    }

    /**
     * Downloads the archive of a year: the PDF and XML of every invoice issued in it, and their table of contents.
     *
     * @param year the year of the invoice dates
     * @param request the request, for the flash message
     * @param response the response, for the flash message
     * @return the ZIP, written while it goes out; a redirect to the report if a file is broken or changed
     */
    @GetMapping("/archive.zip")
    public ResponseEntity<StreamingResponseBody> zip(@RequestParam int year, HttpServletRequest request,
            HttpServletResponse response) {
        List<String> damaged = archive.damaged(year);
        if (!damaged.isEmpty()) {
            // A broken or changed file stays out of the archive: the redirect carries the message as a flash attribute.
            FlashMap flash = RequestContextUtils.getOutputFlashMap(request);
            flash.put("error", DAMAGED.formatted(String.join(", ", damaged)));
            RequestContextUtils.saveOutputFlashMap(PATH, request, response);
            return ResponseEntity.status(HttpStatus.SEE_OTHER).location(URI.create(PATH + "?year=" + year)).build();
        }
        StreamingResponseBody body = out -> archive.write(year, out);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType("application/zip"))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename("Rechnungen_" + year + ".zip").build().toString())
                .body(body);
    }

    /**
     * Shows the report of a year.
     *
     * @param year the year, this year if not given
     * @param model the page model
     * @return the template name
     */
    @GetMapping
    public String show(@RequestParam(name = "year", required = false) Integer asked, Model model) {
        int year = asked == null ? threshold.today().getYear() : asked;
        List<RevenueRow> rows = threshold.rows(year);
        model.addAttribute("year", year);
        SortedSet<Integer> years = new TreeSet<>(Comparator.reverseOrder());
        years.addAll(threshold.years());
        years.addAll(archive.years());
        years.addAll(expenses.years());
        years.add(year);
        model.addAttribute("years", years);
        model.addAttribute("rows", rows);
        model.addAttribute("totals", totals(rows));
        model.addAttribute("businesses", businesses(rows, sellers.find().orElse(null)));
        model.addAttribute("categories", InvoiceController.CATEGORIES);
        List<Expense> spent = expenses.ofYear(year);
        Map<ExpenseCategory, BigDecimal> expenseTotals = ExpenseService.totals(spent);
        model.addAttribute("expenseTotals", expenseTotals);
        model.addAttribute("expenseTotal", sum(List.copyOf(expenseTotals.values())));
        model.addAttribute("assets", spent.stream().filter(expense -> expense.asset() && !expense.cancelled()).toList());
        return VIEW;
    }

    /**
     * Downloads the payments of a year as CSV.
     *
     * @param year the year
     * @return the CSV file
     */
    @GetMapping("/payments.csv")
    public ResponseEntity<byte[]> csv(@RequestParam int year) {
        StringBuilder csv = new StringBuilder().append(BOM).append(CSV_HEAD).append("\r\n");
        for (RevenueRow row : threshold.rows(year)) {
            csv.append(String.join(";", GERMAN.format(row.paidOn()), field(row.number()), field(row.customer()),
                    number(row.amount()), number(row.counted()), number(row.excluded()),
                    number(row.byCategory().getOrDefault(TaxCategory.EXEMPT_4_21, BigDecimal.ZERO)), field(row.note())))
                    .append("\r\n");
        }
        return ResponseEntity.ok().contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename("Zahlungen_" + year + ".csv").build().toString())
                .body(csv.toString().getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Downloads the valid expenses of a year as CSV, for the transfer into WISO Steuer.
     *
     * @param year the year of the payments
     * @return the CSV, UTF-8 with byte order mark
     */
    @GetMapping("/expenses.csv")
    public ResponseEntity<byte[]> expensesCsv(@RequestParam int year) {
        StringBuilder csv = new StringBuilder().append(BOM).append(EXPENSE_HEAD).append("\r\n");
        for (Expense expense : expenses.ofYear(year)) {
            if (!expense.cancelled()) {
                csv.append(String.join(";", GERMAN.format(expense.paidOn()), number(expense.amount()),
                        field(expense.category().label()), field(expense.vendor()), field(expense.description()),
                        expense.asset() ? "ja" : "nein", field(expense.filename()), expense.sha256())).append("\r\n");
            }
        }
        return ResponseEntity.ok().contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename("Ausgaben_" + year + ".csv").build().toString())
                .body(csv.toString().getBytes(StandardCharsets.UTF_8));
    }

    /**
     * The Anlagen EÜR of a year: one for all income, or one per tax number when the Finanzamt gave teaching a number
     * of its own (decision 2026-11-14). The §19 threshold stays one for the whole business.
     */
    static List<Business> businesses(List<RevenueRow> rows, SellerProfile seller) {
        String taxNumber = seller == null ? null : seller.taxNumber();
        if (seller == null || !seller.twoTaxNumbers()) {
            return List.of(new Business("", null, taxNumber, totals(rows)));
        }
        return List.of(
                new Business("-teaching", Activity.TEACHING.label(), seller.taxNumberTeaching(),
                        totals(rows.stream().filter(row -> row.activity() == Activity.TEACHING).toList())),
                new Business("-other", Activity.OTHER.label(), taxNumber,
                        totals(rows.stream().filter(row -> row.activity() == Activity.OTHER).toList())));
    }

    /**
     * The totals of the rows. Zeile 12 is their income without VAT, Zeile 13 the part of it outside the §19 threshold;
     * only the §19 category counts.
     */
    static Totals totals(List<RevenueRow> rows) {
        Map<TaxCategory, BigDecimal> byCategory = new EnumMap<>(TaxCategory.class);
        rows.forEach(row -> row.byCategory().forEach((category, amount) -> byCategory.merge(category, amount,
                BigDecimal::add)));
        BigDecimal vatSales = sum(byCategory.entrySet().stream().filter(entry -> entry.getKey().chargesVat())
                .map(Map.Entry::getValue).toList());
        BigDecimal total = sum(List.copyOf(byCategory.values())).subtract(vatSales);
        BigDecimal counted = sum(rows.stream().map(RevenueRow::counted).toList());
        return new Totals(total, total.subtract(counted), counted, vatSales, byCategory);
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
