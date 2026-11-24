package io.github.grigoriev.im.invoice;

import io.github.grigoriev.im.einvoice.InvoiceFormat;
import io.github.grigoriev.im.seller.Activity;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.format.annotation.DateTimeFormat;

/** The editor of a draft. Quantities and prices stay text, as typed: {@code 1.234,56} or {@code 45,5}. */
public class DraftForm {

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
    private String sellerIdentifier;
    private String purchaseOrder;
    private String contract;
    private String intro;
    private String closing;
    private String taxNote;
    private Activity activity;
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
        form.sellerIdentifier = invoice.references().sellerIdentifier();
        form.purchaseOrder = invoice.references().purchaseOrder();
        form.contract = invoice.references().contract();
        form.intro = invoice.intro();
        form.closing = invoice.closing();
        form.taxNote = invoice.taxNote();
        form.activity = invoice.activity();
        form.lines = rows(invoice.lines());
        return form;
    }

    /**
     * Returns the form rows of stored lines.
     *
     * @param lines the lines
     * @return the rows, a new list
     */
    static List<LineForm> rows(List<DraftLine> lines) {
        List<LineForm> rows = new ArrayList<>();
        for (DraftLine line : lines) {
            LineForm row = new LineForm();
            row.setText(line.text());
            row.setLumpSum(line.lumpSum());
            row.setQuantity(quantity(line.quantity()));
            row.setPrice(price(line.unitPrice()));
            // A lump sum shows its amount as stored; a line with quantity shows its total, for a switch to a lump sum.
            row.setAmount(line.lumpSum() ? price(line.unitPrice())
                    : price(line.quantity().multiply(line.unitPrice()).setScale(2, RoundingMode.HALF_UP)));
            row.setShownAsLumpSum(line.lumpSum());
            row.setShownAmount(row.getAmount());
            row.setCategory(line.category());
            rows.add(row);
        }
        return rows;
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

    /** @return the Geschäftspartnernummer */
    public String getSellerIdentifier() {
        return sellerIdentifier;
    }

    /** @param sellerIdentifier the Geschäftspartnernummer */
    public void setSellerIdentifier(String sellerIdentifier) {
        this.sellerIdentifier = sellerIdentifier;
    }

    /** @return the Bestellnummer */
    public String getPurchaseOrder() {
        return purchaseOrder;
    }

    /** @param purchaseOrder the Bestellnummer */
    public void setPurchaseOrder(String purchaseOrder) {
        this.purchaseOrder = purchaseOrder;
    }

    /** @return the contract */
    public String getContract() {
        return contract;
    }

    /** @param contract the contract */
    public void setContract(String contract) {
        this.contract = contract;
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

    /** @return the business with two tax numbers, {@code null} to follow the lines */
    public Activity getActivity() {
        return activity;
    }

    /** @param activity the business with two tax numbers, {@code null} to follow the lines */
    public void setActivity(Activity activity) {
        this.activity = activity;
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
