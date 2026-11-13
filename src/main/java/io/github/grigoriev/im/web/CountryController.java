package io.github.grigoriev.im.web;

import io.github.grigoriev.im.country.Country;
import io.github.grigoriev.im.country.CountryService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** The page "Einstellungen", tab "Länder": the countries that the forms offer, for the admin only. */
@Controller
@RequestMapping("/admin/laender")
public class CountryController {

    static final String VIEW = "settings/countries";
    static final String BACK = "redirect:/admin/laender";

    private static final String SUCCESS = "success";

    private final CountryService countries;

    /**
     * Creates the controller.
     *
     * @param countries the country service
     */
    public CountryController(CountryService countries) {
        this.countries = countries;
    }

    /**
     * Shows the short list and the countries to add.
     *
     * @param model the page model
     * @return the template name
     */
    @GetMapping
    public String show(Model model) {
        model.addAttribute("enabled", countries.enabled());
        model.addAttribute("others", countries.others());
        return VIEW;
    }

    /**
     * Adds a country to the short list.
     *
     * @param code the ISO code
     * @param flash messages for the next page
     * @return a redirect to the page
     */
    @PostMapping
    public String add(@RequestParam String code, RedirectAttributes flash) {
        try {
            Country country = countries.add(code);
            flash.addFlashAttribute(SUCCESS, country.name() + " hinzugefügt.");
        } catch (IllegalArgumentException _) {
            flash.addFlashAttribute("error", "Dieses Land gibt es nicht.");
        }
        return BACK;
    }

    /**
     * Removes a country from the short list.
     *
     * @param code the ISO code
     * @param flash messages for the next page
     * @return a redirect to the page
     */
    @PostMapping("/{code}/entfernen")
    public String remove(@PathVariable String code, RedirectAttributes flash) {
        flash.addFlashAttribute(SUCCESS, countries.remove(code).name() + " entfernt.");
        return BACK;
    }
}
