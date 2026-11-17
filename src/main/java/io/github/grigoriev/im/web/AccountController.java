package io.github.grigoriev.im.web;

import io.github.grigoriev.im.config.RecentLogin;
import io.github.grigoriev.im.config.SessionEpoch;
import io.github.grigoriev.im.user.PasskeyService;
import io.github.grigoriev.im.user.UserException;
import io.github.grigoriev.im.user.UserService;
import jakarta.servlet.http.HttpSession;
import java.security.Principal;
import org.springframework.security.core.Authentication;
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
@RequestMapping("/account")
public class AccountController {

    private static final String SUCCESS = "success";
    private static final String ERROR = "error";

    static final String VIEW = "account";
    static final String BACK = "redirect:/account";

    private final PasskeyService passkeys;
    private final UserService users;
    private final SessionEpoch sessions;
    private final RecentLogin recentLogin;

    /**
     * Creates the controller.
     *
     * @param passkeys the passkey service
     * @param users the user service
     * @param sessions ends the other sessions after a new password
     * @param recentLogin tells whether a new passkey may be added now
     */
    public AccountController(PasskeyService passkeys, UserService users, SessionEpoch sessions,
            RecentLogin recentLogin) {
        this.passkeys = passkeys;
        this.users = users;
        this.sessions = sessions;
        this.recentLogin = recentLogin;
    }

    /**
     * Shows the account page. A new passkey needs a recent login or the password confirmed first.
     *
     * @param authentication the logged-in user
     * @param session the session
     * @param model the page model
     * @return the template name
     */
    @GetMapping
    public String show(Authentication authentication, HttpSession session, Model model) {
        model.addAttribute("passkeys", passkeys.list(authentication.getName()));
        model.addAttribute("recentLogin", recentLogin.holds(authentication, session));
        return VIEW;
    }

    /**
     * Confirms the password before a new passkey. It counts for 15 minutes in this session.
     *
     * @param principal the logged-in user
     * @param session the session
     * @param password the password
     * @param flash messages for the next page
     * @return a redirect to the account page
     */
    @PostMapping("/confirm")
    public String confirm(Principal principal, HttpSession session, @RequestParam String password,
            RedirectAttributes flash) {
        try {
            users.confirmPassword(principal.getName(), password);
            recentLogin.confirm(session);
        } catch (UserException e) {
            flash.addFlashAttribute(ERROR, e.getMessage());
        }
        return BACK;
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
    @PostMapping("/passkeys/{id}/rename")
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
    @PostMapping("/passkeys/{id}/delete")
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
     * Changes the password. The other sessions of the user end, this one stays.
     *
     * @param principal the logged-in user
     * @param session the session that stays
     * @param current the current password
     * @param password the new password
     * @param confirm the new password once more
     * @param flash messages for the next page
     * @return a redirect to the account page
     */
    @PostMapping("/password")
    public String changePassword(Principal principal, HttpSession session, @RequestParam String current,
            @RequestParam String password, @RequestParam String confirm, RedirectAttributes flash) {
        if (!password.equals(confirm)) {
            flash.addFlashAttribute(ERROR, AdminUserController.MISMATCH);
        } else {
            try {
                users.changePassword(principal.getName(), current, password);
                sessions.endOthers(principal.getName(), session);
                flash.addFlashAttribute(SUCCESS, "Passwort geändert.");
            } catch (UserException e) {
                flash.addFlashAttribute(ERROR, e.getMessage());
            }
        }
        return BACK;
    }
}
