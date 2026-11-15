package io.github.grigoriev.im.integrity;

import java.util.List;

/**
 * Something the check found changed or broken.
 *
 * @param invoiceId the invoice it belongs to, {@code null} for a business document
 * @param what what it is, for example "Rechnung 2027-01"
 * @param href the page that shows it
 * @param problems what is wrong, at least one
 */
public record Damage(Long invoiceId, String what, String href, List<String> problems) {

    /** Keeps a copy of the problems. */
    public Damage {
        problems = List.copyOf(problems);
    }

    /**
     * Returns the problems as one line, for the audit log.
     *
     * @return what it is and what is wrong
     */
    public String summary() {
        return what + ": " + String.join(" ", problems);
    }
}
