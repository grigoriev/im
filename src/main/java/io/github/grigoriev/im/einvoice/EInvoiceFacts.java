package io.github.grigoriev.im.einvoice;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

/**
 * The facts of an invoice that a reader of the XML relies on: number, dates, amount, account and parties. The check of
 * an issued invoice compares the facts of its XML with those of its snapshot, so it can name what a change altered.
 *
 * @param number the invoice number
 * @param issueDate the invoice date
 * @param dueDate the due date, may be {@code null}
 * @param total the amount with VAT
 * @param iban the IBAN, without spaces
 * @param seller the name of the seller
 * @param buyer the name of the buyer
 */
public record EInvoiceFacts(String number, LocalDate issueDate, LocalDate dueDate, BigDecimal total, String iban,
        String seller, String buyer) {

    private static final DateTimeFormatter CII_DATE = DateTimeFormatter.BASIC_ISO_DATE;
    private static final DateTimeFormatter GERMAN = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    /**
     * A fact that differs.
     *
     * @param field the name of the fact
     * @param issued its value at the issue
     * @param file its value in the file
     */
    public record Difference(String field, String issued, String file) {
    }

    /**
     * Returns the facts of an invoice as it was issued.
     *
     * @param document the snapshot
     * @return its facts
     */
    public static EInvoiceFacts of(InvoiceDocument document) {
        return new EInvoiceFacts(document.number(), document.issueDate(), document.dueDate(), document.total(),
                compact(document.iban()), document.seller().name(), document.buyer().name());
    }

    /**
     * Reads the facts of a CII XML: ZUGFeRD and XRechnung. The parser refuses a DOCTYPE, so the file can neither reach
     * out nor expand entities. The paths name local names: the prefixes of the file do not matter.
     *
     * @param xml the XML
     * @return its facts; a missing one is {@code null}
     * @throws EInvoiceException if it is no XML
     */
    public static EInvoiceFacts read(byte[] xml) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            Document document = factory.newDocumentBuilder().parse(new ByteArrayInputStream(xml));
            XPath xpath = XPathFactory.newInstance().newXPath();
            String total = text(xpath, document, "//*[local-name()='SpecifiedTradeSettlementHeaderMonetarySummation']"
                    + "/*[local-name()='GrandTotalAmount']");
            String iban = text(xpath, document, "//*[local-name()='PayeePartyCreditorFinancialAccount']"
                    + "/*[local-name()='IBANID']");
            return new EInvoiceFacts(
                    text(xpath, document, "/*/*[local-name()='ExchangedDocument']/*[local-name()='ID']"),
                    date(text(xpath, document, "/*/*[local-name()='ExchangedDocument']/*[local-name()='IssueDateTime']"
                            + "/*[local-name()='DateTimeString']")),
                    date(text(xpath, document, "//*[local-name()='SpecifiedTradePaymentTerms']"
                            + "/*[local-name()='DueDateDateTime']/*[local-name()='DateTimeString']")),
                    amount(total), iban == null ? null : compact(iban),
                    text(xpath, document, "//*[local-name()='SellerTradeParty']/*[local-name()='Name']"),
                    text(xpath, document, "//*[local-name()='BuyerTradeParty']/*[local-name()='Name']"));
        } catch (ParserConfigurationException | SAXException | IOException | XPathExpressionException e) {
            throw new EInvoiceException("The XML cannot be read", e);
        }
    }

    /**
     * Compares the facts at the issue with those of a file.
     *
     * @param file the facts of the file
     * @return the facts that differ, in German
     */
    public List<Difference> differences(EInvoiceFacts file) {
        List<Difference> differences = new ArrayList<>();
        compare(differences, "Rechnungsnummer", number, file.number);
        compare(differences, "Rechnungsdatum", german(issueDate), german(file.issueDate));
        compare(differences, "Fällig am", german(dueDate), german(file.dueDate));
        if (file.total == null || total.compareTo(file.total) != 0) {
            differences.add(new Difference("Gesamtbetrag", euro(total), euro(file.total)));
        }
        compare(differences, "IBAN", iban, file.iban);
        compare(differences, "Verkäufer", seller, file.seller);
        compare(differences, "Käufer", buyer, file.buyer);
        return differences;
    }

    private static void compare(List<Difference> differences, String field, String issued, String file) {
        if (!Objects.equals(issued, file)) {
            differences.add(new Difference(field, issued, file == null ? "fehlt" : file));
        }
    }

    private static String text(XPath xpath, Document document, String path) throws XPathExpressionException {
        String text = xpath.evaluate(path, document).strip();
        return text.isEmpty() ? null : text;
    }

    private static LocalDate date(String text) {
        try {
            return text == null ? null : LocalDate.parse(text, CII_DATE);
        } catch (DateTimeParseException _) {
            return null;
        }
    }

    private static BigDecimal amount(String text) {
        try {
            return text == null ? null : new BigDecimal(text);
        } catch (NumberFormatException _) {
            return null;
        }
    }

    private static String german(LocalDate date) {
        return date == null ? null : GERMAN.format(date);
    }

    private static String euro(BigDecimal amount) {
        return amount == null ? "fehlt" : String.format(Locale.GERMANY, "%,.2f €", amount);
    }

    private static String compact(String iban) {
        return iban.replace(" ", "");
    }
}
