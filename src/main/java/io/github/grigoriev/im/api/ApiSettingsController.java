package io.github.grigoriev.im.api;

import io.github.grigoriev.im.einvoice.TaxCategory;
import io.github.grigoriev.im.mail.MailText;
import io.github.grigoriev.im.mail.MailTextService;
import io.github.grigoriev.im.numbering.NumberService;
import io.github.grigoriev.im.seller.ProfilePart;
import io.github.grigoriev.im.seller.SellerProfile;
import io.github.grigoriev.im.seller.SellerService;
import io.github.grigoriev.im.seller.TaxNoteService;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The settings in the API, with the checks of their forms: the profile of the seller, the e-mail texts and, for the
 * admin, the tax notes and the number pattern.
 */
@RestController
@RequestMapping("/api/v1")
public class ApiSettingsController {

    private final SellerService sellers;
    private final MailTextService mailTexts;
    private final TaxNoteService taxNotes;
    private final NumberService numbers;

    /**
     * The e-mail texts of both kinds.
     *
     * @param invoice the text of an invoice
     * @param reminder the text of a payment reminder
     */
    public record MailTexts(MailText invoice, MailText reminder) {
    }

    /**
     * The number pattern with the next numbers it gives.
     *
     * @param pattern the pattern, for example {@code {YYYY}-{NN}}
     * @param next the next number of this year and of the next
     */
    public record NumberPatternView(String pattern, Map<Integer, String> next) {
    }

    /**
     * Creates the controller.
     *
     * @param sellers the profile of the seller
     * @param mailTexts the e-mail texts
     * @param taxNotes the default tax notes
     * @param numbers the number pattern
     */
    public ApiSettingsController(SellerService sellers, MailTextService mailTexts, TaxNoteService taxNotes,
            NumberService numbers) {
        this.sellers = sellers;
        this.mailTexts = mailTexts;
        this.taxNotes = taxNotes;
        this.numbers = numbers;
    }

    /**
     * Returns the profile of the seller.
     *
     * @return the profile
     * @throws NotFoundException if nobody filled it in yet
     */
    @GetMapping("/profile")
    public SellerProfile profile() {
        return sellers.find().orElseThrow(() -> new NotFoundException("Noch kein Profil."));
    }

    /**
     * Saves a tab of the profile: {@code address}, {@code contact}, {@code tax} or {@code bank}. The fields of the
     * other tabs in the body are ignored.
     *
     * @param part the tab
     * @param input the profile
     * @return the saved profile
     * @throws NotFoundException for an unknown tab
     */
    @PutMapping("/profile/{part}")
    public SellerProfile saveProfile(@PathVariable String part, @RequestBody SellerProfile input) {
        ProfilePart tab = ProfilePart.of(part).orElseThrow(() -> new NotFoundException(
                "Diesen Teil des Profils gibt es nicht: address, contact, tax oder bank."));
        return sellers.save(tab, input);
    }

    /**
     * Returns the default e-mail texts.
     *
     * @return the texts of an invoice and of a payment reminder
     */
    @GetMapping("/mail-texts")
    public MailTexts mailTexts() {
        return new MailTexts(mailTexts.defaults(), mailTexts.reminderDefaults());
    }

    /**
     * Saves the default e-mail text of an invoice.
     *
     * @param text subject and body
     * @return both texts
     */
    @PutMapping("/mail-texts/invoice")
    public MailTexts saveInvoiceText(@RequestBody MailText text) {
        mailTexts.save(text.subject(), text.body());
        return mailTexts();
    }

    /**
     * Saves the default e-mail text of a payment reminder.
     *
     * @param text subject and body
     * @return both texts
     */
    @PutMapping("/mail-texts/reminder")
    public MailTexts saveReminderText(@RequestBody MailText text) {
        mailTexts.saveReminder(text.subject(), text.body());
        return mailTexts();
    }

    /**
     * Returns the default tax note of each category. Admin only.
     *
     * @return the notes by category
     */
    @GetMapping("/admin/tax-notes")
    public Map<TaxCategory, String> taxNotes() {
        return taxNotes.notes();
    }

    /**
     * Changes default tax notes. Admin only. Categories left out keep their note.
     *
     * @param changes the new notes by category
     * @return all notes
     */
    @PutMapping("/admin/tax-notes")
    public Map<TaxCategory, String> saveTaxNotes(@RequestBody Map<TaxCategory, String> changes) {
        taxNotes.save(changes);
        return taxNotes.notes();
    }

    /**
     * Returns the number pattern. Admin only.
     *
     * @return the pattern and the next numbers
     */
    @GetMapping("/admin/number-pattern")
    public NumberPatternView numberPattern() {
        Map<Integer, String> next = new TreeMap<>();
        numbers.years().forEach(sequence -> next.put(sequence.year(), numbers.preview(sequence)));
        return new NumberPatternView(numbers.pattern().value(), next);
    }

    /**
     * Changes the number pattern. Admin only.
     *
     * @param input the new pattern; {@code next} is ignored
     * @return the pattern and the next numbers
     */
    @PutMapping("/admin/number-pattern")
    public NumberPatternView saveNumberPattern(@RequestBody NumberPatternView input) {
        numbers.savePattern(input.pattern());
        return numberPattern();
    }
}
