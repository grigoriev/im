package io.github.grigoriev.im.seller;

import java.util.List;
import java.util.Map;

/** A settings change was refused. The messages are German: the UI shows them to the user. */
public class SettingsException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final transient Map<String, String> fields;
    private final List<String> problems;

    /**
     * Creates the exception.
     *
     * @param problems what is wrong, in German, at least one entry
     */
    public SettingsException(List<String> problems) {
        super(String.join(" ", problems));
        this.problems = List.copyOf(problems);
        this.fields = Map.of();
    }

    /**
     * Creates the exception with the problem of each field, for a form that marks them.
     *
     * @param fields what is wrong, in German, by field name, at least one entry, in the order of the form
     */
    public SettingsException(Map<String, String> fields) {
        super(String.join(" ", fields.values()));
        this.problems = List.copyOf(fields.values());
        this.fields = java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<>(fields));
    }

    /**
     * Returns the problem of each field, empty if the problems belong to no field.
     *
     * @return the problems by field name
     */
    public Map<String, String> fields() {
        return fields;
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
