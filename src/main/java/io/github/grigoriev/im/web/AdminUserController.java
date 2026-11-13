package io.github.grigoriev.im.web;

import io.github.grigoriev.im.user.Role;
import io.github.grigoriev.im.user.UserException;
import io.github.grigoriev.im.user.UserService;
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

    static final String VIEW = "admin/users";
    static final String BACK = "redirect:/admin/benutzer";

    private final UserService users;

    /**
     * Creates the controller.
     *
     * @param users the user service
     */
    public AdminUserController(UserService users) {
        this.users = users;
    }

    /**
     * Lists the users.
     *
     * @param model the page model
     * @return the template name
     */
    @GetMapping
    public String list(Model model) {
        model.addAttribute("users", users.list());
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
            flash.addFlashAttribute("success", "Benutzer „" + username + "“ angelegt.");
        } catch (UserException e) {
            flash.addFlashAttribute("error", e.getMessage());
            flash.addFlashAttribute("username", username);
            flash.addFlashAttribute("displayName", displayName);
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
            flash.addFlashAttribute("success", "Neues Passwort für „" + username + "“ gesetzt.");
        } catch (UserException e) {
            flash.addFlashAttribute("error", e.getMessage());
        }
        return BACK;
    }
}
