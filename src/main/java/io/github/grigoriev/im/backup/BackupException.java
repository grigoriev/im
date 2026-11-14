package io.github.grigoriev.im.backup;

/** An import was refused. The message is German: the UI shows it to the admin. */
public class BackupException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * Creates the exception.
     *
     * @param message what is wrong, in German
     */
    public BackupException(String message) {
        super(message);
    }
}
