package io.github.grigoriev.im.document;

import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;

/** The description of a business document as the form enters it. */
public class DocumentForm {

    private DocumentCategory category;
    private String title;
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate documentDate;
    private Integer year;
    private String note;

    /**
     * Returns the form of a stored document, to change it or to replace it.
     *
     * @param document the document
     * @return the form with its values
     */
    public static DocumentForm of(BusinessDocument document) {
        DocumentForm form = new DocumentForm();
        form.category = document.category();
        form.title = document.title();
        form.documentDate = document.documentDate();
        form.year = document.year();
        form.note = document.note();
        return form;
    }

    /**
     * Returns the empty form of a new document.
     *
     * @param today the default date
     * @return the form
     */
    public static DocumentForm empty(LocalDate today) {
        DocumentForm form = new DocumentForm();
        form.category = DocumentCategory.OTHER;
        form.documentDate = today;
        return form;
    }

    /**
     * Returns the kind.
     *
     * @return the kind, {@code null} if none was chosen
     */
    public DocumentCategory getCategory() {
        return category;
    }

    /**
     * Sets the kind.
     *
     * @param category the kind
     */
    public void setCategory(DocumentCategory category) {
        this.category = category;
    }

    /**
     * Returns the title.
     *
     * @return the title as entered
     */
    public String getTitle() {
        return title;
    }

    /**
     * Sets the title.
     *
     * @param title the title
     */
    public void setTitle(String title) {
        this.title = title;
    }

    /**
     * Returns the date on the document.
     *
     * @return the date, {@code null} if none was entered
     */
    public LocalDate getDocumentDate() {
        return documentDate;
    }

    /**
     * Sets the date on the document.
     *
     * @param documentDate the date
     */
    public void setDocumentDate(LocalDate documentDate) {
        this.documentDate = documentDate;
    }

    /**
     * Returns the year the document concerns.
     *
     * @return the year, {@code null} for none
     */
    public Integer getYear() {
        return year;
    }

    /**
     * Sets the year the document concerns.
     *
     * @param year the year
     */
    public void setYear(Integer year) {
        this.year = year;
    }

    /**
     * Returns the note.
     *
     * @return the note as entered
     */
    public String getNote() {
        return note;
    }

    /**
     * Sets the note.
     *
     * @param note the note
     */
    public void setNote(String note) {
        this.note = note;
    }
}
