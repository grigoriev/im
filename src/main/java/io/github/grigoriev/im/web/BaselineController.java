package io.github.grigoriev.im.web;

import io.github.grigoriev.im.seller.SettingsException;
import io.github.grigoriev.im.threshold.ThresholdService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** The admin page "Vorjahre" of the settings: the §19 revenue of the years before the app. */
@Controller
@RequestMapping("/admin/vorjahre")
public class BaselineController {

    static final String VIEW = "settings/baseline";
    private static final String BACK = "redirect:/admin/vorjahre";

    private final ThresholdService threshold;

    /**
     * Creates the controller.
     *
     * @param threshold the baselines
     */
    public BaselineController(ThresholdService threshold) {
        this.threshold = threshold;
    }

    /**
     * Shows the baselines.
     *
     * @param model the page model
     * @return the template name
     */
    @GetMapping
    public String show(Model model) {
        model.addAttribute("baselines", threshold.baselines());
        model.addAttribute("defaultYear", threshold.newestBaselineYear());
        model.addAttribute("lastYear", threshold.today().getYear() - 1);
        return VIEW;
    }

    /**
     * Saves the baseline of a year; a blank amount removes it.
     *
     * @param year the year
     * @param amount the amount, German style
     * @param flash messages for the next page
     * @return a redirect to the page
     */
    @PostMapping
    public String save(@RequestParam int year, @RequestParam(required = false) String amount, RedirectAttributes flash) {
        try {
            threshold.saveBaseline(year, amount);
            flash.addFlashAttribute("success", "Vorjahresumsatz " + year + " gespeichert.");
        } catch (SettingsException e) {
            flash.addFlashAttribute("error", e.getMessage());
        }
        return BACK;
    }
}
