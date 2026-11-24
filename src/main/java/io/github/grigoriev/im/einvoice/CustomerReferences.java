package io.github.grigoriev.im.einvoice;

/**
 * The references a customer asks to find on its invoices. Each may be {@code null}.
 *
 * @param sellerIdentifier the number of the seller at the customer, the Geschäftspartnernummer (BT-29)
 * @param purchaseOrder the purchase order of the customer, the Bestellnummer (BT-13)
 * @param contract the contract the invoice belongs to (BT-12)
 */
public record CustomerReferences(String sellerIdentifier, String purchaseOrder, String contract) {

    /** No references. */
    public static final CustomerReferences NONE = new CustomerReferences(null, null, null);
}
