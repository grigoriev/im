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
@RequestMapping("/admin/e-mail")
public class MailTextController {

    static final String VIEW = "settings/mail";

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
        model.addAttribute("subject", text.subject());
        model.addAttribute("body", text.body());
        return VIEW;
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
            model.addAttribute("error", e.getMessage());
            model.addAttribute("subject", subject);
            model.addAttribute("body", body);
            return VIEW;
        }
        flash.addFlashAttribute("success", "E-Mail-Text gespeichert.");
        return "redirect:/admin/e-mail";
    }
}
