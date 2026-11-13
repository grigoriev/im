package io.github.grigoriev.im.user;

/** A user change was refused. The message is German: the UI shows it to the user. */
public class UserException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * Creates the exception.
     *
     * @param message what is wrong, in German
     */
    public UserException(String message) {
        super(message);
    }
}
