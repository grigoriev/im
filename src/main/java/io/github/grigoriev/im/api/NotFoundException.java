package io.github.grigoriev.im.api;

/** A request of the API names something that does not exist. The message is German. */
public class NotFoundException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * Creates the exception.
     *
     * @param message what is missing, in German
     */
    public NotFoundException(String message) {
        super(message);
    }
}
