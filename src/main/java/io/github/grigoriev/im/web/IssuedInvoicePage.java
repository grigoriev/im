package io.github.grigoriev.im.web;

import io.github.grigoriev.im.audit.AuditService;
import io.github.grigoriev.im.invoice.DispatchChannel;
import io.github.grigoriev.im.invoice.DispatchService;
import io.github.grigoriev.im.invoice.DocumentStore;
import io.github.grigoriev.im.invoice.Invoice;
import io.github.grigoriev.im.invoice.PaymentService;
import java.util.Arrays;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.ui.Model;

/** Fills the page of an issued invoice: documents, history, dispatch and payments. */
@Component
public class IssuedInvoicePage {

    /** The Lucide icon of an entry in the history of an invoice, by action. */
    static final Map<String, String> HISTORY_ICONS = Map.of("DRAFT_CREATED", "file-plus", "INVOICE_ISSUED", "stamp",
            "INVOICE_SENT", "send", "PAYMENT_RECORDED", "banknote");

    private final DocumentStore documents;
    private final AuditService audit;
    private final DispatchService dispatches;
    private final PaymentService payments;

    /**
     * Creates the page.
     *
     * @param documents the documents of an issued invoice
     * @param audit the history of an invoice
     * @param dispatches today, the default day of a dispatch
     * @param payments the payments and the open amount
     */
    public IssuedInvoicePage(DocumentStore documents, AuditService audit, DispatchService dispatches,
            PaymentService payments) {
        this.documents = documents;
        this.audit = audit;
        this.dispatches = dispatches;
        this.payments = payments;
    }

    /**
     * Adds what the page of an issued invoice shows.
     *
     * @param model the page model
     * @param invoice the issued invoice
     */
    void fill(Model model, Invoice invoice) {
        model.addAttribute("documents", documents.list(invoice.id()));
        model.addAttribute("history", audit.history(invoice.id()));
        model.addAttribute("icons", HISTORY_ICONS);
        model.addAttribute("today", dispatches.today());
        model.addAttribute("channels", Arrays.stream(DispatchChannel.values()).filter(DispatchChannel::isManual).toList());
        model.addAttribute("payments", payments.summary(invoice));
    }
}
