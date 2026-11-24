package com.benchos.api.shared.push;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Development and test push sender that logs notifications instead of calling Expo servers.
 */
public class LoggingPushNotificationSender implements PushNotificationSender {

    private static final Logger log = LoggerFactory.getLogger(LoggingPushNotificationSender.class);

    @Override
    public PushDeliveryResult sendBatch(List<PushMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return PushDeliveryResult.empty();
        }
        for (PushMessage message : messages) {
            log.info(
                    "[PUSH-DEV] to={} title='{}' body='{}' data={}",
                    message.toToken(),
                    message.title(),
                    message.body(),
                    message.data()
            );
        }
        return new PushDeliveryResult(messages.size(), 0, List.of());
    }
}
