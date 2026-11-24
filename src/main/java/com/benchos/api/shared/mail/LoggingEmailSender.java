package com.benchos.api.shared.mail;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Development / fallback email sender that writes messages to the application log
 * without performing external network calls.
 */
public class LoggingEmailSender implements EmailSender {

    private static final Logger log = LoggerFactory.getLogger(LoggingEmailSender.class);

    @Override
    public void send(EmailMessage message) {
        log.info(
                "DEV MAIL [to={}] [subject={}]\n{}",
                message.to(),
                message.subject(),
                message.textBody()
        );
    }
}
