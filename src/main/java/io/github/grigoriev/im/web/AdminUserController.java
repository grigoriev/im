package io.github.grigoriev.im.web;

import io.github.grigoriev.im.user.AppUser;
import io.github.grigoriev.im.user.PasskeyService;
import io.github.grigoriev.im.user.Role;
import io.github.grigoriev.im.user.UserException;
import io.github.grigoriev.im.user.UserService;
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
@RequestMapping("/admin/benutzer")
public class AdminUserController {

    private static final String SUCCESS = "success";
    private static final String ERROR = "error";

    static final String VIEW = "admin/users";
    static final String BACK = "redirect:/admin/benutzer";

    private final UserService users;
    private final PasskeyService passkeys;

    /**
     * Creates the controller.
     *
     * @param users the user service
     * @param passkeys the passkey service
     */
    public AdminUserController(UserService users, PasskeyService passkeys) {
        this.users = users;
        this.passkeys = passkeys;
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
     * @param role the role
     * @param flash messages for the next page
     * @return a redirect to the list
     */
    @PostMapping
    public String create(@RequestParam String username, @RequestParam String displayName,
            @RequestParam String password, @RequestParam Role role, RedirectAttributes flash) {
        try {
            users.create(username, displayName, password, role);
            flash.addFlashAttribute(SUCCESS, "Benutzer „" + username + "“ angelegt.");
        } catch (UserException e) {
            flash.addFlashAttribute(ERROR, e.getMessage());
            flash.addFlashAttribute("username", username);
            flash.addFlashAttribute("displayName", displayName);
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
    @PostMapping("/{id}/passkeys/loeschen")
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
     * Sets a new password.
     *
     * @param id the user
     * @param password the new password
     * @param flash messages for the next page
     * @return a redirect to the list
     */
    @PostMapping("/{id}/passwort")
    public String resetPassword(@PathVariable long id, @RequestParam String password, RedirectAttributes flash) {
        try {
            String username = users.resetPassword(id, password).username();
            flash.addFlashAttribute(SUCCESS, "Neues Passwort für „" + username + "“ gesetzt.");
        } catch (UserException e) {
            flash.addFlashAttribute(ERROR, e.getMessage());
        }
        return BACK;
    }
}
