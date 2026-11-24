package com.benchos.api.shared.mail;

public record EmailMessage(
        String to,
        String subject,
        String htmlBody,
        String textBody
) {
}
