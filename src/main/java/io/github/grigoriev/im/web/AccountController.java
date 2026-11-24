package io.github.grigoriev.im.web;

import io.github.grigoriev.im.api.ApiTokenException;
import io.github.grigoriev.im.api.ApiTokenScope;
import io.github.grigoriev.im.api.ApiTokenService;
import io.github.grigoriev.im.api.CreatedToken;
import io.github.grigoriev.im.config.RecentLogin;
import io.github.grigoriev.im.config.SessionEpoch;
import io.github.grigoriev.im.user.PasskeyService;
import io.github.grigoriev.im.user.UserException;
import io.github.grigoriev.im.user.UserService;
import jakarta.servlet.http.HttpSession;
import java.security.Principal;
import java.time.Clock;
import java.util.List;
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
 * The page "Mein Konto": the passkeys and API tokens of the logged-in user and the password change. The browser
 * registers a new passkey through the WebAuthn endpoints of Spring Security ({@code js/passkeys.js}). A new passkey
 * and a new API token need a recent login.
 */
@Controller
@RequestMapping("/account")
public class AccountController {

    private static final String SUCCESS = "success";
    private static final String ERROR = "error";

    static final String VIEW = "account";
    static final String BACK = "redirect:/account";
    /** The lives of a new token to choose from, in days. */
    static final List<Integer> TOKEN_DAYS = List.of(30, 90, 365);
    static final String CONFIRM_FIRST = "Bestätigen Sie zuerst Ihr Passwort.";

    private final PasskeyService passkeys;
    private final UserService users;
    private final SessionEpoch sessions;
    private final RecentLogin recentLogin;
    private final ApiTokenService tokens;
    private final Clock clock;

    /**
     * Creates the controller.
     *
     * @param passkeys the passkey service
     * @param users the user service
     * @param sessions ends the other sessions after a new password
     * @param recentLogin tells whether a new passkey or token may be added now
     * @param tokens the API tokens
     * @param clock the time now, which tells the expired tokens
     */
    public AccountController(PasskeyService passkeys, UserService users, SessionEpoch sessions,
            RecentLogin recentLogin, ApiTokenService tokens, Clock clock) {
        this.passkeys = passkeys;
        this.users = users;
        this.sessions = sessions;
        this.recentLogin = recentLogin;
        this.tokens = tokens;
        this.clock = clock;
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
        model.addAttribute("tokens", tokens.list(authentication.getName()));
        model.addAttribute("tokenScopes", ApiTokenScope.values());
        model.addAttribute("tokenDays", TOKEN_DAYS);
        model.addAttribute("now", clock.instant());
        return VIEW;
    }

    /**
     * Creates an API token. It needs a recent login, as a new passkey; the next page shows the token once.
     *
     * @param authentication the logged-in user
     * @param session the session
     * @param name the name of the token
     * @param scope what it may do
     * @param days its life in days
     * @param flash messages for the next page
     * @return a redirect to the account page
     */
    @PostMapping("/tokens")
    public String createToken(Authentication authentication, HttpSession session, @RequestParam String name,
            @RequestParam ApiTokenScope scope, @RequestParam int days, RedirectAttributes flash) {
        if (recentLogin.holds(authentication, session)) {
            try {
                CreatedToken created = tokens.create(authentication.getName(), name, scope, days);
                flash.addFlashAttribute("newToken", created.secret());
                flash.addFlashAttribute(SUCCESS, "API-Token „" + created.token().name() + "“ angelegt.");
            } catch (ApiTokenException e) {
                flash.addFlashAttribute(ERROR, e.getMessage());
            }
        } else {
            flash.addFlashAttribute(ERROR, CONFIRM_FIRST);
        }
        return BACK;
    }

    /**
     * Deletes an API token. It stops working at once.
     *
     * @param principal the logged-in user
     * @param id the ID of the token
     * @param flash messages for the next page
     * @return a redirect to the account page
     */
    @PostMapping("/tokens/{id}/delete")
    public String deleteToken(Principal principal, @PathVariable long id, RedirectAttributes flash) {
        try {
            tokens.delete(principal.getName(), id);
            flash.addFlashAttribute(SUCCESS, "API-Token gelöscht.");
        } catch (ApiTokenException e) {
            flash.addFlashAttribute(ERROR, e.getMessage());
        }
        return BACK;
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
