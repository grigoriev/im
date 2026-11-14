package io.github.grigoriev.im.invoice;

import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;

/** The entered values of "Zahlung erfassen". */
public class PaymentForm {

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate paidOn;
    private String amount;
    private String note;

    /** @return the day the money came in */
    public LocalDate getPaidOn() {
        return paidOn;
    }

    /** @param paidOn the day the money came in */
    public void setPaidOn(LocalDate paidOn) {
        this.paidOn = paidOn;
    }

    /** @return the amount as typed, German style */
    public String getAmount() {
        return amount;
    }

    /** @param amount the amount as typed, German style */
    public void setAmount(String amount) {
        this.amount = amount;
    }

    /** @return the note */
    public String getNote() {
        return note;
    }

    /** @param note the note */
    public void setNote(String note) {
        this.note = note;
    }
}
