package io.github.grigoriev.im.expense;

import java.util.List;

/** An expense that cannot be stored or changed, with what is wrong, in German. */
public class ExpenseException extends RuntimeException {

    private final transient List<String> problems;

    /**
     * Creates the exception.
     *
     * @param problems what is wrong, at least one
     */
    public ExpenseException(List<String> problems) {
        super(String.join(" ", problems));
        this.problems = List.copyOf(problems);
    }

    /**
     * Returns what is wrong.
     *
     * @return the problems, in German
     */
    public List<String> problems() {
        return problems;
    }
}
