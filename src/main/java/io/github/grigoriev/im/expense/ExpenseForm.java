package io.github.grigoriev.im.expense;

import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;

/** The entered values of an expense. */
public class ExpenseForm {

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate paidOn;
    private String amount;
    private ExpenseCategory category;
    private String vendor;
    private String description;
    private boolean asset;

    /**
     * Fills the form with an expense.
     *
     * @param expense the expense
     * @return the form
     */
    public static ExpenseForm of(Expense expense) {
        ExpenseForm form = new ExpenseForm();
        form.paidOn = expense.paidOn();
        form.amount = expense.amount().toPlainString().replace('.', ',');
        form.category = expense.category();
        form.vendor = expense.vendor();
        form.description = expense.description();
        form.asset = expense.asset();
        return form;
    }

    /**
     * Returns the day of the payment.
     *
     * @return the day, {@code null} if not entered
     */
    public LocalDate getPaidOn() {
        return paidOn;
    }

    /**
     * Sets the day of the payment.
     *
     * @param paidOn the day
     */
    public void setPaidOn(LocalDate paidOn) {
        this.paidOn = paidOn;
    }

    /**
     * Returns the amount as typed.
     *
     * @return the text
     */
    public String getAmount() {
        return amount;
    }

    /**
     * Sets the amount as typed.
     *
     * @param amount the text
     */
    public void setAmount(String amount) {
        this.amount = amount;
    }

    /**
     * Returns the kind.
     *
     * @return the kind, {@code null} if not chosen
     */
    public ExpenseCategory getCategory() {
        return category;
    }

    /**
     * Sets the kind.
     *
     * @param category the kind
     */
    public void setCategory(ExpenseCategory category) {
        this.category = category;
    }

    /**
     * Returns who was paid.
     *
     * @return the text
     */
    public String getVendor() {
        return vendor;
    }

    /**
     * Sets who was paid.
     *
     * @param vendor the text
     */
    public void setVendor(String vendor) {
        this.vendor = vendor;
    }

    /**
     * Returns what was bought.
     *
     * @return the text
     */
    public String getDescription() {
        return description;
    }

    /**
     * Sets what was bought.
     *
     * @param description the text
     */
    public void setDescription(String description) {
        this.description = description;
    }

    /**
     * Tells whether it is an asset.
     *
     * @return {@code true} for an Anlagegut
     */
    public boolean isAsset() {
        return asset;
    }

    /**
     * Marks it as an asset.
     *
     * @param asset {@code true} for an Anlagegut
     */
    public void setAsset(boolean asset) {
        this.asset = asset;
    }
}
