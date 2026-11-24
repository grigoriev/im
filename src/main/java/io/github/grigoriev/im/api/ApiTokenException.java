package io.github.grigoriev.im.api;

/** A token change was refused. The message is German: the page shows it to the user. */
public class ApiTokenException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * Creates the exception.
     *
     * @param message what is wrong, in German
     */
    public ApiTokenException(String message) {
        super(message);
    }
}
