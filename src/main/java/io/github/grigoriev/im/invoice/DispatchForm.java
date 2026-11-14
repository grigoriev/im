package io.github.grigoriev.im.invoice;

import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;

/** The entered values of "Als versendet markieren". */
public class DispatchForm {

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate sentOn;
    private DispatchChannel channel;
    private String recipient;
    private String note;

    /** @return the day it went out */
    public LocalDate getSentOn() {
        return sentOn;
    }

    /** @param sentOn the day it went out */
    public void setSentOn(LocalDate sentOn) {
        this.sentOn = sentOn;
    }

    /** @return the way */
    public DispatchChannel getChannel() {
        return channel;
    }

    /** @param channel the way */
    public void setChannel(DispatchChannel channel) {
        this.channel = channel;
    }

    /** @return to whom */
    public String getRecipient() {
        return recipient;
    }

    /** @param recipient to whom */
    public void setRecipient(String recipient) {
        this.recipient = recipient;
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
