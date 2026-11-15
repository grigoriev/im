package io.github.grigoriev.im.web;

import io.github.grigoriev.im.document.DocumentFile;
import io.github.grigoriev.im.document.DocumentService;
import io.github.grigoriev.im.expense.Expense;
import io.github.grigoriev.im.expense.ExpenseCategory;
import io.github.grigoriev.im.expense.ExpenseException;
import io.github.grigoriev.im.expense.ExpenseForm;
import io.github.grigoriev.im.expense.ExpenseService;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;
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
 * The expenses of a year with their receipts (docs/PLAN.md, section 7, "Expenses"): the list with the sums by kind, the
 * entry with the receipt, the change, the cancellation and the download of a receipt.
 */
@Controller
@RequestMapping("/expenses")
public class ExpenseController {

    static final String LIST = "expenses/list";
    static final String EDIT = "expenses/edit";
    private static final String SUCCESS = "success";
    private static final String PROBLEMS = "problems";
    private static final String FORM = "form";
    private static final String EXPENSE_PATH = "/expenses";
    private static final String TO_EXPENSE = "redirect:" + EXPENSE_PATH + "/";

    private final ExpenseService expenses;

    /**
     * Creates the controller.
     *
     * @param expenses the expenses
     */
    public ExpenseController(ExpenseService expenses) {
        this.expenses = expenses;
    }

    /**
     * Lists the expenses of a year with their sums.
     *
     * @param asked the year, this year if not given
     * @param model the page model
     * @return the template name
     */
    @GetMapping
    public String list(@RequestParam(name = "year", required = false) Integer asked, Model model) {
        int year = asked == null ? expenses.today().getYear() : asked;
        List<Expense> rows = expenses.ofYear(year);
        SortedSet<Integer> years = new TreeSet<>(Comparator.reverseOrder());
        years.addAll(expenses.years());
        years.add(expenses.today().getYear());
        years.add(year);
        var totals = ExpenseService.totals(rows);
        List<Expense> assets = ExpenseService.assets(rows);
        model.addAttribute("year", year);
        model.addAttribute("years", years);
        model.addAttribute("rows", rows);
        model.addAttribute("totals", totals);
        model.addAttribute("total", totals.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add));
        model.addAttribute("assetTotal", assets.stream().map(Expense::amount).reduce(BigDecimal.ZERO, BigDecimal::add));
        return LIST;
    }

    /**
     * Shows the form of a new expense.
     *
     * @param model the page model
     * @return the template name
     */
    @GetMapping("/new")
    public String create(Model model) {
        ExpenseForm form = new ExpenseForm();
        form.setPaidOn(expenses.today());
        return page(model, null, form);
    }

    /**
     * Enters an expense with its receipt.
     *
     * @param form the entered values
     * @param file the receipt, {@code null} without one
     * @param model the page model
     * @param flash messages for the next page
     * @return a redirect to the expense, or the form with the problems
     * @throws IOException if the upload cannot be read
     */
    @PostMapping
    public String add(@ModelAttribute(FORM) ExpenseForm form, @RequestParam(required = false) MultipartFile file,
            Model model, RedirectAttributes flash) throws IOException {
        try {
            Expense added = expenses.add(form, file == null ? null : file.getOriginalFilename(), bytes(file));
            flash.addFlashAttribute(SUCCESS, "Ausgabe erfasst.");
            return TO_EXPENSE + added.id();
        } catch (ExpenseException e) {
            model.addAttribute(PROBLEMS, e.problems());
            return page(model, null, form);
        }
    }

    /**
     * Shows an expense.
     *
     * @param id the ID
     * @param model the page model
     * @return the template name
     */
    @GetMapping("/{id}")
    public String show(@PathVariable long id, Model model) {
        Expense expense = found(id);
        return page(model, expense, ExpenseForm.of(expense));
    }

    /**
     * Changes the details of an expense.
     *
     * @param id the ID
     * @param form the entered values
     * @param model the page model
     * @param flash messages for the next page
     * @return a redirect to the expense, or the form with the problems
     */
    @PostMapping("/{id}")
    public String change(@PathVariable long id, @ModelAttribute(FORM) ExpenseForm form, Model model,
            RedirectAttributes flash) {
        try {
            expenses.change(id, form);
            flash.addFlashAttribute(SUCCESS, "Änderungen gespeichert.");
            return TO_EXPENSE + id;
        } catch (ExpenseException e) {
            model.addAttribute(PROBLEMS, e.problems());
            return page(model, found(id), form);
        }
    }

    /**
     * Cancels an expense.
     *
     * @param id the ID
     * @param reason why
     * @param flash messages for the next page
     * @return a redirect to the expense
     */
    @PostMapping("/{id}/cancel")
    public String cancel(@PathVariable long id, @RequestParam(required = false) String reason,
            RedirectAttributes flash) {
        try {
            expenses.cancel(id, reason);
            flash.addFlashAttribute(SUCCESS, "Ausgabe storniert. Sie zählt nicht mehr.");
        } catch (ExpenseException e) {
            flash.addFlashAttribute("error", e.getMessage());
        }
        return TO_EXPENSE + id;
    }

    /**
     * Downloads the receipt of an expense.
     *
     * @param id the ID
     * @return the receipt as it was stored; a redirect to the expense, which shows the check, if it is broken or changed
     */
    @GetMapping("/{id}/file")
    public ResponseEntity<byte[]> download(@PathVariable long id) {
        DocumentFile file;
        try {
            file = expenses.file(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        } catch (ExpenseException _) {
            return ResponseEntity.status(HttpStatus.SEE_OTHER).location(URI.create(EXPENSE_PATH + "/" + id)).build();
        }
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(file.mediaType()))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(file.filename(), StandardCharsets.UTF_8).build().toString())
                .body(file.content());
    }

    private Expense found(long id) {
        return expenses.find(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    private String page(Model model, Expense expense, ExpenseForm form) {
        model.addAttribute("expense", expense);
        model.addAttribute(FORM, form);
        model.addAttribute("categories", ExpenseCategory.values());
        model.addAttribute("integrity", expense == null ? List.of() : expenses.problems(expense.id()));
        return EDIT;
    }

    /** Reads the upload, unless it is too large: the upload limit serves the backup import and is far larger. */
    private static byte[] bytes(MultipartFile file) throws IOException {
        if (file == null) {
            return new byte[0];
        }
        if (file.getSize() > DocumentService.MAX_BYTES) {
            throw new ExpenseException(List.of(DocumentService.TOO_LARGE));
        }
        return file.getBytes();
    }
}
