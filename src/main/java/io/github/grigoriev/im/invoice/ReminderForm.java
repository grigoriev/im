package io.github.grigoriev.im.invoice;

import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;

/** The entered values of "Erinnerung vermerken". */
public class ReminderForm {

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate remindedOn;
    private String note;

    /** @return the day of the reminder */
    public LocalDate getRemindedOn() {
        return remindedOn;
    }

    /** @param remindedOn the day of the reminder */
    public void setRemindedOn(LocalDate remindedOn) {
        this.remindedOn = remindedOn;
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
