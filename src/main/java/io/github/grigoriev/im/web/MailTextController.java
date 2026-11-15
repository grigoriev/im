package io.github.grigoriev.im.web;

import io.github.grigoriev.im.mail.MailText;
import io.github.grigoriev.im.mail.MailTextService;
import io.github.grigoriev.im.seller.SettingsException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** The admin page "E-Mail" of the settings: the default text of the e-mail with an invoice. */
@Controller
@RequestMapping("/admin/mail")
public class MailTextController {

    static final String VIEW = "settings/mail";
    private static final String SUBJECT = "subject";
    private static final String BODY = "body";
    private static final String ERROR = "error";
    private static final String SUCCESS = "success";
    private static final String BACK = "redirect:/admin/mail";

    private final MailTextService texts;

    /**
     * Creates the controller.
     *
     * @param texts the default e-mail text
     */
    public MailTextController(MailTextService texts) {
        this.texts = texts;
    }

    /**
     * Shows the default text.
     *
     * @param model the page model
     * @return the template name
     */
    @GetMapping
    public String show(Model model) {
        MailText text = texts.defaults();
        model.addAttribute(SUBJECT, text.subject());
        model.addAttribute(BODY, text.body());
        reminder(model);
        return VIEW;
    }

    private void reminder(Model model) {
        MailText reminder = texts.reminderDefaults();
        model.addAttribute("reminderSubject", reminder.subject());
        model.addAttribute("reminderBody", reminder.body());
    }

    /**
     * Saves the default text.
     *
     * @param subject the subject
     * @param body the text
     * @param model the page model
     * @param flash messages for the next page
     * @return a redirect to the page, or the page with the problems
     */
    @PostMapping
    public String save(@RequestParam(required = false) String subject, @RequestParam(required = false) String body,
            Model model, RedirectAttributes flash) {
        try {
            texts.save(subject, body);
        } catch (SettingsException e) {
            model.addAttribute(ERROR, e.getMessage());
            model.addAttribute(SUBJECT, subject);
            model.addAttribute(BODY, body);
            reminder(model);
            return VIEW;
        }
        flash.addFlashAttribute(SUCCESS, "E-Mail-Text gespeichert.");
        return BACK;
    }

    /**
     * Saves the default text of a payment reminder.
     *
     * @param subject the subject
     * @param body the text
     * @param model the page model
     * @param flash messages for the next page
     * @return a redirect to the page, or the page with the problems
     */
    @PostMapping("/reminder")
    public String saveReminder(@RequestParam(required = false) String subject,
            @RequestParam(required = false) String body, Model model, RedirectAttributes flash) {
        try {
            texts.saveReminder(subject, body);
        } catch (SettingsException e) {
            MailText text = texts.defaults();
            model.addAttribute(ERROR, e.getMessage());
            model.addAttribute(SUBJECT, text.subject());
            model.addAttribute(BODY, text.body());
            model.addAttribute("reminderSubject", subject);
            model.addAttribute("reminderBody", body);
            return VIEW;
        }
        flash.addFlashAttribute(SUCCESS, "Text der Zahlungserinnerung gespeichert.");
        return BACK;
    }
}
