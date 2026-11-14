package io.github.grigoriev.im.mail;

/** The entered values of "Per E-Mail senden". */
public class SendForm {

    private String to;
    private String subject;
    private String body;
    private boolean pdf;
    private boolean xml;

    /** @return the address */
    public String getTo() {
        return to;
    }

    /** @param to the address */
    public void setTo(String to) {
        this.to = to;
    }

    /** @return the subject */
    public String getSubject() {
        return subject;
    }

    /** @param subject the subject */
    public void setSubject(String subject) {
        this.subject = subject;
    }

    /** @return the text */
    public String getBody() {
        return body;
    }

    /** @param body the text */
    public void setBody(String body) {
        this.body = body;
    }

    /** @return whether the PDF goes along */
    public boolean isPdf() {
        return pdf;
    }

    /** @param pdf whether the PDF goes along */
    public void setPdf(boolean pdf) {
        this.pdf = pdf;
    }

    /** @return whether the XML goes along */
    public boolean isXml() {
        return xml;
    }

    /** @param xml whether the XML goes along */
    public void setXml(boolean xml) {
        this.xml = xml;
    }
}
