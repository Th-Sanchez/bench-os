package com.benchos.api.finance.internal.job;

import com.benchos.api.finance.internal.entity.FinancialTransaction;
import com.benchos.api.finance.internal.repository.FinancialTransactionRepository;
import com.benchos.api.iam.PushTokenQuery;
import com.benchos.api.iam.Role;
import com.benchos.api.shared.push.PushDeliveryResult;
import com.benchos.api.shared.push.PushMessage;
import com.benchos.api.shared.push.PushNotificationSender;
import com.benchos.api.shared.push.PushProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Daily scheduled job that notifies workshop OWNERs about PENDING expense payables
 * (such as supplier STORE_CREDIT / "fiado" accounts) due today or already overdue.
 */
@Component
public class PendingPayablesAlertJob {

    private static final Logger log = LoggerFactory.getLogger(PendingPayablesAlertJob.class);
    private static final Locale PT_BR = Locale.forLanguageTag("pt-BR");

    private final FinancialTransactionRepository transactionRepository;
    private final PushTokenQuery pushTokenQuery;
    private final PushNotificationSender pushNotificationSender;
    private final PushProperties pushProperties;

    public PendingPayablesAlertJob(
            FinancialTransactionRepository transactionRepository,
            PushTokenQuery pushTokenQuery,
            PushNotificationSender pushNotificationSender,
            PushProperties pushProperties
    ) {
        this.transactionRepository = transactionRepository;
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
        int notifiedTenants = dispatchDueAlerts(today);
        if (notifiedTenants > 0) {
            log.info("Daily pending payables alert notified {} tenant(s) for date={}", notifiedTenants, today);
        }
    }

    public int dispatchDueAlerts(LocalDate referenceDate) {
        List<FinancialTransaction> dueTransactions =
                transactionRepository.findAllPendingExpensesDueOnOrBefore(referenceDate);
        if (dueTransactions.isEmpty()) {
            return 0;
        }

        Map<UUID, List<FinancialTransaction>> byTenant = dueTransactions.stream()
                .collect(Collectors.groupingBy(FinancialTransaction::getTenantId));

        int notifiedTenants = 0;
        for (Map.Entry<UUID, List<FinancialTransaction>> entry : byTenant.entrySet()) {
            UUID tenantId = entry.getKey();
            List<FinancialTransaction> items = entry.getValue();

            List<String> ownerTokens = pushTokenQuery.findActiveTokensForTenantRoles(tenantId, List.of(Role.OWNER));
            if (ownerTokens.isEmpty()) {
                continue;
            }

            long overdueCount = items.stream()
                    .filter(tx -> tx.getDueDate() != null && tx.getDueDate().isBefore(referenceDate))
                    .count();
            long dueTodayCount = items.size() - overdueCount;
            BigDecimal totalAmount = items.stream()
                    .map(FinancialTransaction::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            String title = overdueCount > 0
                    ? "Contas a pagar vencidas"
                    : "Contas a pagar vencendo hoje";
            String body = buildSummaryBody(items.size(), overdueCount, dueTodayCount, totalAmount);

            Map<String, Object> data = Map.of(
                    "type", "FINANCE_DUE_ALERT",
                    "route", "/(tabs)/finance",
                    "overdueCount", overdueCount,
                    "dueTodayCount", dueTodayCount
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

    private static String buildSummaryBody(int totalCount, long overdueCount, long dueTodayCount, BigDecimal totalAmount) {
        List<String> breakdown = new ArrayList<>(2);
        if (overdueCount > 0) {
            breakdown.add(overdueCount == 1 ? "1 vencida" : overdueCount + " vencidas");
        }
        if (dueTodayCount > 0) {
            breakdown.add(dueTodayCount == 1 ? "1 vence hoje" : dueTodayCount + " vencem hoje");
        }
        String countLabel = totalCount == 1 ? "1 conta pendente" : totalCount + " contas pendentes";
        String formattedTotal = NumberFormat.getCurrencyInstance(PT_BR).format(totalAmount);
        return countLabel + " (" + formattedTotal + "): " + String.join(" e ", breakdown) + ".";
    }
}
