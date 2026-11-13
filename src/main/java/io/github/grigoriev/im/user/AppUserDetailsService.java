package io.github.grigoriev.im.user;

import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/** Loads a user for the password and the remember-me login. */
@Service
public class AppUserDetailsService implements UserDetailsService {

    private final AppUserRepository users;

    /**
     * Creates the service.
     *
     * @param users the user store
     */
    public AppUserDetailsService(AppUserRepository users) {
        this.users = users;
    }

    @Override
    public UserDetails loadUserByUsername(String username) {
        AppUser user = users.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("No user " + username));
        return User.withUsername(user.username())
                .password(user.passwordHash())
                .authorities(user.role().authority())
                .disabled(!user.enabled())
                .build();
    }
}
