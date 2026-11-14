package io.github.grigoriev.im.mail;

import java.util.Properties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

/** The mail sender from {@link MailProperties}. Without a server it exists but the app never calls it. */
@Configuration(proxyBeanMethods = false)
public class MailConfig {

    /** Milliseconds to wait for the server before the send fails. */
    static final String TIMEOUT_MS = "20000";

    /**
     * Creates the mail sender.
     *
     * @param properties the server
     * @return the sender
     */
    @Bean
    JavaMailSender mailSender(MailProperties properties) {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(properties.host());
        sender.setPort(properties.port());
        sender.setDefaultEncoding("UTF-8");
        Properties mail = sender.getJavaMailProperties();
        if (!properties.username().isBlank()) {
            sender.setUsername(properties.username());
            sender.setPassword(properties.password());
            mail.put("mail.smtp.auth", "true");
        }
        mail.put("mail.smtp.starttls.enable", Boolean.toString(properties.starttls()));
        mail.put("mail.smtp.starttls.required", Boolean.toString(properties.starttls()));
        mail.put("mail.smtp.connectiontimeout", TIMEOUT_MS);
        mail.put("mail.smtp.timeout", TIMEOUT_MS);
        mail.put("mail.smtp.writetimeout", TIMEOUT_MS);
        return sender;
    }
}
