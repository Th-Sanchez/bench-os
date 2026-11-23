package com.benchos.api.order;

import java.util.EnumSet;
import java.util.Set;

public enum ItemStatus {
    RECEIVED,
    DIAGNOSING,
    QUOTE_SENT,
    APPROVED,
    WAITING_PARTS,
    IN_REPAIR,
    READY_FOR_PICKUP,
    DELIVERED,
    REJECTED,
    DISCARDED,
    CANCELLED;

    public Set<ItemStatus> allowedTransitions() {
        return switch (this) {
            case RECEIVED -> EnumSet.of(DIAGNOSING, CANCELLED);
            case DIAGNOSING -> EnumSet.of(QUOTE_SENT, CANCELLED);
            case QUOTE_SENT -> EnumSet.of(APPROVED, REJECTED, CANCELLED);
            case APPROVED -> EnumSet.of(WAITING_PARTS, IN_REPAIR, QUOTE_SENT, CANCELLED);
            case WAITING_PARTS -> EnumSet.of(IN_REPAIR, QUOTE_SENT, CANCELLED);
            case IN_REPAIR -> EnumSet.of(READY_FOR_PICKUP, QUOTE_SENT, CANCELLED);
            case READY_FOR_PICKUP -> EnumSet.of(DELIVERED, DISCARDED, CANCELLED);
            case REJECTED -> EnumSet.of(DELIVERED, DISCARDED, CANCELLED);
            case DELIVERED, DISCARDED, CANCELLED -> EnumSet.noneOf(ItemStatus.class);
        };
    }

    public boolean canTransitionTo(ItemStatus target) {
        return target != null && allowedTransitions().contains(target);
    }

    public boolean isQuoteEditable() {
        return this == DIAGNOSING || this == QUOTE_SENT;
    }

    public boolean isQuoteLockedAfterApproval() {
        return this == APPROVED || this == WAITING_PARTS || this == IN_REPAIR;
    }

    public boolean isTerminal() {
        return this == DELIVERED || this == DISCARDED || this == CANCELLED;
    }
}
