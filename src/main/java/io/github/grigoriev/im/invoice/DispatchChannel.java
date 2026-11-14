package io.github.grigoriev.im.invoice;

/** The way an invoice went to the customer. The label is the German text of the UI. */
public enum DispatchChannel {

    /** Sent by the app over SMTP. */
    EMAIL_APP("E-Mail aus der App"),
    /** Sent from the user's own mail program. */
    OWN_MAIL("Eigenes Mailprogramm"),
    /** Sent by post. */
    POST("Post"),
    /** Uploaded to a portal of the customer. */
    PORTAL("Portal des Kunden"),
    /** Handed over in person. */
    IN_PERSON("Persönlich übergeben");

    private final String label;

    DispatchChannel(String label) {
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

    /**
     * Tells whether the user records this way by hand.
     *
     * @return {@code false} only for {@link #EMAIL_APP}
     */
    public boolean isManual() {
        return this != EMAIL_APP;
    }
}
