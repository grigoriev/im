package io.github.grigoriev.im.invoice;

import io.github.grigoriev.im.einvoice.InvoiceFormat;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.format.annotation.DateTimeFormat;

/** The editor of a draft. Quantities and prices stay text, as typed: {@code 1.234,56} or {@code 45,5}. */
public class DraftForm {

    /** German digits with thousands dots and an optional decimal comma: {@code 1.234,56}. */
    private static final Pattern THOUSANDS = Pattern.compile("-?\\d{1,3}(\\.\\d{3})+(,\\d+)?");

    /** Digits with an optional decimal comma: {@code 45,5}. */
    private static final Pattern COMMA = Pattern.compile("-?\\d+(,\\d+)?");

    /** One dot before exactly three digits: {@code 1.500} is 1500 for a German and 1,5 for a keypad. */
    private static final Pattern AMBIGUOUS = Pattern.compile("-?\\d{1,3}\\.\\d{3}");

    /** Digits with a decimal dot, as a Mac keypad sends them. */
    private static final Pattern PLAIN = Pattern.compile("-?\\d+\\.\\d+");

    private Long version;
    private InvoiceFormat format;
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate issueDate;
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate serviceFrom;
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate serviceTo;
    private int paymentDays;
    private String buyerReference;
    private String intro;
    private String closing;
    private String taxNote;
    private List<LineForm> lines = new ArrayList<>();

    /**
     * Returns the form of a stored invoice.
     *
     * @param invoice the invoice
     * @return the form
     */
    public static DraftForm of(Invoice invoice) {
        DraftForm form = new DraftForm();
        form.version = invoice.version();
        form.format = invoice.format();
        form.issueDate = invoice.issueDate();
        form.serviceFrom = invoice.serviceFrom();
        form.serviceTo = invoice.serviceTo();
        form.paymentDays = invoice.paymentDays();
        form.buyerReference = invoice.buyerReference();
        form.intro = invoice.intro();
        form.closing = invoice.closing();
        form.taxNote = invoice.taxNote();
        for (DraftLine line : invoice.lines()) {
            LineForm row = new LineForm();
            row.setText(line.text());
            row.setLumpSum(line.lumpSum());
            row.setQuantity(quantity(line.quantity()));
            row.setPrice(price(line.unitPrice()));
            row.setCategory(line.category());
            form.lines.add(row);
        }
        return form;
    }

    /**
     * Reads an amount as Germans type it. A dot alone counts as decimal point, as a Mac keypad sends it.
     *
     * @param text the entered text
     * @return the amount, or {@code null} if the text is no number
     */
    static BigDecimal parse(String text) {
        String value = text == null ? "" : text.replace(" ", "").replace("€", "").strip();
        if (AMBIGUOUS.matcher(value).matches()) {
            return null;
        }
        if (PLAIN.matcher(value).matches()) {
            return new BigDecimal(value);
        }
        if (THOUSANDS.matcher(value).matches() || COMMA.matcher(value).matches()) {
            return new BigDecimal(value.replace(".", "").replace(",", "."));
        }
        return null;
    }

    /**
     * Tells whether a text reads both as thousands and as decimals, such as {@code 1.500}.
     *
     * @param text the entered text
     * @return {@code true} if the text is ambiguous
     */
    static boolean ambiguous(String text) {
        return text != null && AMBIGUOUS.matcher(text.replace(" ", "").replace("€", "").strip()).matches();
    }

    /**
     * Writes a price for the form, German style: {@code 45,00}.
     *
     * @param amount the amount
     * @return the text
     */
    static String price(BigDecimal amount) {
        return new DecimalFormat("0.00##", DecimalFormatSymbols.getInstance(Locale.GERMANY)).format(amount);
    }

    /**
     * Writes a quantity for the form, German style without trailing zeros: {@code 1,5}.
     *
     * @param quantity the quantity
     * @return the text
     */
    static String quantity(BigDecimal quantity) {
        return new DecimalFormat("0.####", DecimalFormatSymbols.getInstance(Locale.GERMANY)).format(quantity);
    }

    /** @return the version of the draft that the form shows */
    public Long getVersion() {
        return version;
    }

    /** @param version the version of the draft that the form shows */
    public void setVersion(Long version) {
        this.version = version;
    }

    /** @return the e-invoice format */
    public InvoiceFormat getFormat() {
        return format;
    }

    /** @param format the e-invoice format */
    public void setFormat(InvoiceFormat format) {
        this.format = format;
    }

    /** @return the invoice date */
    public LocalDate getIssueDate() {
        return issueDate;
    }

    /** @param issueDate the invoice date */
    public void setIssueDate(LocalDate issueDate) {
        this.issueDate = issueDate;
    }

    /** @return the first day of the service */
    public LocalDate getServiceFrom() {
        return serviceFrom;
    }

    /** @param serviceFrom the first day of the service */
    public void setServiceFrom(LocalDate serviceFrom) {
        this.serviceFrom = serviceFrom;
    }

    /** @return the last day of the service */
    public LocalDate getServiceTo() {
        return serviceTo;
    }

    /** @param serviceTo the last day of the service */
    public void setServiceTo(LocalDate serviceTo) {
        this.serviceTo = serviceTo;
    }

    /** @return the days until payment is due */
    public int getPaymentDays() {
        return paymentDays;
    }

    /** @param paymentDays the days until payment is due */
    public void setPaymentDays(int paymentDays) {
        this.paymentDays = paymentDays;
    }

    /** @return the buyer reference */
    public String getBuyerReference() {
        return buyerReference;
    }

    /** @param buyerReference the buyer reference */
    public void setBuyerReference(String buyerReference) {
        this.buyerReference = buyerReference;
    }

    /** @return the text above the lines */
    public String getIntro() {
        return intro;
    }

    /** @param intro the text above the lines */
    public void setIntro(String intro) {
        this.intro = intro;
    }

    /** @return the text below the lines */
    public String getClosing() {
        return closing;
    }

    /** @param closing the text below the lines */
    public void setClosing(String closing) {
        this.closing = closing;
    }

    /** @return the tax note */
    public String getTaxNote() {
        return taxNote;
    }

    /** @param taxNote the tax note */
    public void setTaxNote(String taxNote) {
        this.taxNote = taxNote;
    }

    /** @return the lines */
    public List<LineForm> getLines() {
        return lines;
    }

    /** @param lines the lines */
    public void setLines(List<LineForm> lines) {
        this.lines = lines;
    }
}
