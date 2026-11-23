package com.benchos.api.order.internal.entity;

import com.benchos.api.order.ItemStatus;
import com.benchos.api.order.OrderItemDeliveredEvent;
import com.benchos.api.order.PaymentMethod;
import com.benchos.api.order.PaymentPart;
import com.benchos.api.order.Voltage;
import com.benchos.api.shared.exception.BusinessRuleException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ServiceOrderItemStateMachineTest {

    private static final Instant BASE_TIME = Instant.parse("2026-01-10T12:00:00Z");

    @Test
    void enforcesQuoteLockReopenAndSplitPaymentDelivery() {
        ServiceOrderItem item = newReceivedItem();

        // Cannot quote before DIAGNOSING
        assertThatThrownBy(() -> item.updateQuote("Diag", bd("80.00"), bd("40.00"), bd("0.00"), 90, BASE_TIME))
                .isInstanceOf(BusinessRuleException.class);

        // Cannot skip directly to APPROVED
        assertThatThrownBy(() -> item.transitionTo(ItemStatus.APPROVED, LocalDate.of(2026, 1, 15), null, BASE_TIME))
                .isInstanceOf(BusinessRuleException.class);

        item.transitionTo(ItemStatus.DIAGNOSING, null, null, BASE_TIME);

        // Negative total rejected
        assertThatThrownBy(() -> item.updateQuote("Diag", bd("50.00"), bd("20.00"), bd("80.00"), 90, BASE_TIME))
                .isInstanceOf(BusinessRuleException.class);

        item.updateQuote("Blown magnetron", bd("80.00"), bd("120.00"), bd("20.00"), 90, BASE_TIME);
        assertThat(item.getTotalPrice()).isEqualByComparingTo("180.00");

        item.transitionTo(ItemStatus.QUOTE_SENT, null, null, BASE_TIME);
        LocalDate estimatedDate = LocalDate.of(2026, 1, 15);
        item.transitionTo(ItemStatus.APPROVED, estimatedDate, null, BASE_TIME);

        assertThat(item.getApprovedAt()).isEqualTo(BASE_TIME);
        assertThat(item.getEstimatedCompletionDate()).isEqualTo(estimatedDate);

        // Locked after approval
        assertThatThrownBy(() -> item.updateQuote("Adjusted", bd("100.00"), bd("120.00"), bd("0.00"), 90, BASE_TIME))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("locked");

        // Explicit reopen to QUOTE_SENT unlocks quote and clears previous approval metadata
        item.transitionTo(ItemStatus.QUOTE_SENT, null, null, BASE_TIME.plusSeconds(60));
        assertThat(item.getApprovedAt()).isNull();
        assertThat(item.getEstimatedCompletionDate()).isNull();

        item.updateQuote("Magnetron + capacitor", bd("100.00"), bd("150.00"), bd("10.00"), 120, BASE_TIME.plusSeconds(120));
        assertThat(item.getTotalPrice()).isEqualByComparingTo("240.00");
        assertThat(item.getWarrantyDays()).isEqualTo(120);

        item.transitionTo(ItemStatus.APPROVED, LocalDate.of(2026, 1, 16), null, BASE_TIME.plusSeconds(180));
        item.transitionTo(ItemStatus.WAITING_PARTS, null, null, BASE_TIME.plusSeconds(240));
        item.transitionTo(ItemStatus.IN_REPAIR, null, null, BASE_TIME.plusSeconds(300));
        item.transitionTo(ItemStatus.READY_FOR_PICKUP, null, null, BASE_TIME.plusSeconds(360));
        assertThat(item.getFinishedAt()).isEqualTo(BASE_TIME.plusSeconds(360));

        // Transitioning to DELIVERED via transitionTo is blocked
        assertThatThrownBy(() -> item.transitionTo(ItemStatus.DELIVERED, null, null, BASE_TIME.plusSeconds(400)))
                .isInstanceOf(BusinessRuleException.class);

        // Split payment sum mismatch rejected
        assertThatThrownBy(() -> item.deliver(
                List.of(new PaymentPart(PaymentMethod.PIX, bd("100.00"))),
                BASE_TIME.plusSeconds(400)
        )).isInstanceOf(BusinessRuleException.class);

        // Valid split payment succeeds and produces domain event
        Instant deliveredAt = BASE_TIME.plusSeconds(420);
        Optional<OrderItemDeliveredEvent> event = item.deliver(
                List.of(
                        new PaymentPart(PaymentMethod.PIX, bd("100.00")),
                        new PaymentPart(PaymentMethod.CREDIT_CARD, bd("140.00"))
                ),
                deliveredAt
        );

        assertThat(item.getStatus()).isEqualTo(ItemStatus.DELIVERED);
        assertThat(item.getDeliveredAt()).isEqualTo(deliveredAt);
        assertThat(event).isPresent();
        assertThat(event.get().totalPrice()).isEqualByComparingTo("240.00");
        assertThat(event.get().payments()).hasSize(2);

        // Terminal status blocks further transitions
        assertThatThrownBy(() -> item.transitionTo(ItemStatus.CANCELLED, null, "Too late", deliveredAt.plusSeconds(10)))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void deliversRejectedItemWithoutChargeAndWithoutEvent() {
        ServiceOrderItem item = newReceivedItem();
        item.transitionTo(ItemStatus.DIAGNOSING, null, null, BASE_TIME);
        item.updateQuote("Motor burnt", bd("90.00"), bd("110.00"), bd("0.00"), 90, BASE_TIME);
        item.transitionTo(ItemStatus.QUOTE_SENT, null, null, BASE_TIME);
        item.transitionTo(ItemStatus.REJECTED, null, null, BASE_TIME);

        // Payments rejected for REJECTED items
        assertThatThrownBy(() -> item.deliver(
                List.of(new PaymentPart(PaymentMethod.CASH, bd("20.00"))),
                BASE_TIME.plusSeconds(60)
        )).isInstanceOf(BusinessRuleException.class);

        Optional<OrderItemDeliveredEvent> event = item.deliver(List.of(), BASE_TIME.plusSeconds(120));
        assertThat(event).isEmpty();
        assertThat(item.getStatus()).isEqualTo(ItemStatus.DELIVERED);
        assertThat(item.getTotalPrice()).isEqualByComparingTo("0.00");
        assertThat(item.getLaborCost()).isEqualByComparingTo("0.00");
        assertThat(item.getPartsCost()).isEqualByComparingTo("0.00");
    }

    @Test
    void enforcesAbandonmentPeriodAndCancellationReason() {
        ServiceOrderItem readyItem = newReceivedItem();
        readyItem.transitionTo(ItemStatus.DIAGNOSING, null, null, BASE_TIME);
        readyItem.transitionTo(ItemStatus.QUOTE_SENT, null, null, BASE_TIME);
        readyItem.transitionTo(ItemStatus.APPROVED, LocalDate.of(2026, 1, 15), null, BASE_TIME);
        readyItem.transitionTo(ItemStatus.IN_REPAIR, null, null, BASE_TIME);
        readyItem.transitionTo(ItemStatus.READY_FOR_PICKUP, null, null, BASE_TIME);

        // 179 days -> too early
        Instant day179 = BASE_TIME.plus(Duration.ofDays(179));
        assertThatThrownBy(() -> readyItem.markDiscarded(180, day179))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("180 days");

        // 180 days -> eligible
        Instant day180 = BASE_TIME.plus(Duration.ofDays(180));
        readyItem.markDiscarded(180, day180);
        assertThat(readyItem.getStatus()).isEqualTo(ItemStatus.DISCARDED);
        assertThat(readyItem.getDiscardedAt()).isEqualTo(day180);

        // Cancellation requires reason
        ServiceOrderItem cancelItem = newReceivedItem();
        assertThatThrownBy(() -> cancelItem.transitionTo(ItemStatus.CANCELLED, null, "   ", BASE_TIME))
                .isInstanceOf(BusinessRuleException.class);

        cancelItem.transitionTo(ItemStatus.CANCELLED, null, "Customer requested cancellation", BASE_TIME);
        assertThat(cancelItem.getStatus()).isEqualTo(ItemStatus.CANCELLED);
        assertThat(cancelItem.getCancellationReason()).isEqualTo("Customer requested cancellation");
    }

    private ServiceOrderItem newReceivedItem() {
        ServiceOrder order = new ServiceOrder(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                1001L,
                null
        );
        return order.addItem("Microwave", "Electrolux", "MEF41", Voltage.V110, null, null, "Not heating");
    }

    private static BigDecimal bd(String value) {
        return new BigDecimal(value);
    }
}
