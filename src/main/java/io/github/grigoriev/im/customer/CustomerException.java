package io.github.grigoriev.im.customer;

import java.util.List;

/** A customer change was refused. The messages are German: the UI shows them to the user. */
public class CustomerException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final List<String> problems;

    /**
     * Creates the exception.
     *
     * @param problems what is wrong, in German, at least one entry
     */
    public CustomerException(List<String> problems) {
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
