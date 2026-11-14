package io.github.grigoriev.im.invoice;

import io.github.grigoriev.im.einvoice.InvoiceFormat;
import java.util.ArrayList;
import java.util.List;

/** The editor of a template. Lines read themselves, as in a draft. */
public class TemplateForm {

    private Long version;
    private String name;
    private InvoiceFormat format;
    private int paymentDays;
    private String intro;
    private String closing;
    private String taxNote;
    private String emailSubject;
    private String emailBody;
    private List<LineForm> lines = new ArrayList<>();

    /**
     * Returns the form of a stored template.
     *
     * @param template the template
     * @return the form
     */
    public static TemplateForm of(InvoiceTemplate template) {
        TemplateForm form = new TemplateForm();
        form.version = template.version();
        form.name = template.name();
        form.format = template.format();
        form.paymentDays = template.paymentDays();
        form.intro = template.intro();
        form.closing = template.closing();
        form.taxNote = template.taxNote();
        form.emailSubject = template.emailSubject();
        form.emailBody = template.emailBody();
        form.lines = DraftForm.rows(template.lines().stream().map(TemplateLine::toDraftLine).toList());
        return form;
    }

    /** @return the version of the template that the form shows */
    public Long getVersion() {
        return version;
    }

    /** @param version the version of the template that the form shows */
    public void setVersion(Long version) {
        this.version = version;
    }

    /** @return the name */
    public String getName() {
        return name;
    }

    /** @param name the name */
    public void setName(String name) {
        this.name = name;
    }

    /** @return the e-invoice format */
    public InvoiceFormat getFormat() {
        return format;
    }

    /** @param format the e-invoice format */
    public void setFormat(InvoiceFormat format) {
        this.format = format;
    }

    /** @return the days until payment is due */
    public int getPaymentDays() {
        return paymentDays;
    }

    /** @param paymentDays the days until payment is due */
    public void setPaymentDays(int paymentDays) {
        this.paymentDays = paymentDays;
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

    /** @return the subject of the e-mail */
    public String getEmailSubject() {
        return emailSubject;
    }

    /** @param emailSubject the subject of the e-mail */
    public void setEmailSubject(String emailSubject) {
        this.emailSubject = emailSubject;
    }

    /** @return the text of the e-mail */
    public String getEmailBody() {
        return emailBody;
    }

    /** @param emailBody the text of the e-mail */
    public void setEmailBody(String emailBody) {
        this.emailBody = emailBody;
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
