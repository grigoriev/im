package io.github.grigoriev.im.web;

import io.github.grigoriev.im.invoice.DocumentStore;
import io.github.grigoriev.im.invoice.Invoice;
import io.github.grigoriev.im.invoice.InvoiceService;
import io.github.grigoriev.im.invoice.InvoiceStatus;
import io.github.grigoriev.im.invoice.PaymentService;
import io.github.grigoriev.im.threshold.ThresholdService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/** Serves the start page "Übersicht": open and overdue invoices, the money received and the §19 threshold. */
@Controller
public class HomeController {

    /**
     * An invoice that waits for its money.
     *
     * @param invoice the invoice
     * @param customer the buyer of the snapshot
     * @param open the amount still open
     * @param overdue whether its due date has passed
     */
    public record Open(Invoice invoice, String customer, BigDecimal open, boolean overdue) {
    }

    private final InvoiceService invoices;
    private final PaymentService payments;
    private final DocumentStore documents;
    private final ThresholdService threshold;

    /**
     * Creates the controller.
     *
     * @param invoices the invoices
     * @param payments what an invoice still waits for
     * @param documents the buyer of an issued invoice
     * @param threshold the §19 revenue
     */
    public HomeController(InvoiceService invoices, PaymentService payments, DocumentStore documents,
            ThresholdService threshold) {
        this.invoices = invoices;
        this.payments = payments;
        this.documents = documents;
        this.threshold = threshold;
    }

    /**
     * Shows the start page.
     *
     * @param model the page model
     * @return the template name
     */
    @GetMapping("/")
    public String home(Model model) {
        LocalDate today = threshold.today();
        List<Open> open = new ArrayList<>();
        for (Invoice invoice : invoices.list()) {
            boolean waiting = invoice.status() == InvoiceStatus.ISSUED || invoice.status() == InvoiceStatus.SENT;
            if (waiting && invoices.cancelled(invoice).isEmpty()) {
                BigDecimal amount = payments.summary(invoice).open();
                if (amount.signum() > 0) {
                    open.add(new Open(invoice, documents.restore(invoice.snapshot()).buyer().name(), amount,
                            invoice.dueDate().isBefore(today)));
                }
            }
        }
        List<Open> overdue = open.stream().filter(Open::overdue).toList();
        model.addAttribute("today", today);
        model.addAttribute("open", open);
        model.addAttribute("openSum", open.stream().map(Open::open).reduce(BigDecimal.ZERO, BigDecimal::add));
        model.addAttribute("overdue", overdue);
        model.addAttribute("overdueSum", overdue.stream().map(Open::open).reduce(BigDecimal.ZERO, BigDecimal::add));
        model.addAttribute("threshold", threshold.current());
        return "home";
    }
}
