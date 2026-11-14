package io.github.grigoriev.im.web;

import io.github.grigoriev.im.document.BusinessDocument;
import io.github.grigoriev.im.document.DocumentCategory;
import io.github.grigoriev.im.document.DocumentException;
import io.github.grigoriev.im.document.DocumentFile;
import io.github.grigoriev.im.document.DocumentForm;
import io.github.grigoriev.im.document.DocumentService;
import java.io.IOException;
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
    private static final String SUCCESS = "success";
    private static final String PROBLEMS = "problems";
    private static final String FORM = "form";

    private final DocumentService documents;
    private final Clock clock;

    /**
     * Creates the controller.
     *
     * @param documents the documents
     * @param clock the default date of a new document
     */
    public DocumentController(DocumentService documents, Clock clock) {
        this.documents = documents;
        this.clock = clock;
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
            return "redirect:/documents/" + added.id();
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
            return "redirect:/documents/" + id;
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
            return "redirect:/documents/" + added.id();
        } catch (DocumentException e) {
            model.addAttribute(PROBLEMS, e.problems());
            return page(model, found(id), form);
        }
    }

    /**
     * Downloads the file of a document.
     *
     * @param id the ID
     * @return the file as it was stored
     */
    @GetMapping("/{id}/file")
    public ResponseEntity<byte[]> download(@PathVariable long id) {
        DocumentFile file = documents.file(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(file.mediaType()))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(file.filename()).build().toString())
                .body(file.content());
    }

    private BusinessDocument found(long id) {
        return documents.find(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    private String page(Model model, BusinessDocument document, DocumentForm form) {
        model.addAttribute("document", document);
        model.addAttribute(FORM, form);
        model.addAttribute("categories", DocumentCategory.values());
        model.addAttribute("versions", document == null ? List.of() : documents.versions(document.id()));
        return EDIT;
    }

    private static String name(MultipartFile file) {
        return file == null ? null : file.getOriginalFilename();
    }

    private static byte[] bytes(MultipartFile file) throws IOException {
        return file == null ? new byte[0] : file.getBytes();
    }
}
