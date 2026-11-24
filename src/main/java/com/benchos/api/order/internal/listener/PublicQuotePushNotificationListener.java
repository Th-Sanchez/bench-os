package com.benchos.api.order.internal.listener;

import com.benchos.api.iam.PushTokenQuery;
import com.benchos.api.order.PublicQuoteRespondedEvent;
import com.benchos.api.shared.push.PushDeliveryResult;
import com.benchos.api.shared.push.PushMessage;
import com.benchos.api.shared.push.PushNotificationSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Asynchronous Spring Modulith listener that notifies workshop devices in real time
 * when a customer approves or rejects a quote via the public tracking page.
 */
@Component
public class PublicQuotePushNotificationListener {

    private static final Logger log = LoggerFactory.getLogger(PublicQuotePushNotificationListener.class);
    private static final Locale PT_BR = Locale.forLanguageTag("pt-BR");

    private final PushTokenQuery pushTokenQuery;
    private final PushNotificationSender pushNotificationSender;

    public PublicQuotePushNotificationListener(
            PushTokenQuery pushTokenQuery,
            PushNotificationSender pushNotificationSender
    ) {
        this.pushTokenQuery = pushTokenQuery;
        this.pushNotificationSender = pushNotificationSender;
    }

    @ApplicationModuleListener
    public void onPublicQuoteResponded(PublicQuoteRespondedEvent event) {
        List<String> tokens = pushTokenQuery.findActiveTokensForTenant(event.tenantId());
        if (tokens.isEmpty()) {
            log.debug("No active push tokens for tenant={} on quote response item={}", event.tenantId(), event.itemId());
            return;
        }

        String title = (event.approved() ? "Orçamento aprovado" : "Orçamento recusado")
                + " · #" + event.tag();
        String actionVerb = event.approved() ? "aprovou" : "recusou";
        String priceSuffix = event.totalPrice() != null && event.totalPrice().compareTo(BigDecimal.ZERO) > 0
                ? " (" + formatCurrency(event.totalPrice()) + ")"
                : "";
        String body = event.customerName() + " " + actionVerb + " " + event.applianceLabel() + priceSuffix;

        Map<String, Object> data = Map.of(
                "type", event.approved() ? "QUOTE_APPROVED" : "QUOTE_REJECTED",
                "orderId", event.serviceOrderId().toString(),
                "itemId", event.itemId().toString(),
                "tag", event.tag(),
                "route", "/orders/" + event.serviceOrderId() + "/items/" + event.itemId()
        );

        List<PushMessage> messages = tokens.stream()
                .map(token -> PushMessage.of(token, title, body, data))
                .toList();

        PushDeliveryResult result = pushNotificationSender.sendBatch(messages);
        if (!result.invalidTokens().isEmpty()) {
            pushTokenQuery.revokeInvalidTokens(result.invalidTokens());
        }

        log.info(
                "Dispatched quote response push tenant={} tag={} approved={} sent={} failed={}",
                event.tenantId(),
                event.tag(),
                event.approved(),
                result.sentCount(),
                result.failedCount()
        );
    }

    private static String formatCurrency(BigDecimal amount) {
        return NumberFormat.getCurrencyInstance(PT_BR).format(amount);
    }
}
