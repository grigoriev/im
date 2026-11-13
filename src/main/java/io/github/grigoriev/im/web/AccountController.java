package io.github.grigoriev.im.web;

import io.github.grigoriev.im.user.PasskeyService;
import io.github.grigoriev.im.user.UserException;
import io.github.grigoriev.im.user.UserService;
import java.security.Principal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * The page "Mein Konto": the passkeys of the logged-in user and the password change. The browser
 * registers a new passkey through the WebAuthn endpoints of Spring Security ({@code js/passkeys.js}).
 */
@Controller
@RequestMapping("/konto")
public class AccountController {

    private static final String SUCCESS = "success";
    private static final String ERROR = "error";

    static final String VIEW = "account";
    static final String BACK = "redirect:/konto";

    private final PasskeyService passkeys;
    private final UserService users;

    /**
     * Creates the controller.
     *
     * @param passkeys the passkey service
     * @param users the user service
     */
    public AccountController(PasskeyService passkeys, UserService users) {
        this.passkeys = passkeys;
        this.users = users;
    }

    /**
     * Shows the account page.
     *
     * @param principal the logged-in user
     * @param model the page model
     * @return the template name
     */
    @GetMapping
    public String show(Principal principal, Model model) {
        model.addAttribute("passkeys", passkeys.list(principal.getName()));
        return VIEW;
    }

    /**
     * Renames a passkey.
     *
     * @param principal the logged-in user
     * @param id the credential ID
     * @param label the new name
     * @param flash messages for the next page
     * @return a redirect to the account page
     */
    @PostMapping("/passkeys/{id}/name")
    public String rename(Principal principal, @PathVariable String id, @RequestParam String label,
            RedirectAttributes flash) {
        try {
            passkeys.rename(principal.getName(), id, label);
            flash.addFlashAttribute(SUCCESS, "Passkey umbenannt.");
        } catch (UserException e) {
            flash.addFlashAttribute(ERROR, e.getMessage());
        }
        return BACK;
    }

    /**
     * Removes a passkey.
     *
     * @param principal the logged-in user
     * @param id the credential ID
     * @param flash messages for the next page
     * @return a redirect to the account page
     */
    @PostMapping("/passkeys/{id}/loeschen")
    public String remove(Principal principal, @PathVariable String id, RedirectAttributes flash) {
        try {
            passkeys.remove(principal.getName(), id);
            flash.addFlashAttribute(SUCCESS, "Passkey entfernt.");
        } catch (UserException e) {
            flash.addFlashAttribute(ERROR, e.getMessage());
        }
        return BACK;
    }

    /**
     * Changes the password.
     *
     * @param principal the logged-in user
     * @param current the current password
     * @param password the new password
     * @param flash messages for the next page
     * @return a redirect to the account page
     */
    @PostMapping("/passwort")
    public String changePassword(Principal principal, @RequestParam String current, @RequestParam String password,
            RedirectAttributes flash) {
        try {
            users.changePassword(principal.getName(), current, password);
            flash.addFlashAttribute(SUCCESS, "Passwort geändert.");
        } catch (UserException e) {
            flash.addFlashAttribute(ERROR, e.getMessage());
        }
        return BACK;
    }
}
