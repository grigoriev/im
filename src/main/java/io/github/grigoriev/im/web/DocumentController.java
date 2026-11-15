package io.github.grigoriev.im.web;

import io.github.grigoriev.im.document.BusinessDocument;
import io.github.grigoriev.im.document.DocumentCategory;
import io.github.grigoriev.im.document.DocumentException;
import io.github.grigoriev.im.document.DocumentFile;
import io.github.grigoriev.im.document.DocumentForm;
import io.github.grigoriev.im.document.DocumentService;
import io.github.grigoriev.im.document.ProcedureForm;
import io.github.grigoriev.im.document.ProcedureService;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.View;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * The page "Dokumente": the business documents with their versions. Both roles use it. A file is kept for good; a
 * new version replaces it in the list, and the old one stays in the history.
 */
@Controller
@RequestMapping("/documents")
public class DocumentController {

    static final String LIST = "documents/list";
    static final String EDIT = "documents/edit";
    static final String PROCEDURE_VIEW = "documents/procedure";
    private static final String PROCEDURE_FORM = "procedureForm";
    private static final String SUCCESS = "success";
    private static final String PROBLEMS = "problems";
    private static final String FORM = "form";
    private static final String DOCUMENT_PATH = "/documents";
    private static final String TO_DOCUMENT = "redirect:" + DOCUMENT_PATH + "/";

    private final DocumentService documents;
    private final ProcedureService procedure;
    private final Clock clock;

    /**
     * Creates the controller.
     *
     * @param documents the documents
     * @param procedure writes the Verfahrensdokumentation
     * @param clock the default date of a new document
     */
    public DocumentController(DocumentService documents, ProcedureService procedure, Clock clock) {
        this.documents = documents;
        this.procedure = procedure;
        this.clock = clock;
    }

    /**
     * Shows the form of the Verfahrensdokumentation, with the values of the last version.
     *
     * @param model the page model
     * @return the template name
     */
    @GetMapping("/procedure")
    public String procedureForm(Model model) {
        return procedurePage(model, procedure.form());
    }

    /**
     * Downloads the PDF of the form, without saving it.
     *
     * @param form the entered values
     * @param model the page model
     * @return the PDF as a download, or the form with the problems
     */
    @PostMapping("/procedure/preview")
    public ModelAndView procedurePreview(@ModelAttribute(PROCEDURE_FORM) ProcedureForm form, Model model) {
        try {
            byte[] content = procedure.preview(form);
            View download = (values, request, response) -> {
                response.setContentType(MediaType.APPLICATION_PDF_VALUE);
                response.setHeader(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename("Verfahrensdokumentation_Entwurf.pdf").build().toString());
                response.getOutputStream().write(content);
            };
            return new ModelAndView(download);
        } catch (DocumentException e) {
            model.addAttribute(PROBLEMS, e.problems());
            return new ModelAndView(procedurePage(model, form), model.asMap());
        }
    }

    /**
     * Writes the Verfahrensdokumentation and keeps it as a new version.
     *
     * @param form the entered values
     * @param model the page model
     * @param flash messages for the next page
     * @return a redirect to the new version, or the form with the problems
     */
    @PostMapping("/procedure")
    public String procedureSave(@ModelAttribute(PROCEDURE_FORM) ProcedureForm form, Model model,
            RedirectAttributes flash) {
        try {
            BusinessDocument saved = procedure.save(form);
            flash.addFlashAttribute(SUCCESS, "Verfahrensdokumentation als neue Fassung abgelegt. Bitte ausdrucken, "
                    + "unterschreiben und den Scan als neue Fassung ablegen.");
            return TO_DOCUMENT + saved.id();
        } catch (DocumentException e) {
            model.addAttribute(PROBLEMS, e.problems());
            return procedurePage(model, form);
        }
    }

    /**
     * Shows the current documents, optionally of one kind.
     *
     * @param category the kind to show, {@code null} for all
     * @param model the page model
     * @return the template name
     */
    @GetMapping
    public String list(@RequestParam(required = false) DocumentCategory category, Model model) {
        List<BusinessDocument> all = documents.list();
        model.addAttribute("any", !all.isEmpty());
        model.addAttribute("rows", all.stream().filter(d -> category == null || d.category() == category).toList());
        model.addAttribute("category", category);
        model.addAttribute("categories", DocumentCategory.values());
        return LIST;
    }

    /**
     * Shows the form of a new document.
     *
     * @param model the page model
     * @return the template name
     */
    @GetMapping("/new")
    public String create(Model model) {
        return page(model, null, DocumentForm.empty(LocalDate.now(clock)));
    }

    /**
     * Stores a new document. On a problem the form shows again; the file needs choosing again.
     *
     * @param form the description
     * @param file the uploaded file, {@code null} without one
     * @param model the page model
     * @param flash messages for the next page
     * @return a redirect to the document, or the form with the problems
     * @throws IOException if the upload cannot be read
     */
    @PostMapping
    public String add(@ModelAttribute(FORM) DocumentForm form, @RequestParam(required = false) MultipartFile file,
            Model model, RedirectAttributes flash) throws IOException {
        try {
            BusinessDocument added = documents.add(form, name(file), bytes(file));
            flash.addFlashAttribute(SUCCESS, "Dokument „" + added.title() + "“ abgelegt.");
            return TO_DOCUMENT + added.id();
        } catch (DocumentException e) {
            model.addAttribute(PROBLEMS, e.problems());
            return page(model, null, form);
        }
    }

    /**
     * Shows a document with its versions.
     *
     * @param id the ID
     * @param model the page model
     * @return the template name
     */
    @GetMapping("/{id}")
    public String show(@PathVariable long id, Model model) {
        BusinessDocument document = found(id);
        return page(model, document, DocumentForm.of(document));
    }

    /**
     * Changes the description of a document.
     *
     * @param id the ID
     * @param form the new description
     * @param model the page model
     * @param flash messages for the next page
     * @return a redirect to the document, or the form with the problems
     */
    @PostMapping("/{id}")
    public String change(@PathVariable long id, @ModelAttribute(FORM) DocumentForm form, Model model,
            RedirectAttributes flash) {
        try {
            documents.change(id, form);
            flash.addFlashAttribute(SUCCESS, "Änderungen gespeichert.");
            return TO_DOCUMENT + id;
        } catch (DocumentException e) {
            model.addAttribute(PROBLEMS, e.problems());
            return page(model, found(id), form);
        }
    }

    /**
     * Stores a new version of a document, with the description of the form.
     *
     * @param id the current version
     * @param form the description of the new version
     * @param file the uploaded file, {@code null} without one
     * @param model the page model
     * @param flash messages for the next page
     * @return a redirect to the new version, or the page with the problems
     * @throws IOException if the upload cannot be read
     */
    @PostMapping("/{id}/versions")
    public String replace(@PathVariable long id, @ModelAttribute(FORM) DocumentForm form,
            @RequestParam(required = false) MultipartFile file, Model model, RedirectAttributes flash)
            throws IOException {
        try {
            BusinessDocument added = documents.replace(id, form, name(file), bytes(file));
            flash.addFlashAttribute(SUCCESS, "Neue Fassung abgelegt.");
            return TO_DOCUMENT + added.id();
        } catch (DocumentException e) {
            model.addAttribute(PROBLEMS, e.problems());
            return page(model, found(id), form);
        }
    }

    /**
     * Downloads the file of a document.
     *
     * @param id the ID
     * @return the file as it was stored; a redirect to the document, which shows the check, if it is broken or changed
     */
    @GetMapping("/{id}/file")
    public ResponseEntity<byte[]> download(@PathVariable long id) {
        DocumentFile file;
        try {
            file = documents.file(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        } catch (DocumentException _) {
            return ResponseEntity.status(HttpStatus.SEE_OTHER).location(URI.create(DOCUMENT_PATH + "/" + id))
                    .build();
        }
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(file.mediaType()))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(file.filename(), StandardCharsets.UTF_8).build().toString())
                .body(file.content());
    }

    private BusinessDocument found(long id) {
        return documents.find(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    private String procedurePage(Model model, ProcedureForm form) {
        model.addAttribute(PROCEDURE_FORM, form);
        model.addAttribute("current", procedure.current().orElse(null));
        return PROCEDURE_VIEW;
    }

    private String page(Model model, BusinessDocument document, DocumentForm form) {
        model.addAttribute("document", document);
        model.addAttribute(FORM, form);
        model.addAttribute("categories", DocumentCategory.values());
        model.addAttribute("versions", document == null ? List.of() : documents.versions(document.id()));
        model.addAttribute("integrity", document == null ? List.of() : documents.problems(document.id()));
        return EDIT;
    }

    private static String name(MultipartFile file) {
        return file == null ? null : file.getOriginalFilename();
    }

    /** Reads the upload, unless it is too large: the upload limit serves the backup import and is far larger. */
    private static byte[] bytes(MultipartFile file) throws IOException {
        if (file == null) {
            return new byte[0];
        }
        if (file.getSize() > DocumentService.MAX_BYTES) {
            throw new DocumentException(List.of(DocumentService.TOO_LARGE));
        }
        return file.getBytes();
    }
}
