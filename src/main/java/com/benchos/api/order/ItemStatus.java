package com.benchos.api.order;

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
    CANCELLED
}
