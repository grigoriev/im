package io.github.grigoriev.im.mail;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * The SMTP server the app sends invoices through, from the environment ({@code IM_SMTP_*}, {@code IM_MAIL_*}). The
 * password stays out of the database and out of git.
 *
 * @param host the server, empty to send nothing
 * @param port the port, 587 by default
 * @param username the login, empty for none
 * @param password the password, empty for none
 * @param starttls whether the connection switches to TLS, {@code true} by default
 * @param from the sender address
 * @param bcc an address that gets a blind copy, empty for none
 */
@ConfigurationProperties("im.mail")
public record MailProperties(
        @DefaultValue("") String host,
        @DefaultValue("587") int port,
        @DefaultValue("") String username,
        @DefaultValue("") String password,
        @DefaultValue("true") boolean starttls,
        @DefaultValue("") String from,
        @DefaultValue("") String bcc) {

    /**
     * Tells whether the app can send.
     *
     * @return {@code true} if a server and a sender are set
     */
    public boolean configured() {
        return !host.isBlank() && !from.isBlank();
    }

    @Override
    public String toString() {
        return "MailProperties[host=" + host + ", port=" + port + ", username=" + username + ", from=" + from + "]";
    }
}
