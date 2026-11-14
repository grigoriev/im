package io.github.grigoriev.im.web;

import io.github.grigoriev.im.country.CountryService;
import io.github.grigoriev.im.seller.ProfilePart;
import io.github.grigoriev.im.seller.SellerProfile;
import io.github.grigoriev.im.seller.SellerService;
import io.github.grigoriev.im.seller.SettingsException;
import io.github.grigoriev.im.seller.TaxStatus;
import java.util.Arrays;
import java.util.Map;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * The page "Einstellungen", tab "Profil": the seller as every invoice shows it, in four tabs that save on their own.
 * Both roles edit it.
 */
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
     * Shows one tab of the profile, an empty form before the first save.
     *
     * @param teil the slug of the tab, the address if missing or unknown
     * @param model the page model
     * @return the template name
     */
    @GetMapping
    public String show(@RequestParam(required = false) String teil, Model model) {
        ProfilePart part = ProfilePart.of(teil).orElse(ProfilePart.ADDRESS);
        SellerProfile stored = sellers.find().orElseGet(SellerProfile::empty);
        return form(model, part, stored, stored.withNameAsDefault(), null);
    }

    /**
     * Saves one tab of the profile; the other tabs stay as stored. On a problem the tab shows again with the entered
     * values and the problem at each field.
     *
     * @param teil the slug of the tab
     * @param profile the tab as entered; the fields of other tabs are empty
     * @param model the page model
     * @param flash messages for the next page
     * @return a redirect to the tab, or the tab with the problems
     */
    @PostMapping
    public String save(@RequestParam String teil, @ModelAttribute("profile") SellerProfile profile, Model model,
            RedirectAttributes flash) {
        ProfilePart part = ProfilePart.of(teil).orElse(ProfilePart.ADDRESS);
        try {
            SellerProfile saved = sellers.save(part, profile);
            Map<String, String> missing = SellerService.missing(saved);
            flash.addFlashAttribute("success", part.label() + " gespeichert." + (missing.isEmpty() ? ""
                    : " Für Rechnungen fehlt noch: " + String.join(", ", missing.values()) + "."));
            return "redirect:/einstellungen?teil=" + part.slug();
        } catch (SettingsException e) {
            model.addAttribute("invalid", e.fields());
            SellerProfile stored = sellers.find().orElseGet(SellerProfile::empty);
            return form(model, part, part.merge(stored, profile), part.merge(stored, profile), profile.countryCode());
        }
    }

    /**
     * Shows a tab. The tabs with an empty field that invoices need carry a mark. The stored country and the submitted
     * one stay in the select, even if the admin removed them.
     */
    private String form(Model model, ProfilePart part, SellerProfile state, SellerProfile shown, String submitted) {
        Map<String, String> missing = SellerService.missing(state);
        model.addAttribute("part", part);
        model.addAttribute("parts", ProfilePart.values());
        model.addAttribute("incomplete", Arrays.stream(ProfilePart.values())
                .filter(each -> each.fields().stream().anyMatch(missing::containsKey)).toList());
        model.addAttribute("profile", shown);
        model.addAttribute("missing", missing);
        model.addAttribute("statuses", TaxStatus.values());
        model.addAttribute("countries",
                countries.options(sellers.find().map(SellerProfile::countryCode).orElse(null), submitted));
        return VIEW;
    }
}
