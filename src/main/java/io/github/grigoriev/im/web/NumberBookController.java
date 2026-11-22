package io.github.grigoriev.im.web;

import io.github.grigoriev.im.numbering.ManualNumbers;
import io.github.grigoriev.im.seller.SettingsException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * The page "Nummernkreis" of a year before the app: its numbers by hand, the gaps with their reasons and the closing of
 * the year (docs/PLAN.md, section 7, "External invoices"). Both roles use it.
 */
@Controller
@RequestMapping("/numbers/{year}")
public class NumberBookController {

    static final String VIEW = "numbers/book";

    private static final String SUCCESS = "success";
    private static final String ERROR = "error";
    private static final String BACK = "redirect:/numbers/";

    private final ManualNumbers manual;

    /**
     * Creates the controller.
     *
     * @param manual the numbers by hand
     */
    public NumberBookController(ManualNumbers manual) {
        this.manual = manual;
    }

    /**
     * Shows the numbers of a year.
     *
     * @param year the year
     * @param model the page model
     * @return the template name
     */
    @GetMapping
    public String show(@PathVariable int year, Model model) {
        if (year < ManualNumbers.FIRST_YEAR || year > ManualNumbers.LAST_YEAR) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        ManualNumbers.Book book = manual.book(year);
        model.addAttribute("book", book);
        model.addAttribute("gaps", book.entries().stream().filter(ManualNumbers.Entry::gap).count());
        model.addAttribute("open", book.openGaps().size());
        return VIEW;
    }

    /**
     * Writes the reason of a gap.
     *
     * @param year the year
     * @param counter the missing counter
     * @param reason why the number has no invoice
     * @param flash messages for the next page
     * @return a redirect to the page
     */
    @PostMapping("/gaps")
    public String explain(@PathVariable int year, @RequestParam int counter, @RequestParam(required = false) String reason,
            RedirectAttributes flash) {
        try {
            manual.explainGap(year, counter, reason);
            flash.addFlashAttribute(SUCCESS, "Lücke %d-%02d begründet.".formatted(year, counter));
        } catch (SettingsException e) {
            flash.addFlashAttribute(ERROR, e.getMessage());
        }
        return BACK + year;
    }

    /**
     * Closes the year.
     *
     * @param year the year
     * @param flash messages for the next page
     * @return a redirect to the page
     */
    @PostMapping("/close")
    public String close(@PathVariable int year, RedirectAttributes flash) {
        try {
            int next = manual.close(year);
            flash.addFlashAttribute(SUCCESS, "Das Jahr %d ist abgeschlossen. Der Zähler macht mit %d weiter."
                    .formatted(year, next));
        } catch (SettingsException e) {
            flash.addFlashAttribute(ERROR, e.getMessage());
        }
        return BACK + year;
    }
}
