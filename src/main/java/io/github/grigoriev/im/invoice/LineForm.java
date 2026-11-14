package io.github.grigoriev.im.invoice;

import io.github.grigoriev.im.einvoice.TaxCategory;

/** A line of the draft form, with quantity and price as typed. */
public class LineForm {

    private String text = "";
    private boolean lumpSum = true;
    private String quantity = "1";
    private String price = "";
    private TaxCategory category;

    /**
     * Returns the service text.
     *
     * @return the text
     */
    public String getText() {
        return text;
    }

    /**
     * Sets the service text.
     *
     * @param text the text
     */
    public void setText(String text) {
        this.text = text;
    }

    /**
     * Tells whether the line is a lump sum.
     *
     * @return {@code true} for a lump sum
     */
    public boolean isLumpSum() {
        return lumpSum;
    }

    /**
     * Sets whether the line is a lump sum.
     *
     * @param lumpSum {@code true} for a lump sum
     */
    public void setLumpSum(boolean lumpSum) {
        this.lumpSum = lumpSum;
    }

    /**
     * Returns the quantity as typed.
     *
     * @return the quantity
     */
    public String getQuantity() {
        return quantity;
    }

    /**
     * Sets the quantity as typed.
     *
     * @param quantity the quantity
     */
    public void setQuantity(String quantity) {
        this.quantity = quantity;
    }

    /**
     * Returns the price as typed: the amount of a lump sum, the unit price otherwise.
     *
     * @return the price
     */
    public String getPrice() {
        return price;
    }

    /**
     * Sets the price as typed.
     *
     * @param price the price
     */
    public void setPrice(String price) {
        this.price = price;
    }

    /**
     * Returns the tax category.
     *
     * @return the category
     */
    public TaxCategory getCategory() {
        return category;
    }

    /**
     * Sets the tax category.
     *
     * @param category the category
     */
    public void setCategory(TaxCategory category) {
        this.category = category;
    }
}
