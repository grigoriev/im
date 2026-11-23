package io.github.grigoriev.im.einvoice;

import java.util.Locale;

/**
 * The text size of the buyer address in the window field of DIN 5008, form B: 27.3 mm below the sender line, 85 mm
 * wide. Six lines fit at the size of the invoice. A longer address, for example a name of two lines, gets a smaller
 * size, so the full name stays (§14 Abs. 4 Nr. 1 UStG) and the address stays inside the window. The font is
 * monospace, so the length of a text tells its lines.
 *
 * @param fontSize the font size in pt
 * @param lineHeight the line height in mm
 * @param lines the lines of the address at this size
 */
public record AddressLayout(double fontSize, double lineHeight, int lines) {

    /** The height below the sender line, in mm. */
    static final double FIELD_HEIGHT = 27.3;

    private static final double FIELD_WIDTH = 85;
    private static final double LARGEST = 9.5;
    private static final double SMALLEST = 6;
    private static final double STEP = 0.5;
    /** The line height at the size of the invoice, in mm: six lines fit. */
    private static final double LINE_HEIGHT = 4.5;
    /** The advance of a JetBrains Mono character, in em. */
    private static final double ADVANCE = 0.6;
    private static final double MM_PER_PT = 25.4 / 72;

    /**
     * Returns the largest size at which the address of the buyer fits the field, or the smallest size.
     *
     * @param buyer the buyer
     * @return the layout
     */
    public static AddressLayout of(Party buyer) {
        double size = LARGEST;
        while (size > SMALLEST && lines(buyer, size) > maxLines(size)) {
            size -= STEP;
        }
        return new AddressLayout(size, lineHeight(size), lines(buyer, size));
    }

    /**
     * Returns the CSS of the address.
     *
     * @return for example {@code font-size: 9.5pt; line-height: 4.50mm}
     */
    public String style() {
        return String.format(Locale.ROOT, "font-size: %.1fpt; line-height: %.2fmm", fontSize, lineHeight);
    }

    /**
     * Returns how many lines fit the height of the field.
     *
     * @param size the font size in pt
     * @return the lines
     */
    static int maxLines(double size) {
        return (int) (FIELD_HEIGHT / lineHeight(size));
    }

    private static double lineHeight(double size) {
        return LINE_HEIGHT * size / LARGEST;
    }

    /** The addition and the contact person keep one line each; the other lines wrap. */
    private static int lines(Party buyer, double size) {
        int perLine = perLine(size);
        int lines = wrapped(buyer.name(), perLine) + wrapped(buyer.street(), perLine)
                + wrapped(buyer.postcode() + " " + buyer.city(), perLine);
        if (buyer.addressAddition() != null) {
            lines++;
        }
        if (buyer.contactName() != null) {
            lines++;
        }
        if (!"DE".equals(buyer.countryCode())) {
            lines++;
        }
        return lines;
    }

    /**
     * Returns how many characters fit one line of the field.
     *
     * @param size the font size in pt
     * @return the characters
     */
    static int perLine(double size) {
        return (int) (FIELD_WIDTH / (ADVANCE * size * MM_PER_PT));
    }

    /**
     * Counts the lines of a text that wraps between its words. A word longer than a line counts as the lines it would
     * fill, so the count never falls short.
     *
     * @param text the text
     * @param perLine the characters of one line
     * @return the lines
     */
    static int wrapped(String text, int perLine) {
        int lines = 0;
        int used = perLine;
        for (String word : text.split(" ")) {
            int length = word.length();
            if (used + 1 + length <= perLine) {
                used += 1 + length;
            } else {
                lines += Math.max(1, (length + perLine - 1) / perLine);
                used = Math.min(length, perLine);
            }
        }
        return lines;
    }
}
