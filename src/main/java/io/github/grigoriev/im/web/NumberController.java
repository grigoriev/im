package io.github.grigoriev.im.web;

import io.github.grigoriev.im.numbering.NumberSequence;
import io.github.grigoriev.im.numbering.NumberService;
import io.github.grigoriev.im.seller.SettingsException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** The page "Einstellungen", tab "Nummernkreis": the pattern and the start values, for the admin only. */
@Controller
@RequestMapping("/admin/nummernkreis")
public class NumberController {

    static final String VIEW = "settings/numbers";
    static final String BACK = "redirect:/admin/nummernkreis";

    private static final String SUCCESS = "success";
    private static final String ERROR = "error";

    private final NumberService numbers;

    /**
     * Creates the controller.
     *
     * @param numbers the number service
     */
    public NumberController(NumberService numbers) {
        this.numbers = numbers;
    }

    /**
     * Shows the pattern and the next number of the current and the next year.
     *
     * @param model the page model
     * @return the template name
     */
    @GetMapping
    public String show(Model model) {
        Map<NumberSequence, String> years = new LinkedHashMap<>();
        numbers.years().forEach(sequence -> years.put(sequence, numbers.preview(sequence)));
        model.addAttribute("pattern", numbers.pattern().value());
        model.addAttribute("years", years);
        return VIEW;
    }

    /**
     * Saves the pattern.
     *
     * @param pattern the pattern as entered
     * @param flash messages for the next page
     * @return a redirect to the page
     */
    @PostMapping("/muster")
    public String savePattern(@RequestParam String pattern, RedirectAttributes flash) {
        try {
            numbers.savePattern(pattern);
            flash.addFlashAttribute(SUCCESS, "Muster gespeichert.");
        } catch (SettingsException e) {
            flash.addFlashAttribute(ERROR, e.getMessage());
        }
        return BACK;
    }

    /**
     * Sets the start value of a year.
     *
     * @param year the year
     * @param start the counter of the next invoice
     * @param flash messages for the next page
     * @return a redirect to the page
     */
    @PostMapping("/start")
    public String setStart(@RequestParam int year, @RequestParam int start, RedirectAttributes flash) {
        try {
            numbers.setStart(year, start);
            flash.addFlashAttribute(SUCCESS, "Startnummer für " + year + " gespeichert.");
        } catch (SettingsException e) {
            flash.addFlashAttribute(ERROR, e.getMessage());
        }
        return BACK;
    }
}
