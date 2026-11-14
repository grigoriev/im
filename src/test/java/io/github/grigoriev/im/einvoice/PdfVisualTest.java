package io.github.grigoriev.im.einvoice;

import static io.github.grigoriev.im.einvoice.SampleInvoices.DUTCH;
import static io.github.grigoriev.im.einvoice.SampleInvoices.PUBLISHER;
import static io.github.grigoriev.im.einvoice.SampleInvoices.SCHOOL;
import static io.github.grigoriev.im.einvoice.SampleInvoices.SELLER_EU;
import static io.github.grigoriev.im.einvoice.SampleInvoices.invoice;
import static io.github.grigoriev.im.einvoice.SampleInvoices.line;
import static org.assertj.core.api.Assertions.assertThat;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import javax.imageio.ImageIO;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Compares every page of the invoice PDF with a baseline image, see section 10 of {@code docs/PLAN.md}.
 *
 * <p>Update the baselines with {@code mvn test -Dtest=PdfVisualTest -Dvisual.update=true}. Commit them on
 * purpose. On a difference the actual and the diff image land in {@code target/visual-diff}.
 */
class PdfVisualTest {

    private static final Path BASELINES = Path.of("src/test/resources/visual/pdf");
    private static final Path DIFFS = Path.of("target/visual-diff/pdf");
    private static final float DPI = 72;
    private static final int CHANNEL_TOLERANCE = 32;
    private static final double MAX_DIFFERING_PIXELS = 0.001;

    private final InvoicePdfRenderer renderer = new InvoicePdfRenderer(SampleInvoices.templates());

    /** A seller with a long name and a long e-mail address: the footer must wrap them, never print over the page number. */
    private static final Party LONG_CONTACT = new Party("Gemeinnützige Gesellschaft für Sprachförderung mbH",
            "Hauptstraße 123", "80331", "München", "DE", "rechnungswesen.sprachfoerderung@gemeinnuetzige-gesellschaft.example",
            "+49 89 1234567", "Erika Mustermann", "143/123/45678", "DE123456789");

    /** The invoice with another IBAN, for example the 31 characters of Malta, the longest in SEPA. */
    private static InvoiceDocument withIban(InvoiceDocument d, String iban) {
        return new InvoiceDocument(d.number(), d.issueDate(), d.serviceFrom(), d.serviceTo(), d.dueDate(), d.seller(),
                d.buyer(), d.buyerReference(), iban, d.bic(), d.intro(), d.closing(), d.taxNote(), d.format(), d.lines());
    }

    static Stream<Arguments> layouts() {
        List<InvoiceLine> many = new ArrayList<>();
        IntStream.rangeClosed(1, 40).forEach(n -> many.add(
                line("Unterrichtseinheit " + n + ", Deutsch B2, Grammatik und Konversation", "1", "45.00",
                        TaxCategory.EXEMPT_4_21)));
        return Stream.of(
                Arguments.of("one-line", invoice(PUBLISHER, null, InvoiceFormat.ZUGFERD_EN16931,
                        SampleInvoices.lump("Lesung aus „Der stille Hafen“ am 16.03.2027, 90 Min.", "400.00",
                                TaxCategory.KLEINUNTERNEHMER))),
                Arguments.of("lump-sums", SampleInvoices.reading(InvoiceFormat.ZUGFERD_EN16931)),
                Arguments.of("sale-19-and-7", SampleInvoices.sale(InvoiceFormat.ZUGFERD_EN16931)),
                Arguments.of("cancellation", SampleInvoices.cancellation(InvoiceFormat.ZUGFERD_EN16931)),
                Arguments.of("page-break", invoice(SCHOOL, null, InvoiceFormat.ZUGFERD_EN16931,
                        many.toArray(InvoiceLine[]::new))),
                Arguments.of("mixed-categories", invoice(SCHOOL, null, InvoiceFormat.ZUGFERD_EN16931,
                        line("Unterricht Deutsch B2, je 45 Min.", "16", "45.00", TaxCategory.EXEMPT_4_21),
                        SampleInvoices.lump("Lesung aus „Der stille Hafen“", "400.00", TaxCategory.KLEINUNTERNEHMER))),
                Arguments.of("xrechnung-buyer-reference", invoice(SCHOOL, "04011000-12345-67", InvoiceFormat.XRECHNUNG,
                        line("Integrationskurs Modul 4, je 45 Min.", "24", "41.00", TaxCategory.EXEMPT_4_21))),
                Arguments.of("long-contact", invoice(LONG_CONTACT, SCHOOL, null, InvoiceFormat.ZUGFERD_EN16931,
                        many.subList(0, 30).toArray(InvoiceLine[]::new))),
                Arguments.of("long-iban", withIban(SampleInvoices.reading(InvoiceFormat.ZUGFERD_EN16931),
                        "MT84MALT011000012345MTLCAST001S")),
                Arguments.of("reverse-charge", invoice(SELLER_EU, DUTCH, null, InvoiceFormat.ZUGFERD_EN16931,
                        line("Online-Workshop „Deutsch im Beruf“, 3 Std.", "1", "600.00",
                                TaxCategory.REVERSE_CHARGE_EU))));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("layouts")
    void matchesTheBaseline(String name, InvoiceDocument document) throws IOException {
        List<BufferedImage> pages = render(renderer.render(document));
        if (name.equals("page-break")) {
            assertThat(pages).hasSizeGreaterThan(1);
        }
        for (int page = 0; page < pages.size(); page++) {
            compare(name + "-p" + (page + 1), pages.get(page));
        }
        assertThat(Files.exists(BASELINES.resolve(name + "-p" + (pages.size() + 1) + ".png")))
                .as("baseline for a page that no longer exists").isFalse();
    }

    private static List<BufferedImage> render(byte[] pdf) throws IOException {
        List<BufferedImage> pages = new ArrayList<>();
        try (PDDocument document = Loader.loadPDF(pdf)) {
            PDFRenderer renderer = new PDFRenderer(document);
            for (int page = 0; page < document.getNumberOfPages(); page++) {
                pages.add(renderer.renderImageWithDPI(page, DPI, ImageType.RGB));
            }
        }
        return pages;
    }

    private static void compare(String name, BufferedImage actual) throws IOException {
        Path baseline = BASELINES.resolve(name + ".png");
        if (Boolean.getBoolean("visual.update")) {
            Files.createDirectories(BASELINES);
            ImageIO.write(actual, "png", baseline.toFile());
            return;
        }
        assertThat(baseline).as("baseline %s, create it with -Dvisual.update=true", baseline).exists();
        BufferedImage expected = ImageIO.read(baseline.toFile());
        assertThat(actual.getWidth()).as("width of %s", name).isEqualTo(expected.getWidth());
        assertThat(actual.getHeight()).as("height of %s", name).isEqualTo(expected.getHeight());
        BufferedImage diff = new BufferedImage(actual.getWidth(), actual.getHeight(), BufferedImage.TYPE_INT_RGB);
        long differing = 0;
        for (int y = 0; y < actual.getHeight(); y++) {
            for (int x = 0; x < actual.getWidth(); x++) {
                boolean differs = differs(actual.getRGB(x, y), expected.getRGB(x, y));
                differing += differs ? 1 : 0;
                diff.setRGB(x, y, differs ? 0xFF0000 : (expected.getRGB(x, y) & 0xFFFFFF) | 0xE0E0E0);
            }
        }
        double ratio = (double) differing / ((long) actual.getWidth() * actual.getHeight());
        if (ratio > MAX_DIFFERING_PIXELS) {
            Files.createDirectories(DIFFS);
            ImageIO.write(actual, "png", DIFFS.resolve(name + "-actual.png").toFile());
            ImageIO.write(diff, "png", DIFFS.resolve(name + "-diff.png").toFile());
        }
        assertThat(ratio).as("share of differing pixels in %s, see %s", name, DIFFS).isLessThanOrEqualTo(MAX_DIFFERING_PIXELS);
    }

    private static boolean differs(int a, int b) {
        for (int shift = 0; shift <= 16; shift += 8) {
            if (Math.abs(((a >> shift) & 0xFF) - ((b >> shift) & 0xFF)) > CHANNEL_TOLERANCE) {
                return true;
            }
        }
        return false;
    }
}
