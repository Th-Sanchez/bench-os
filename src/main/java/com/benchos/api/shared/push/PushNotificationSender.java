package com.benchos.api.shared.push;

import java.util.List;

/**
 * Provider-agnostic push notification dispatch port.
 */
public interface PushNotificationSender {

    PushDeliveryResult sendBatch(List<PushMessage> messages);
}
