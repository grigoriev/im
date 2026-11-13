package io.github.grigoriev.im.web;

import io.github.grigoriev.im.einvoice.TaxCategory;
import io.github.grigoriev.im.seller.SettingsException;
import io.github.grigoriev.im.seller.TaxNoteService;
import java.util.EnumMap;
import java.util.Map;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** The page "Einstellungen", tab "Steuerhinweise": the default tax notes, for the admin only. */
@Controller
@RequestMapping("/admin/steuerhinweise")
public class TaxNoteController {

    static final String VIEW = "settings/tax-notes";

    /** The field of a note in the form: this prefix and the name of the category. */
    static final String FIELD = "note-";

    /** The headings of the notes. */
    static final Map<TaxCategory, String> TITLES = Map.of(
            TaxCategory.KLEINUNTERNEHMER, "Kleinunternehmer (§ 19 UStG)",
            TaxCategory.EXEMPT_4_21, "Steuerfreie Bildungsleistung (§ 4 Nr. 21 UStG)",
            TaxCategory.REVERSE_CHARGE_EU, "Reverse Charge, Kunde in der EU",
            TaxCategory.NOT_TAXABLE_ABROAD, "Nicht steuerbar, Kunde außerhalb der EU");

    private final TaxNoteService notes;

    /**
     * Creates the controller.
     *
     * @param notes the tax note service
     */
    public TaxNoteController(TaxNoteService notes) {
        this.notes = notes;
    }

    /**
     * Shows the notes.
     *
     * @param model the page model
     * @return the template name
     */
    @GetMapping
    public String show(Model model) {
        model.addAttribute("notes", notes.notes());
        model.addAttribute("titles", TITLES);
        return VIEW;
    }

    /**
     * Saves the notes.
     *
     * @param form the form fields
     * @param flash messages for the next page
     * @return a redirect to the notes
     */
    @PostMapping
    public String save(@RequestParam Map<String, String> form, RedirectAttributes flash) {
        Map<TaxCategory, String> changes = new EnumMap<>(TaxCategory.class);
        TaxNoteService.CATEGORIES.stream().filter(category -> form.containsKey(FIELD + category.name()))
                .forEach(category -> changes.put(category, form.get(FIELD + category.name())));
        try {
            notes.save(changes);
            flash.addFlashAttribute("success", "Steuerhinweise gespeichert.");
        } catch (SettingsException e) {
            flash.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/steuerhinweise";
    }
}
