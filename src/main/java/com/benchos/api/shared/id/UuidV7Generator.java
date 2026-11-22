package com.benchos.api.shared.id;

import com.github.f4b6a3.uuid.UuidCreator;

import java.util.UUID;

/**
 * Centralized generator for time-ordered UUID v7 primary keys.
 * Preserves B-Tree index locality in PostgreSQL while preventing sequential ID enumeration (IDOR).
 */
public final class UuidV7Generator {

    private UuidV7Generator() {
    }

    public static UUID next() {
        return UuidCreator.getTimeOrderedEpoch();
    }
}
