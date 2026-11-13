package io.github.grigoriev.im.seller;

import io.github.grigoriev.im.einvoice.TaxCategory;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The default tax notes of the exempt categories. The admin changes them; a template and an invoice can
 * override them (docs/PLAN.md, section 6). The standard rates have no note: the PDF shows their VAT.
 */
@Service
public class TaxNoteService {

    /** Maximum length of a note. */
    static final int MAX_NOTE = 500;

    /** The categories with a note, in the order of the enum. */
    public static final List<TaxCategory> CATEGORIES = Arrays.stream(TaxCategory.values())
            .filter(category -> !category.defaultNote().isEmpty()).toList();

    /** Inserts or replaces a note, atomic even if two admins save at once. */
    static final String UPSERT = """
            INSERT INTO tax_note (category, text) VALUES (:category, :text)
            ON CONFLICT (category) DO UPDATE SET text = EXCLUDED.text""";

    private final TaxNoteRepository notes;
    private final JdbcClient jdbc;

    /**
     * Creates the service.
     *
     * @param notes reads and removes the changed notes
     * @param jdbc writes the changed notes
     */
    public TaxNoteService(TaxNoteRepository notes, JdbcClient jdbc) {
        this.notes = notes;
        this.jdbc = jdbc;
    }

    /**
     * Returns the note of every category that has one.
     *
     * @return the notes by category, in the order of {@link #CATEGORIES}
     */
    public Map<TaxCategory, String> notes() {
        Map<String, String> changed = notes.findAll().stream()
                .collect(Collectors.toMap(TaxNote::category, TaxNote::text));
        Map<TaxCategory, String> result = new LinkedHashMap<>();
        CATEGORIES.forEach(category -> result.put(category, changed.getOrDefault(category.name(), category.defaultNote())));
        return result;
    }

    /**
     * Returns the note of a category.
     *
     * @param category the category
     * @return the changed note, the default note, or empty for a standard rate
     */
    public String note(TaxCategory category) {
        return notes.findById(category.name()).map(TaxNote::text).orElse(category.defaultNote());
    }

    /**
     * Saves the notes. A blank note or the default text goes back to the default.
     *
     * @param changes the notes by category; categories without a note are ignored
     * @throws SettingsException if a note is too long
     */
    @Transactional
    public void save(Map<TaxCategory, String> changes) {
        List<String> tooLong = changes.entrySet().stream()
                .filter(entry -> entry.getValue() != null && entry.getValue().strip().length() > MAX_NOTE)
                .map(entry -> "Der Hinweis „" + entry.getKey().label() + "“ hat höchstens " + MAX_NOTE + " Zeichen.")
                .toList();
        if (!tooLong.isEmpty()) {
            throw new SettingsException(tooLong);
        }
        changes.forEach((category, text) -> {
            if (CATEGORIES.contains(category)) {
                String note = text == null ? "" : text.strip();
                if (note.isEmpty() || note.equals(category.defaultNote())) {
                    notes.deleteById(category.name());
                } else {
                    jdbc.sql(UPSERT).param("category", category.name()).param("text", note).update();
                }
            }
        });
    }
}
