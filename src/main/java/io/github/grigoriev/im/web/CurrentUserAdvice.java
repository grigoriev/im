package io.github.grigoriev.im.web;

import io.github.grigoriev.im.user.AppUser;
import io.github.grigoriev.im.user.AppUserRepository;
import java.security.Principal;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/** Puts the logged-in user into every page model, for the name and the role in the app shell. */
@ControllerAdvice
public class CurrentUserAdvice {

    private final AppUserRepository users;

    /**
     * Creates the advice.
     *
     * @param users the user store
     */
    public CurrentUserAdvice(AppUserRepository users) {
        this.users = users;
    }

    /**
     * Returns the logged-in user.
     *
     * @param principal the authenticated principal, {@code null} on public pages
     * @return the user, or {@code null} when nobody is logged in
     */
    @ModelAttribute("currentUser")
    public AppUser currentUser(Principal principal) {
        return principal == null ? null : users.findByUsername(principal.getName()).orElse(null);
    }
}
