package io.github.grigoriev.im.mail;

/**
 * The subject and the text of an e-mail.
 *
 * @param subject the subject
 * @param body the text
 */
public record MailText(String subject, String body) {
}
