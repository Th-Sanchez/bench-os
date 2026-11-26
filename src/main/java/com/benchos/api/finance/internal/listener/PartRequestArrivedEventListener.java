package com.benchos.api.finance.internal.listener;

import com.benchos.api.finance.TransactionPaymentMethod;
import com.benchos.api.finance.internal.entity.FinancialTransaction;
import com.benchos.api.finance.internal.repository.FinancialTransactionRepository;
import com.benchos.api.order.PartRequestArrivedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Asynchronous Spring Modulith event listener that reacts to {@link PartRequestArrivedEvent}
 * and records a PAID EXPENSE (PARTS_PURCHASE) transaction idempotently.
 */
@Component
public class PartRequestArrivedEventListener {

    private static final Logger log = LoggerFactory.getLogger(PartRequestArrivedEventListener.class);

    private final FinancialTransactionRepository transactionRepository;

    public PartRequestArrivedEventListener(FinancialTransactionRepository transactionRepository) {
        this.transactionRepository = transactionRepository;
    }

    @ApplicationModuleListener
    public void onPartRequestArrived(PartRequestArrivedEvent event) {
        if (event.totalCost() == null || event.totalCost().compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }

        if (transactionRepository.existsByTenantIdAndPartRequestId(event.tenantId(), event.partRequestId())) {
            log.info("Skipping duplicate PartRequestArrivedEvent for tenant={} partRequest={}",
                    event.tenantId(), event.partRequestId());
            return;
        }

        FinancialTransaction expense = FinancialTransaction.partsPurchaseExpense(
                event.tenantId(),
                event.itemId(),
                event.partRequestId(),
                event.orderNumber(),
                event.itemIndex(),
                event.partName(),
                event.quantity(),
                event.supplierName(),
                event.totalCost(),
                TransactionPaymentMethod.fromOrderPaymentMethod(event.paymentMethod()),
                event.arrivedAt()
        );

        transactionRepository.save(expense);
        log.info("Recorded part purchase expense for tenant={} partRequest={} order={} item={}",
                event.tenantId(), event.partRequestId(), event.orderNumber(), event.itemIndex());
    }
}
