package io.github.grigoriev.im.document;

import io.github.grigoriev.im.einvoice.InvoicePdfRenderer;
import io.github.grigoriev.im.seller.SellerProfile;
import io.github.grigoriev.im.seller.SellerService;
import io.github.grigoriev.im.validation.Fields;
import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.info.BuildProperties;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Writes the Verfahrensdokumentation of the invoicing (GoBD, Rz. 151 ff.) from the profile and a few values of a form,
 * and keeps it as a business document: each save is a new version. Its table "Änderungen" lists the change of every
 * version the app wrote, which carries the file name {@code Verfahrensdokumentation_<date>.pdf}.
 */
@Service
public class ProcedureService {

    static final String TITLE = "Verfahrensdokumentation";
    static final String PREFIX = "Verfahrensdokumentation_";
    static final String VALID_FROM_KEY = "procedure-valid-from";
    static final String EXPENSES_KEY = "procedure-expenses";
    static final String ARCHIVE_KEY = "procedure-archive";
    static final int MAX_TEXT = 200;
    static final String FIRST = "Erstfassung";
    static final String NO_PROFILE = "Bitte zuerst das Profil mit Name, Anschrift und Steuernummer ausfüllen.";

    static final String READ = "SELECT value FROM app_setting WHERE key = :key";
    static final String WRITE = """
            INSERT INTO app_setting (key, value) VALUES (:key, :value)
            ON CONFLICT (key) DO UPDATE SET value = EXCLUDED.value""";

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    private final JdbcClient jdbc;
    private final SellerService sellers;
    private final DocumentService documents;
    private final InvoicePdfRenderer pdf;
    private final ObjectProvider<BuildProperties> build;
    private final Clock clock;

    /**
     * Creates the service.
     *
     * @param jdbc keeps the values of the form
     * @param sellers the profile
     * @param documents keeps the versions
     * @param pdf renders the PDF
     * @param build the version of the app, absent in a build without build info
     * @param clock the date of a version
     */
    public ProcedureService(JdbcClient jdbc, SellerService sellers, DocumentService documents, InvoicePdfRenderer pdf,
            ObjectProvider<BuildProperties> build, Clock clock) {
        this.jdbc = jdbc;
        this.sellers = sellers;
        this.documents = documents;
        this.pdf = pdf;
        this.build = build;
        this.clock = clock;
    }

    /**
     * One row of the table "Änderungen".
     *
     * @param date the date, German
     * @param text the change
     */
    public record Change(String date, String text) {
    }

    /**
     * Returns the form with the values of the last save, and "Erstfassung" as the change of a first version.
     *
     * @return the form
     */
    public ProcedureForm form() {
        ProcedureForm form = new ProcedureForm();
        form.setValidFrom(read(VALID_FROM_KEY).map(LocalDate::parse).orElse(null));
        form.setExpenses(read(EXPENSES_KEY).orElse(null));
        form.setArchive(read(ARCHIVE_KEY).orElse(null));
        form.setChange(current().isEmpty() ? FIRST : null);
        return form;
    }

    /**
     * Returns the current version of the Verfahrensdokumentation.
     *
     * @return the newest document of that kind, empty if there is none
     */
    public Optional<BusinessDocument> current() {
        return documents.list().stream().filter(document -> document.category() == DocumentCategory.PROCEDURE)
                .max(Comparator.comparing(BusinessDocument::createdAt).thenComparing(BusinessDocument::id));
    }

    /**
     * Writes the PDF of the form, without saving it.
     *
     * @param form the entered values
     * @return the PDF
     * @throws DocumentException if a value is missing or the profile is incomplete
     */
    public byte[] preview(ProcedureForm form) {
        return render(check(form), form);
    }

    /**
     * Writes the PDF and keeps it as a new version of the Verfahrensdokumentation, and the values for the next time.
     *
     * @param form the entered values
     * @return the stored version
     * @throws DocumentException if a value is missing or the profile is incomplete
     */
    @Transactional
    public BusinessDocument save(ProcedureForm form) {
        SellerProfile seller = check(form);
        byte[] content = render(seller, form);
        write(VALID_FROM_KEY, form.getValidFrom().toString());
        write(EXPENSES_KEY, Fields.text(form.getExpenses()));
        write(ARCHIVE_KEY, Fields.text(form.getArchive()));
        LocalDate today = LocalDate.now(clock);
        DocumentForm description = new DocumentForm();
        description.setCategory(DocumentCategory.PROCEDURE);
        description.setTitle(TITLE);
        description.setDocumentDate(today);
        description.setNote(Fields.text(form.getChange()));
        String filename = PREFIX + today + ".pdf";
        Optional<BusinessDocument> current = current();
        return current.isPresent() ? documents.replace(current.get().id(), description, filename, content)
                : documents.add(description, filename, content);
    }

    private SellerProfile check(ProcedureForm form) {
        List<String> problems = new ArrayList<>();
        if (form.getValidFrom() == null) {
            problems.add("Bitte das Datum angeben, ab dem das Verfahren gilt.");
        }
        Fields.required(problems, Fields.text(form.getExpenses()), "das Programm für Belege und Ausgaben", MAX_TEXT);
        Fields.required(problems, Fields.text(form.getArchive()), "den Ablageort der Jahresarchive", MAX_TEXT);
        Fields.required(problems, Fields.text(form.getChange()), "die Änderung", MAX_TEXT);
        Optional<SellerProfile> seller = sellers.find().filter(ProcedureService::complete);
        if (seller.isEmpty()) {
            problems.add(NO_PROFILE);
        }
        if (!problems.isEmpty()) {
            throw new DocumentException(problems);
        }
        return seller.get();
    }

    private static boolean complete(SellerProfile seller) {
        return Fields.text(seller.name()) != null && Fields.text(seller.street()) != null
                && Fields.text(seller.city()) != null && Fields.text(seller.taxNumber()) != null;
    }

    private byte[] render(SellerProfile seller, ProcedureForm form) {
        return pdf.render("pdf/procedure", Map.of("seller", seller, "validFrom", DATE.format(form.getValidFrom()),
                "expenses", Fields.text(form.getExpenses()), "archive", Fields.text(form.getArchive()),
                "version", version(), "changes", changes(Fields.text(form.getChange()))));
    }

    /** The changes of the versions the app wrote, oldest first, and the new one. */
    List<Change> changes(String change) {
        List<Change> changes = new ArrayList<>(current().map(document -> documents.versions(document.id())).orElse(List.of())
                .stream().filter(version -> version.filename().startsWith(PREFIX))
                .sorted(Comparator.comparing(BusinessDocument::createdAt).thenComparing(BusinessDocument::id))
                .map(version -> new Change(DATE.format(version.documentDate()), version.note())).toList());
        changes.add(new Change(DATE.format(LocalDate.now(clock)), change));
        return changes;
    }

    String version() {
        BuildProperties properties = build.getIfAvailable();
        return properties == null ? "ohne Versionsangabe" : properties.getVersion();
    }

    private Optional<String> read(String key) {
        return jdbc.sql(READ).param("key", key).query(String.class).optional();
    }

    private void write(String key, String value) {
        jdbc.sql(WRITE).param("key", key).param("value", value).update();
    }
}
