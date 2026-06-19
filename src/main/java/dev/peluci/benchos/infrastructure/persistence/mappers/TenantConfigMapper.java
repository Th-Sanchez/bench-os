package dev.peluci.benchos.infrastructure.persistence.mappers;

import dev.peluci.benchos.domain.entities.TenantConfig;
import dev.peluci.benchos.infrastructure.persistence.entities.TenantConfigEntity;
import java.time.DayOfWeek;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class TenantConfigMapper {

    private String serializeWorkingDays(List<DayOfWeek> workingDays) {
        return workingDays
            .stream()
            .map(DayOfWeek::name)
            .collect(Collectors.joining(","));
    }

    private List<DayOfWeek> deserializeWorkingDays(String workingDays) {
        if (workingDays == null || workingDays.isEmpty()) {
            return List.of();
        }
        return Arrays.stream(workingDays.split(","))
            .map(DayOfWeek::valueOf)
            .collect(Collectors.toList());
    }

    public TenantConfig toDomain(TenantConfigEntity entity) {
        return new TenantConfig(
            entity.getId(),
            entity.isActive(),
            entity.getDtInsert(),
            entity.getDtUpdate(),
            entity.getDtDelete(),
            entity.getTenantId(),
            entity.getMaxOrdersPerDay(),
            entity.getDeadlineDaysAfterApproval(),
            deserializeWorkingDays(entity.getWorkingDays())
        );
    }

    public TenantConfigEntity toEntity(TenantConfig domain) {
        return TenantConfigEntity.builder()
            .id(domain.getId())
            .active(domain.isActive())
            .dtInsert(domain.getDtInsert())
            .dtUpdate(domain.getDtUpdate())
            .dtDelete(domain.getDtDelete())
            .tenantId(domain.getTenantId())
            .maxOrdersPerDay(domain.getMaxOrdersPerDay())
            .deadlineDaysAfterApproval(domain.getDeadlineDaysAfterApproval())
            .workingDays(serializeWorkingDays(domain.getWorkingDays()))
            .build();
    }
}
