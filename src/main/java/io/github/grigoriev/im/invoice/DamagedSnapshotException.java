package io.github.grigoriev.im.invoice;

import java.util.List;

/**
 * The snapshot of an issued invoice no longer matches its seal: it was changed outside the app. Nothing may compute
 * with it. A form that catches an {@link InvoiceException} shows the message; a page that only reads shows what the
 * check of all data finds.
 */
public class DamagedSnapshotException extends InvoiceException {

    private static final long serialVersionUID = 1L;

    /**
     * Creates the exception.
     *
     * @param number the number of the invoice
     */
    public DamagedSnapshotException(String number) {
        super(List.of(DocumentStore.SNAPSHOT_DAMAGED.formatted(number)));
    }
}
