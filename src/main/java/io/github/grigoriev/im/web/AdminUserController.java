package io.github.grigoriev.im.web;

import io.github.grigoriev.im.config.SessionEpoch;
import io.github.grigoriev.im.user.AppUser;
import io.github.grigoriev.im.user.PasskeyService;
import io.github.grigoriev.im.user.Role;
import io.github.grigoriev.im.user.UserException;
import io.github.grigoriev.im.user.UserService;
import jakarta.servlet.http.HttpSession;
import java.security.Principal;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** The admin page "Benutzer": list users, create a user, reset a password. */
@Controller
@RequestMapping("/admin/users")
public class AdminUserController {

    private static final String USERNAME = "username";
    private static final String DISPLAY_NAME = "displayName";

    /** The problem when the two password fields differ. */
    static final String MISMATCH = "Die beiden Passwörter stimmen nicht überein.";

    private static final String SUCCESS = "success";
    private static final String ERROR = "error";

    static final String VIEW = "admin/users";
    static final String BACK = "redirect:/admin/users";

    private final UserService users;
    private final PasskeyService passkeys;
    private final SessionEpoch sessions;

    /**
     * Creates the controller.
     *
     * @param users the user service
     * @param passkeys the passkey service
     * @param sessions ends the sessions of a deleted user
     */
    public AdminUserController(UserService users, PasskeyService passkeys, SessionEpoch sessions) {
        this.users = users;
        this.passkeys = passkeys;
        this.sessions = sessions;
    }

    /**
     * Lists the users.
     *
     * @param model the page model
     * @return the template name
     */
    @GetMapping
    public String list(Model model) {
        var all = users.list();
        Map<String, Integer> counts = all.stream().map(AppUser::username)
                .collect(Collectors.toMap(Function.identity(), passkeys::count));
        model.addAttribute("users", all);
        model.addAttribute("passkeyCounts", counts);
        model.addAttribute("roles", Role.values());
        return VIEW;
    }

    /**
     * Creates a user.
     *
     * @param username login name
     * @param displayName name shown in the app
     * @param password the password
     * @param confirm the password once more
     * @param role the role
     * @param flash messages for the next page
     * @return a redirect to the list
     */
    @PostMapping
    public String create(@RequestParam String username, @RequestParam String displayName,
            @RequestParam String password, @RequestParam String confirm, @RequestParam Role role,
            RedirectAttributes flash) {
        if (!password.equals(confirm)) {
            flash.addFlashAttribute(ERROR, MISMATCH);
            flash.addFlashAttribute(USERNAME, username);
            flash.addFlashAttribute(DISPLAY_NAME, displayName);
        } else {
            try {
                users.create(username, displayName, password, role);
                flash.addFlashAttribute(SUCCESS, "Benutzer „" + username + "“ angelegt.");
            } catch (UserException e) {
                flash.addFlashAttribute(ERROR, e.getMessage());
                flash.addFlashAttribute(USERNAME, username);
                flash.addFlashAttribute(DISPLAY_NAME, displayName);
            }
        }
        return BACK;
    }

    /**
     * Removes every passkey of a user, for example after a lost phone.
     *
     * @param id the user
     * @param flash messages for the next page
     * @return a redirect to the list
     */
    @PostMapping("/{id}/passkeys/delete")
    public String removePasskeys(@PathVariable long id, RedirectAttributes flash) {
        try {
            String username = users.get(id).username();
            int removed = passkeys.removeAll(username);
            flash.addFlashAttribute(SUCCESS, removed + " Passkey(s) von „" + username + "“ entfernt.");
        } catch (UserException e) {
            flash.addFlashAttribute(ERROR, e.getMessage());
        }
        return BACK;
    }

    /**
     * Deletes a user. The sessions of the user end in every browser.
     *
     * @param id the user
     * @param flash messages for the next page
     * @return a redirect to the list
     */
    @PostMapping("/{id}/delete")
    public String delete(@PathVariable long id, RedirectAttributes flash) {
        try {
            AppUser deleted = users.delete(id);
            sessions.end(deleted.username());
            flash.addFlashAttribute(SUCCESS, "Benutzer „" + deleted.username() + "“ gelöscht.");
        } catch (UserException e) {
            flash.addFlashAttribute(ERROR, e.getMessage());
        }
        return BACK;
    }

    /**
     * Sets a new password. The sessions of the user end in every browser, except this one if the admin resets the own
     * password.
     *
     * @param principal the logged-in admin
     * @param session the session of the admin
     * @param id the user
     * @param password the new password
     * @param confirm the new password once more
     * @param flash messages for the next page
     * @return a redirect to the list
     */
    @PostMapping("/{id}/password")
    public String resetPassword(Principal principal, HttpSession session, @PathVariable long id,
            @RequestParam String password, @RequestParam String confirm, RedirectAttributes flash) {
        if (!password.equals(confirm)) {
            flash.addFlashAttribute(ERROR, MISMATCH);
        } else {
            try {
                String username = users.resetPassword(id, password).username();
                if (username.equals(principal.getName())) {
                    sessions.endOthers(username, session);
                } else {
                    sessions.end(username);
                }
                flash.addFlashAttribute(SUCCESS, "Neues Passwort für „" + username + "“ gesetzt.");
            } catch (UserException e) {
                flash.addFlashAttribute(ERROR, e.getMessage());
            }
        }
        return BACK;
    }
}
