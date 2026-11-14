package io.github.grigoriev.im.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.github.grigoriev.im.einvoice.InvoiceFormat;
import io.github.grigoriev.im.einvoice.SampleInvoices;
import io.github.grigoriev.im.invoice.DocumentStore;
import io.github.grigoriev.im.invoice.Invoice;
import io.github.grigoriev.im.invoice.InvoiceService;
import io.github.grigoriev.im.invoice.InvoiceStatus;
import io.github.grigoriev.im.invoice.PaymentService;
import io.github.grigoriev.im.threshold.Threshold;
import io.github.grigoriev.im.threshold.ThresholdService;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.ui.ExtendedModelMap;

class HomeControllerTest {

    private static final LocalDate TODAY = LocalDate.of(2027, 4, 20);

    private final InvoiceService invoices = mock(InvoiceService.class);
    private final PaymentService payments = mock(PaymentService.class);
    private final DocumentStore documents = mock(DocumentStore.class);
    private final ThresholdService threshold = mock(ThresholdService.class);
    private final HomeController controller = new HomeController(invoices, payments, documents, threshold);

    /** An issued invoice dated {@code daysAgo} days ago, due after 14 days. */
    private static Invoice invoice(long id, InvoiceStatus status, int daysAgo) {
        LocalDate date = TODAY.minusDays(daysAgo);
        return new Invoice(id, status, 2L, status == InvoiceStatus.DRAFT ? null : "2027-0" + id,
                InvoiceFormat.ZUGFERD_EN16931, date, date, date, 14, null, null, null, null, List.of(), Instant.EPOCH,
                Instant.EPOCH, 1L, status == InvoiceStatus.DRAFT ? null : Instant.EPOCH,
                status == InvoiceStatus.DRAFT ? null : "{}");
    }

    private void open(Invoice invoice, String amount) {
        when(payments.summary(invoice)).thenReturn(new PaymentService.Payments(List.of(), BigDecimal.ZERO,
                new BigDecimal(amount)));
    }

    @Test
    void showsOpenAndOverdueInvoicesAndTheThreshold() {
        Invoice due = invoice(1, InvoiceStatus.SENT, 20);
        Invoice fresh = invoice(2, InvoiceStatus.ISSUED, 3);
        Invoice dueToday = invoice(3, InvoiceStatus.ISSUED, 14);
        Invoice settled = invoice(4, InvoiceStatus.SENT, 30);
        Invoice storno = invoice(5, InvoiceStatus.ISSUED, 30);
        when(invoices.list()).thenReturn(List.of(due, fresh, dueToday, settled, storno, invoice(6, InvoiceStatus.PAID, 40),
                invoice(7, InvoiceStatus.CANCELLED, 40), invoice(8, InvoiceStatus.DRAFT, 40)));
        open(due, "480.00");
        open(fresh, "100.00");
        open(dueToday, "20.00");
        open(settled, "0.00");
        when(invoices.cancelled(storno)).thenReturn(Optional.of(due));
        when(documents.restore("{}")).thenReturn(SampleInvoices.reading(InvoiceFormat.ZUGFERD_EN16931));
        when(threshold.today()).thenReturn(TODAY);
        Threshold current = new Threshold(2027, BigDecimal.ONE, true, BigDecimal.TEN, BigDecimal.ZERO, BigDecimal.TEN,
                BigDecimal.TEN);
        when(threshold.current()).thenReturn(current);

        ExtendedModelMap model = new ExtendedModelMap();
        assertThat(controller.home(model)).isEqualTo("home");
        @SuppressWarnings("unchecked")
        List<HomeController.Open> open = (List<HomeController.Open>) model.get("open");
        assertThat(open).extracting(item -> item.invoice().id()).containsExactly(1L, 2L, 3L);
        assertThat(open).extracting(HomeController.Open::customer).containsOnly("Verlag am Fluss GmbH");
        assertThat(model.get("openSum")).isEqualTo(new BigDecimal("600.00"));
        @SuppressWarnings("unchecked")
        List<HomeController.Open> overdue = (List<HomeController.Open>) model.get("overdue");
        assertThat(overdue).as("due today is not overdue yet").extracting(item -> item.invoice().id()).containsExactly(1L);
        assertThat(model.get("overdueSum")).isEqualTo(new BigDecimal("480.00"));
        assertThat(model.get("threshold")).isEqualTo(current);
        assertThat(model.get("today")).isEqualTo(TODAY);
    }
}
