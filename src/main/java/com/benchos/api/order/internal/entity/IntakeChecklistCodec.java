package com.benchos.api.order.internal.entity;

import com.benchos.api.shared.exception.BusinessRuleException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Normalizes, validates and serializes an item's intake checklist snapshot.
 */
public final class IntakeChecklistCodec {

    public static final int MAX_ENTRIES = 30;
    public static final int MAX_LABEL_LENGTH = 160;

    private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();
    private static final TypeReference<List<IntakeChecklistEntry>> LIST_TYPE = new TypeReference<>() {};

    private IntakeChecklistCodec() {
    }

    public static String encodeAndValidate(List<IntakeChecklistEntry> rawEntries) {
        if (rawEntries == null || rawEntries.isEmpty()) {
            return null;
        }
        if (rawEntries.size() > MAX_ENTRIES) {
            throw new BusinessRuleException("At most " + MAX_ENTRIES + " intake checklist items are allowed");
        }

        List<IntakeChecklistEntry> normalized = new ArrayList<>();
        Set<String> seenLabelsLower = new LinkedHashSet<>();

        for (IntakeChecklistEntry entry : rawEntries) {
            if (entry == null || entry.label() == null || entry.label().trim().isEmpty()) {
                continue;
            }
            String label = entry.label().trim();
            if (label.length() > MAX_LABEL_LENGTH) {
                throw new BusinessRuleException(
                        "Intake checklist item label must be at most " + MAX_LABEL_LENGTH + " characters"
                );
            }
            if (seenLabelsLower.add(label.toLowerCase())) {
                normalized.add(new IntakeChecklistEntry(label, entry.checked()));
            }
        }

        if (normalized.isEmpty()) {
            return null;
        }
        return JSON_MAPPER.writeValueAsString(normalized);
    }

    public static List<IntakeChecklistEntry> decode(String rawJson) {
        if (rawJson == null || rawJson.isBlank()) {
            return List.of();
        }
        try {
            List<IntakeChecklistEntry> parsed = JSON_MAPPER.readValue(rawJson, LIST_TYPE);
            return parsed == null ? List.of() : List.copyOf(parsed);
        } catch (RuntimeException ex) {
            return List.of();
        }
    }
}
