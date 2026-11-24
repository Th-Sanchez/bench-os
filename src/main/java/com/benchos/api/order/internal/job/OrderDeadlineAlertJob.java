package com.benchos.api.order.internal.job;

import com.benchos.api.iam.PushTokenQuery;
import com.benchos.api.iam.Role;
import com.benchos.api.order.ItemStatus;
import com.benchos.api.order.internal.entity.ServiceOrderItem;
import com.benchos.api.order.internal.repository.ServiceOrderItemRepository;
import com.benchos.api.shared.push.PushDeliveryResult;
import com.benchos.api.shared.push.PushMessage;
import com.benchos.api.shared.push.PushNotificationSender;
import com.benchos.api.shared.push.PushProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Daily scheduled job that alerts workshop OWNERs about active repairs whose
 * estimated completion date is overdue, due today, or due tomorrow.
 */
@Component
public class OrderDeadlineAlertJob {

    private static final Logger log = LoggerFactory.getLogger(OrderDeadlineAlertJob.class);
    private static final List<ItemStatus> ACTIVE_DEADLINE_STATUSES = List.of(
            ItemStatus.APPROVED,
            ItemStatus.WAITING_PARTS,
            ItemStatus.IN_REPAIR
    );

    private final ServiceOrderItemRepository itemRepository;
    private final PushTokenQuery pushTokenQuery;
    private final PushNotificationSender pushNotificationSender;
    private final PushProperties pushProperties;

    public OrderDeadlineAlertJob(
            ServiceOrderItemRepository itemRepository,
            PushTokenQuery pushTokenQuery,
            PushNotificationSender pushNotificationSender,
            PushProperties pushProperties
    ) {
        this.itemRepository = itemRepository;
        this.pushTokenQuery = pushTokenQuery;
        this.pushNotificationSender = pushNotificationSender;
        this.pushProperties = pushProperties;
    }

    @Scheduled(
            cron = "${benchos.push.daily-alerts-cron:0 30 8 * * *}",
            zone = "${benchos.push.daily-alerts-zone:America/Sao_Paulo}"
    )
    public void runDailyCheck() {
        LocalDate today = LocalDate.now(ZoneId.of(pushProperties.dailyAlertsZone()));
        int notifiedTenants = dispatchDeadlineAlerts(today);
        if (notifiedTenants > 0) {
            log.info("Daily order deadline alert notified {} tenant(s) for date={}", notifiedTenants, today);
        }
    }

    public int dispatchDeadlineAlerts(LocalDate referenceDate) {
        LocalDate tomorrow = referenceDate.plusDays(1);
        List<ServiceOrderItem> dueItems = itemRepository.findAllByStatusInAndEstimatedCompletionDateOnOrBefore(
                ACTIVE_DEADLINE_STATUSES,
                tomorrow
        );
        if (dueItems.isEmpty()) {
            return 0;
        }

        Map<UUID, List<ServiceOrderItem>> byTenant = dueItems.stream()
                .collect(Collectors.groupingBy(ServiceOrderItem::getTenantId));

        int notifiedTenants = 0;
        for (Map.Entry<UUID, List<ServiceOrderItem>> entry : byTenant.entrySet()) {
            UUID tenantId = entry.getKey();
            List<ServiceOrderItem> items = entry.getValue();

            List<String> ownerTokens = pushTokenQuery.findActiveTokensForTenantRoles(tenantId, List.of(Role.OWNER));
            if (ownerTokens.isEmpty()) {
                continue;
            }

            long overdueCount = items.stream()
                    .filter(i -> i.getEstimatedCompletionDate() != null && i.getEstimatedCompletionDate().isBefore(referenceDate))
                    .count();
            long dueTodayCount = items.stream()
                    .filter(i -> referenceDate.equals(i.getEstimatedCompletionDate()))
                    .count();
            long dueTomorrowCount = items.size() - overdueCount - dueTodayCount;

            String title = overdueCount > 0
                    ? "OS com prazo estourado na bancada"
                    : "Prazos de OS próximos na bancada";
            String body = buildSummaryBody(items.size(), overdueCount, dueTodayCount, dueTomorrowCount);

            Map<String, Object> data = Map.of(
                    "type", "ORDER_DEADLINE_ALERT",
                    "route", "/(tabs)/workbench",
                    "overdueCount", overdueCount,
                    "dueTodayCount", dueTodayCount,
                    "dueTomorrowCount", dueTomorrowCount
            );

            List<PushMessage> messages = ownerTokens.stream()
                    .map(token -> PushMessage.of(token, title, body, data))
                    .toList();

            PushDeliveryResult result = pushNotificationSender.sendBatch(messages);
            if (!result.invalidTokens().isEmpty()) {
                pushTokenQuery.revokeInvalidTokens(result.invalidTokens());
            }
            if (result.sentCount() > 0) {
                notifiedTenants++;
            }
        }

        return notifiedTenants;
    }

    private static String buildSummaryBody(
            int totalCount,
            long overdueCount,
            long dueTodayCount,
            long dueTomorrowCount
    ) {
        List<String> parts = new ArrayList<>(3);
        if (overdueCount > 0) {
            parts.add(overdueCount == 1 ? "1 atrasada" : overdueCount + " atrasadas");
        }
        if (dueTodayCount > 0) {
            parts.add(dueTodayCount == 1 ? "1 vence hoje" : dueTodayCount + " vencem hoje");
        }
        if (dueTomorrowCount > 0) {
            parts.add(dueTomorrowCount == 1 ? "1 vence amanhã" : dueTomorrowCount + " vencem amanhã");
        }
        String prefix = totalCount == 1 ? "1 aparelho exige atenção: " : totalCount + " aparelhos exigem atenção: ";
        return prefix + String.join(", ", parts) + ".";
    }
}
