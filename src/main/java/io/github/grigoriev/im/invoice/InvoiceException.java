package io.github.grigoriev.im.invoice;

import java.util.List;

/** An invoice change was refused. The messages are German: the UI shows them to the user. */
public class InvoiceException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final List<String> problems;

    /**
     * Creates the exception.
     *
     * @param problems what is wrong, in German, at least one entry
     */
    public InvoiceException(List<String> problems) {
        super(String.join(" ", problems));
        this.problems = List.copyOf(problems);
    }

    /**
     * Returns every problem found.
     *
     * @return the problems
     */
    public List<String> problems() {
        return problems;
    }
}
