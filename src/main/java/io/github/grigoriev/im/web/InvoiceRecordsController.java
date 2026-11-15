package io.github.grigoriev.im.web;

import io.github.grigoriev.im.invoice.DispatchForm;
import io.github.grigoriev.im.invoice.DispatchService;
import io.github.grigoriev.im.invoice.InvoiceException;
import io.github.grigoriev.im.invoice.InvoiceStatus;
import io.github.grigoriev.im.invoice.PaymentForm;
import io.github.grigoriev.im.invoice.PaymentService;
import io.github.grigoriev.im.invoice.ReminderForm;
import io.github.grigoriev.im.invoice.ReminderService;
import io.github.grigoriev.im.mail.MailService;
import io.github.grigoriev.im.mail.SendForm;
import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** Records what happens to an issued invoice: its e-mail, its dispatches and its payments. Both roles use it. */
@Controller
@RequestMapping("/invoices/{id}")
public class InvoiceRecordsController {

    private static final String SUCCESS = "success";
    private static final String ERROR = "error";
    private static final String TO_INVOICE = "redirect:/invoices/";
    private static final String BROKEN = "Bitte gültige Daten eingeben, zum Beispiel ein Datum.";
    /** The policy of the preview: inline styles, nothing else; no script, no form, no frame. */
    static final String PREVIEW_POLICY = "default-src 'none'; style-src 'unsafe-inline'; base-uri 'none'; "
            + "form-action 'none'; frame-ancestors 'none'";

    private final DispatchService dispatches;
    private final PaymentService payments;
    private final MailService mail;
    private final ReminderService reminders;

    /**
     * Creates the controller.
     *
     * @param dispatches records a dispatch
     * @param payments records a payment
     * @param mail sends an invoice or a payment reminder by e-mail
     * @param reminders records a payment reminder given by hand
     */
    public InvoiceRecordsController(DispatchService dispatches, PaymentService payments, MailService mail,
            ReminderService reminders) {
        this.dispatches = dispatches;
        this.payments = payments;
        this.mail = mail;
        this.reminders = reminders;
    }

    /**
     * Sends an issued invoice by e-mail and records the dispatch. On a problem the next page shows the form as typed.
     *
     * @param id the ID
     * @param form the entered values
     * @param flash messages for the next page
     * @return a redirect to the invoice
     */
    @PostMapping("/send")
    public String send(@PathVariable long id, @ModelAttribute SendForm form, RedirectAttributes flash) {
        try {
            mail.send(id, form);
            flash.addFlashAttribute(SUCCESS, "E-Mail an " + form.getTo().strip() + " gesendet.");
        } catch (InvoiceException e) {
            flash.addFlashAttribute(ERROR, e.getMessage());
            flash.addFlashAttribute(IssuedInvoicePage.MAIL_FORM, form);
        }
        return TO_INVOICE + id;
    }

    /**
     * Shows the e-mail as typed, as the customer sees it, in a tab of its own. The HTML has inline styles, which the
     * policy of the app forbids: this page gets a policy of its own that allows them and nothing else.
     *
     * @param id the ID
     * @param form the entered values
     * @return the HTML of the e-mail
     */
    @PostMapping("/send/preview")
    public ResponseEntity<String> preview(@PathVariable long id, @ModelAttribute SendForm form) {
        try {
            return ResponseEntity.ok().contentType(new MediaType(MediaType.TEXT_HTML, StandardCharsets.UTF_8))
                    .header("Content-Security-Policy", PREVIEW_POLICY).body(mail.preview(id, form));
        } catch (InvoiceException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage(), e);
        }
    }

    /**
     * Sends a payment reminder by e-mail and records it. On a problem the next page shows the form as typed.
     *
     * @param id the ID
     * @param form the entered values
     * @param flash messages for the next page
     * @return a redirect to the invoice
     */
    @PostMapping("/remind")
    public String remind(@PathVariable long id, @ModelAttribute SendForm form, RedirectAttributes flash) {
        try {
            mail.sendReminder(id, form);
            flash.addFlashAttribute(SUCCESS, "Zahlungserinnerung an " + form.getTo().strip() + " gesendet.");
        } catch (InvoiceException e) {
            flash.addFlashAttribute(ERROR, e.getMessage());
            flash.addFlashAttribute(IssuedInvoicePage.REMINDER_FORM, form);
        }
        return TO_INVOICE + id;
    }

    /**
     * Shows the payment reminder as typed, as the customer sees it, under the policy of the e-mail preview.
     *
     * @param id the ID
     * @param form the entered values
     * @return the HTML of the e-mail
     */
    @PostMapping("/remind/preview")
    public ResponseEntity<String> remindPreview(@PathVariable long id, @ModelAttribute SendForm form) {
        try {
            return ResponseEntity.ok().contentType(new MediaType(MediaType.TEXT_HTML, StandardCharsets.UTF_8))
                    .header("Content-Security-Policy", PREVIEW_POLICY).body(mail.previewReminder(id, form));
        } catch (InvoiceException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage(), e);
        }
    }

    /**
     * Records a payment reminder the user gave outside the app.
     *
     * @param id the ID
     * @param form the entered values
     * @param binding problems of the binding, for example a broken date
     * @param flash messages for the next page
     * @return a redirect to the invoice
     */
    @PostMapping("/reminders")
    public String reminder(@PathVariable long id, @ModelAttribute ReminderForm form, BindingResult binding,
            RedirectAttributes flash) {
        if (binding.hasErrors()) {
            flash.addFlashAttribute(ERROR, BROKEN);
            return TO_INVOICE + id;
        }
        try {
            reminders.recordManual(id, form);
            flash.addFlashAttribute(SUCCESS, "Erinnerung vermerkt.");
        } catch (InvoiceException e) {
            flash.addFlashAttribute(ERROR, e.getMessage());
        }
        return TO_INVOICE + id;
    }

    /**
     * Records a dispatch the user did outside the app.
     *
     * @param id the ID
     * @param form the entered values
     * @param binding problems of the binding, for example a broken date
     * @param flash messages for the next page
     * @return a redirect to the invoice
     */
    @PostMapping("/dispatches")
    public String dispatch(@PathVariable long id, @ModelAttribute DispatchForm form, BindingResult binding,
            RedirectAttributes flash) {
        if (binding.hasErrors()) {
            flash.addFlashAttribute(ERROR, BROKEN);
            return TO_INVOICE + id;
        }
        try {
            dispatches.recordManual(id, form);
            flash.addFlashAttribute(SUCCESS, "Versand vermerkt.");
        } catch (InvoiceException e) {
            flash.addFlashAttribute(ERROR, e.getMessage());
        }
        return TO_INVOICE + id;
    }

    /**
     * Records a payment or, with a negative amount, a refund.
     *
     * @param id the ID
     * @param form the entered values
     * @param binding problems of the binding, for example a broken date
     * @param flash messages for the next page
     * @return a redirect to the invoice
     */
    @PostMapping("/payments")
    public String payment(@PathVariable long id, @ModelAttribute PaymentForm form, BindingResult binding,
            RedirectAttributes flash) {
        if (binding.hasErrors()) {
            flash.addFlashAttribute(ERROR, BROKEN);
            return TO_INVOICE + id;
        }
        try {
            InvoiceStatus status = payments.add(id, form);
            flash.addFlashAttribute(SUCCESS, status == InvoiceStatus.PAID ? "Zahlung erfasst. Die Rechnung ist bezahlt."
                    : "Zahlung erfasst.");
        } catch (InvoiceException e) {
            flash.addFlashAttribute(ERROR, e.getMessage());
        }
        return TO_INVOICE + id;
    }
}
