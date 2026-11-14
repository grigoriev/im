package io.github.grigoriev.im.mail;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSenderImpl;

class MailConfigTest {

    @Test
    void logsInWithTlsAndTimeouts() {
        JavaMailSenderImpl sender = (JavaMailSenderImpl) new MailConfig().mailSender(new MailProperties("smtp.example.org",
                587, "elena", "geheim", true, "a@b.example", ""));
        assertThat(sender.getHost()).isEqualTo("smtp.example.org");
        assertThat(sender.getPort()).isEqualTo(587);
        assertThat(sender.getUsername()).isEqualTo("elena");
        assertThat(sender.getPassword()).isEqualTo("geheim");
        assertThat(sender.getDefaultEncoding()).isEqualTo("UTF-8");
        Properties mail = sender.getJavaMailProperties();
        assertThat(mail).containsEntry("mail.smtp.auth", "true").containsEntry("mail.smtp.starttls.enable", "true")
                .containsEntry("mail.smtp.starttls.required", "true").containsEntry("mail.smtp.connectiontimeout", "20000")
                .containsEntry("mail.smtp.timeout", "20000").containsEntry("mail.smtp.writetimeout", "20000");
    }

    @Test
    void sendsWithoutLoginAndTls() {
        JavaMailSenderImpl sender = (JavaMailSenderImpl) new MailConfig().mailSender(new MailProperties("localhost", 3025,
                "", "", false, "a@b.example", ""));
        assertThat(sender.getUsername()).isNull();
        assertThat(sender.getJavaMailProperties()).doesNotContainKey("mail.smtp.auth")
                .containsEntry("mail.smtp.starttls.enable", "false").containsEntry("mail.smtp.starttls.required", "false");
    }
}
