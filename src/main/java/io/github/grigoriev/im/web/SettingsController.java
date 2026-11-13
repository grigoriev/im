package io.github.grigoriev.im.web;

import io.github.grigoriev.im.country.CountryService;
import io.github.grigoriev.im.seller.SellerProfile;
import io.github.grigoriev.im.seller.SellerService;
import io.github.grigoriev.im.seller.SettingsException;
import io.github.grigoriev.im.seller.TaxStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** The page "Einstellungen", tab "Profil": the seller as every invoice shows it. Both roles edit it. */
@Controller
@RequestMapping("/einstellungen")
public class SettingsController {

    static final String VIEW = "settings/profile";

    private final SellerService sellers;
    private final CountryService countries;

    /**
     * Creates the controller.
     *
     * @param sellers the seller service
     * @param countries the countries of the form
     */
    public SettingsController(SellerService sellers, CountryService countries) {
        this.sellers = sellers;
        this.countries = countries;
    }

    /**
     * Shows the profile, an empty form before the first save.
     *
     * @param model the page model
     * @return the template name
     */
    @GetMapping
    public String show(Model model) {
        return form(model, sellers.find().orElseGet(SellerProfile::empty));
    }

    /**
     * Saves the profile. On a problem the form shows again with the entered values.
     *
     * @param profile the profile as entered
     * @param model the page model
     * @param flash messages for the next page
     * @return a redirect to the profile, or the form with the problems
     */
    @PostMapping
    public String save(@ModelAttribute("profile") SellerProfile profile, Model model, RedirectAttributes flash) {
        try {
            sellers.save(profile);
            flash.addFlashAttribute("success", "Profil gespeichert.");
            return "redirect:/einstellungen";
        } catch (SettingsException e) {
            model.addAttribute("problems", e.problems());
            return form(model, profile);
        }
    }

    private String form(Model model, SellerProfile profile) {
        model.addAttribute("profile", profile);
        model.addAttribute("statuses", TaxStatus.values());
        model.addAttribute("countries", countries.options(sellers.find().map(SellerProfile::countryCode).orElse(null)));
        return VIEW;
    }
}
