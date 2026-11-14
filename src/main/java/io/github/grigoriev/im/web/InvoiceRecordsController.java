package io.github.grigoriev.im.web;

import io.github.grigoriev.im.invoice.DispatchForm;
import io.github.grigoriev.im.invoice.DispatchService;
import io.github.grigoriev.im.invoice.InvoiceException;
import io.github.grigoriev.im.invoice.InvoiceStatus;
import io.github.grigoriev.im.invoice.PaymentForm;
import io.github.grigoriev.im.invoice.PaymentService;
import io.github.grigoriev.im.mail.MailService;
import io.github.grigoriev.im.mail.SendForm;
import org.springframework.stereotype.Controller;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** Records what happens to an issued invoice: its e-mail, its dispatches and its payments. Both roles use it. */
@Controller
@RequestMapping("/rechnungen/{id}")
public class InvoiceRecordsController {

    private static final String SUCCESS = "success";
    private static final String ERROR = "error";
    private static final String TO_INVOICE = "redirect:/rechnungen/";
    private static final String BROKEN = "Bitte gültige Daten eingeben, zum Beispiel ein Datum.";

    private final DispatchService dispatches;
    private final PaymentService payments;
    private final MailService mail;

    /**
     * Creates the controller.
     *
     * @param dispatches records a dispatch
     * @param payments records a payment
     * @param mail sends an invoice by e-mail
     */
    public InvoiceRecordsController(DispatchService dispatches, PaymentService payments, MailService mail) {
        this.dispatches = dispatches;
        this.payments = payments;
        this.mail = mail;
    }

    /**
     * Sends an issued invoice by e-mail and records the dispatch.
     *
     * @param id the ID
     * @param form the entered values
     * @param flash messages for the next page
     * @return a redirect to the invoice
     */
    @PostMapping("/senden")
    public String send(@PathVariable long id, @ModelAttribute SendForm form, RedirectAttributes flash) {
        try {
            mail.send(id, form);
            flash.addFlashAttribute(SUCCESS, "E-Mail an " + form.getTo().strip() + " gesendet.");
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
    @PostMapping("/versand")
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
    @PostMapping("/zahlungen")
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
