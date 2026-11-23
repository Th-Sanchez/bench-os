package com.benchos.api.finance.internal.listener;

import com.benchos.api.finance.TransactionCategory;
import com.benchos.api.finance.TransactionPaymentMethod;
import com.benchos.api.finance.TransactionType;
import com.benchos.api.finance.internal.entity.FinancialTransaction;
import com.benchos.api.finance.internal.repository.FinancialTransactionRepository;
import com.benchos.api.order.OrderItemDeliveredEvent;
import com.benchos.api.order.PaymentPart;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Asynchronous Spring Modulith event listener that reacts to {@link OrderItemDeliveredEvent}
 * and records one PAID INCOME transaction per payment split.
 *
 * <p>Runs on an async worker thread without {@code TenantContext}, so {@code event.tenantId()}
 * is explicitly propagated into every persisted entity.
 */
@Component
public class OrderItemDeliveredEventListener {

    private static final Logger log = LoggerFactory.getLogger(OrderItemDeliveredEventListener.class);

    private final FinancialTransactionRepository transactionRepository;

    public OrderItemDeliveredEventListener(FinancialTransactionRepository transactionRepository) {
        this.transactionRepository = transactionRepository;
    }

    @ApplicationModuleListener
    public void onOrderItemDelivered(OrderItemDeliveredEvent event) {
        if (event.payments().isEmpty()) {
            return;
        }

        boolean alreadyRecorded = transactionRepository.existsByTenantIdAndServiceOrderItemIdAndTypeAndCategory(
                event.tenantId(),
                event.itemId(),
                TransactionType.INCOME,
                TransactionCategory.REPAIR_PAYMENT
        );
        if (alreadyRecorded) {
            log.info("Skipping duplicate OrderItemDeliveredEvent for tenant={} item={}",
                    event.tenantId(), event.itemId());
            return;
        }

        List<FinancialTransaction> transactions = event.payments().stream()
                .map(part -> toIncomeTransaction(event, part))
                .toList();

        transactionRepository.saveAll(transactions);
        log.info("Recorded {} repair income transaction(s) for tenant={} order={} item={}",
                transactions.size(), event.tenantId(), event.orderNumber(), event.itemIndex());
    }

    private FinancialTransaction toIncomeTransaction(OrderItemDeliveredEvent event, PaymentPart part) {
        return FinancialTransaction.repairIncome(
                event.tenantId(),
                event.itemId(),
                event.orderNumber(),
                event.itemIndex(),
                part.amount(),
                TransactionPaymentMethod.fromOrderPaymentMethod(part.paymentMethod()),
                event.deliveredAt()
        );
    }
}
