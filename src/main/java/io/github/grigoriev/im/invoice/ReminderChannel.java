package io.github.grigoriev.im.invoice;

/** How a payment reminder reached the customer. The label is the German text of the UI. */
public enum ReminderChannel {

    /** Sent by the app over SMTP. */
    EMAIL_APP("E-Mail aus der App"),
    /** Noted by hand: a call, a letter or an e-mail from the own mail program. */
    MANUAL("Von Hand vermerkt");

    private final String label;

    ReminderChannel(String label) {
        this.label = label;
    }

    /**
     * Returns the German label.
     *
     * @return the label
     */
    public String label() {
        return label;
    }
}
