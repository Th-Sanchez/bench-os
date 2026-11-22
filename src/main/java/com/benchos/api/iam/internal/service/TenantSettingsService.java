package com.benchos.api.iam.internal.service;

import com.benchos.api.iam.TenantSettingsQuery;
import com.benchos.api.iam.TenantSettingsSnapshot;
import com.benchos.api.iam.internal.entity.TenantSettings;
import com.benchos.api.iam.internal.repository.TenantSettingsRepository;
import com.benchos.api.iam.internal.web.dto.TenantSettingsResponse;
import com.benchos.api.iam.internal.web.dto.UpdateTenantSettingsRequest;
import com.benchos.api.shared.exception.ResourceNotFoundException;
import com.benchos.api.shared.tenant.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class TenantSettingsService implements TenantSettingsQuery {

    private final TenantSettingsRepository tenantSettingsRepository;

    public TenantSettingsService(TenantSettingsRepository tenantSettingsRepository) {
        this.tenantSettingsRepository = tenantSettingsRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public TenantSettingsSnapshot getSettings(UUID tenantId) {
        TenantSettings settings = findOrThrow(tenantId);
        return new TenantSettingsSnapshot(
                settings.getTenantId(),
                settings.getDefaultLaborCost(),
                settings.getDefaultEstimatedDays(),
                settings.getAbandonmentDays(),
                settings.getWhatsappQuoteTemplate()
        );
    }

    @Transactional(readOnly = true)
    public TenantSettingsResponse getCurrentTenantSettings() {
        UUID tenantId = TenantContext.requireCurrentTenant();
        return TenantSettingsResponse.from(findOrThrow(tenantId));
    }

    @Transactional
    public TenantSettingsResponse updateCurrentTenantSettings(UpdateTenantSettingsRequest request) {
        UUID tenantId = TenantContext.requireCurrentTenant();
        TenantSettings settings = findOrThrow(tenantId);
        settings.update(
                request.defaultLaborCost(),
                request.defaultEstimatedDays(),
                request.abandonmentDays(),
                request.whatsappQuoteTemplate()
        );
        return TenantSettingsResponse.from(tenantSettingsRepository.save(settings));
    }

    private TenantSettings findOrThrow(UUID tenantId) {
        return tenantSettingsRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant settings not found"));
    }
}
