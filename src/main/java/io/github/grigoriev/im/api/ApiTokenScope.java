package io.github.grigoriev.im.api;

/** What a token may do. Both read; only {@link #WRITE} changes data. */
public enum ApiTokenScope {

    /** Reads only. */
    READ("Nur lesen"),

    /** Reads and writes. */
    WRITE("Lesen und schreiben");

    /** The authority of every token: it may read. */
    public static final String READ_AUTHORITY = "SCOPE_READ";
    /** The authority of a token that may write. */
    public static final String WRITE_AUTHORITY = "SCOPE_WRITE";

    private final String label;

    ApiTokenScope(String label) {
        this.label = label;
    }

    /**
     * Returns the German name for the page.
     *
     * @return for example "Nur lesen"
     */
    public String label() {
        return label;
    }
}
