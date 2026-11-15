package io.github.grigoriev.im.document;

import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;

/** The values of the Verfahrensdokumentation that the profile does not hold, as the form enters them. */
public class ProcedureForm {

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate validFrom;
    private String expenses;
    private String archive;
    private String change;

    /**
     * Returns the day from which the procedure applies.
     *
     * @return the day, {@code null} if none was entered
     */
    public LocalDate getValidFrom() {
        return validFrom;
    }

    /**
     * Sets the day from which the procedure applies.
     *
     * @param validFrom the day
     */
    public void setValidFrom(LocalDate validFrom) {
        this.validFrom = validFrom;
    }

    /**
     * Returns the program for receipts and expenses.
     *
     * @return the program as entered
     */
    public String getExpenses() {
        return expenses;
    }

    /**
     * Sets the program for receipts and expenses.
     *
     * @param expenses the program, for example "WISO Steuern"
     */
    public void setExpenses(String expenses) {
        this.expenses = expenses;
    }

    /**
     * Returns where the yearly archives go besides the app.
     *
     * @return the place as entered
     */
    public String getArchive() {
        return archive;
    }

    /**
     * Sets where the yearly archives go besides the app.
     *
     * @param archive the place, for example "in einem Dropbox-Ordner"
     */
    public void setArchive(String archive) {
        this.archive = archive;
    }

    /**
     * Returns what this version changes.
     *
     * @return the change as entered
     */
    public String getChange() {
        return change;
    }

    /**
     * Sets what this version changes.
     *
     * @param change the change, for example "Erstfassung"
     */
    public void setChange(String change) {
        this.change = change;
    }
}
