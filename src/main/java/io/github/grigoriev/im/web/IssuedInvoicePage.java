package io.github.grigoriev.im.web;

import io.github.grigoriev.im.audit.AuditService;
import io.github.grigoriev.im.invoice.DispatchChannel;
import io.github.grigoriev.im.invoice.DispatchService;
import io.github.grigoriev.im.invoice.DocumentStore;
import io.github.grigoriev.im.invoice.Invoice;
import io.github.grigoriev.im.invoice.InvoiceIntegrity;
import io.github.grigoriev.im.invoice.InvoiceService;
import io.github.grigoriev.im.invoice.OriginalStore;
import io.github.grigoriev.im.invoice.PaymentService;
import io.github.grigoriev.im.invoice.ReminderService;
import io.github.grigoriev.im.mail.MailService;
import java.util.Arrays;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.ui.Model;

/**
 * Fills the page of an issued invoice: the check of its documents, documents, history, e-mail, dispatch, payments and
 * its Stornorechnung.
 */
@Component
public class IssuedInvoicePage {

    /** The Lucide icon of an entry in the history of an invoice, by action. */
    static final Map<String, String> HISTORY_ICONS = Map.of("DRAFT_CREATED", "file-plus", "INVOICE_ISSUED", "stamp",
            "INVOICE_REGISTERED", "file-input", "ORIGINAL_ADDED", "file-plus-2",
            "INVOICE_SENT", "send", "PAYMENT_RECORDED", "banknote", "INVOICE_CANCELLED", "undo-2", "REMINDER_RECORDED", "bell",
            "INTEGRITY_FAILED", "shield-alert");

    /** The flash attribute that carries a mail form back after a problem. */
    static final String MAIL_FORM = "mailForm";
    /** The flash attribute that carries a reminder form back after a problem. */
    static final String REMINDER_FORM = "reminderForm";

    private final DocumentStore documents;
    private final OriginalStore originals;
    private final AuditService audit;
    private final DispatchService dispatches;
    private final PaymentService payments;
    private final MailService mail;
    private final InvoiceService invoices;
    private final ReminderService reminders;
    private final InvoiceIntegrity integrity;

    /**
     * Creates the page.
     *
     * @param documents the documents of an issued invoice
     * @param originals the original files of an external invoice
     * @param audit the history of an invoice
     * @param dispatches today, the default day of a dispatch
     * @param payments the payments and the open amount
     * @param mail the e-mail form, if sending is set up
     * @param invoices the Stornorechnung of an invoice
     * @param reminders the payment reminders
     * @param integrity checks the documents and the snapshot
     */
    public IssuedInvoicePage(DocumentStore documents, OriginalStore originals, AuditService audit,
            DispatchService dispatches, PaymentService payments, MailService mail, InvoiceService invoices,
            ReminderService reminders, InvoiceIntegrity integrity) {
        this.documents = documents;
        this.originals = originals;
        this.audit = audit;
        this.dispatches = dispatches;
        this.payments = payments;
        this.mail = mail;
        this.invoices = invoices;
        this.reminders = reminders;
        this.integrity = integrity;
    }

    /**
     * Adds what the page of an issued invoice shows.
     *
     * @param model the page model
     * @param invoice the issued invoice
     */
    void fill(Model model, Invoice invoice) {
        model.addAttribute("integrity", integrity.check(invoice));
        model.addAttribute("documents", documents.list(invoice.id()));
        model.addAttribute("originals", originals.list(invoice.id()));
        model.addAttribute("history", audit.history(invoice.id()));
        model.addAttribute("icons", HISTORY_ICONS);
        model.addAttribute("today", dispatches.today());
        model.addAttribute("channels", Arrays.stream(DispatchChannel.values()).filter(DispatchChannel::isManual).toList());
        model.addAttribute("payments", payments.summary(invoice));
        model.addAttribute("cancellation", invoices.cancellation(invoice).orElse(null));
        model.addAttribute("mailConfigured", mail.configured());
        model.addAttribute("external", invoice.format().isExternal());
        String buyerEmail = documents.verifiedSnapshot(invoice).buyer().email();
        if (model.containsAttribute(MAIL_FORM)) {
            model.addAttribute("mail", model.getAttribute(MAIL_FORM));
        } else if (mail.configured()) {
            model.addAttribute("mail", mail.form(invoice, buyerEmail));
        }
        model.addAttribute("reminders", reminders.list(invoice.id()));
        boolean remindable = reminders.isRemindable(invoice.id());
        model.addAttribute("remindable", remindable);
        model.addAttribute("overdue", reminders.overdue(invoice));
        if (model.containsAttribute(REMINDER_FORM)) {
            model.addAttribute("reminderMail", model.getAttribute(REMINDER_FORM));
        } else if (remindable && mail.configured()) {
            model.addAttribute("reminderMail", mail.reminderForm(invoice, buyerEmail));
        }
    }
}
