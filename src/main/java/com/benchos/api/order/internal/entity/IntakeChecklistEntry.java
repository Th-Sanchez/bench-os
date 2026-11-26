package com.benchos.api.order.internal.entity;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Snapshot of a single intake checklist item recorded when an appliance enters the workshop.
 */
public record IntakeChecklistEntry(
        @NotBlank @Size(max = 160) String label,
        boolean checked
) {
}
