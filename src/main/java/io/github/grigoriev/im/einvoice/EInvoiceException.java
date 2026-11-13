package io.github.grigoriev.im.einvoice;

/** The e-invoice could not be produced or read. */
public class EInvoiceException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * Creates the exception.
     *
     * @param message what failed
     * @param cause the underlying error
     */
    public EInvoiceException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Creates the exception.
     *
     * @param message what failed
     */
    public EInvoiceException(String message) {
        super(message);
    }
}
