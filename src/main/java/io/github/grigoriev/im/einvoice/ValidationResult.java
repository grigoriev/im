package io.github.grigoriev.im.einvoice;

import java.util.List;

/**
 * Result of a validation.
 *
 * @param valid whether the document passed
 * @param validator name of the validator
 * @param messages findings, empty when valid
 */
public record ValidationResult(boolean valid, String validator, List<String> messages) {

    /** Copies the messages. */
    public ValidationResult {
        messages = List.copyOf(messages);
    }
}
