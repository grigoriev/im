package io.github.grigoriev.im.einvoice;

import java.math.BigDecimal;

/**
 * The unit of a line with a quantity (docs/PLAN.md, section 7). The XML carries its UN/ECE Recommendation 20 code
 * (BT-130), the PDF its label after the quantity.
 *
 * <p>A teaching unit of 45 minutes has no code of its own. It travels as {@code C62} (one), and the PDF names it "UE".
 */
public enum LineUnit {

    /** A piece, the unit of lines from before the units. The PDF shows no label, as it did then. */
    PIECE("C62", "Stück", "", ""),
    /** An hour. */
    HOUR("HUR", "Stunden", "Std.", "Std."),
    /** A teaching unit of 45 minutes. */
    TEACHING_UNIT("C62", "UE (45 Min.)", "UE", "UE"),
    /** A day. */
    DAY("DAY", "Tage", "Tag", "Tage");

    private final String code;
    private final String choice;
    private final String one;
    private final String many;

    LineUnit(String code, String choice, String one, String many) {
        this.code = code;
        this.choice = choice;
        this.one = one;
        this.many = many;
    }

    /**
     * Returns the UN/ECE Recommendation 20 code for the XML.
     *
     * @return for example {@code HUR}
     */
    public String code() {
        return code;
    }

    /**
     * Returns the German name in the choice of the editor.
     *
     * @return for example "Stunden"
     */
    public String choice() {
        return choice;
    }

    /**
     * Returns the label the PDF prints after a quantity.
     *
     * @param quantity the quantity of the line
     * @return for example "Std.", "Tag" for one day, empty for a piece
     */
    public String label(BigDecimal quantity) {
        return quantity.compareTo(BigDecimal.ONE) == 0 ? one : many;
    }
}
