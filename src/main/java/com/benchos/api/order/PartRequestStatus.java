package com.benchos.api.order;

public enum PartRequestStatus {
    TO_ORDER,
    ORDERED,
    ARRIVED,
    CANCELLED;

    public boolean isPending() {
        return this == TO_ORDER || this == ORDERED;
    }
}
