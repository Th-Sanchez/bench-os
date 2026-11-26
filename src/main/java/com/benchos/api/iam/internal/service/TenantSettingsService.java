package com.benchos.api.iam.internal.service;

import com.benchos.api.iam.TenantSettingsQuery;
import com.benchos.api.iam.TenantSettingsSnapshot;
import com.benchos.api.iam.internal.entity.Tenant;
import com.benchos.api.iam.internal.entity.TenantSettings;
import com.benchos.api.iam.internal.repository.TenantRepository;
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

    private final TenantRepository tenantRepository;
    private final TenantSettingsRepository tenantSettingsRepository;

    public TenantSettingsService(
            TenantRepository tenantRepository,
            TenantSettingsRepository tenantSettingsRepository
    ) {
        this.tenantRepository = tenantRepository;
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
                settings.getWhatsappIntakeTemplate(),
                settings.getWhatsappQuoteTemplate(),
                settings.getWhatsappReadyTemplate(),
                settings.getCustomApplianceTypeList(),
                settings.getGoogleReviewUrl(),
                settings.isDeliverySignatureEnabled()
        );
    }

    @Override
    @Transactional(readOnly = true)
    public String getShopName(UUID tenantId) {
        return tenantRepository.findById(tenantId)
                .map(Tenant::getName)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant not found"));
    }

    @Override
    @Transactional(readOnly = true)
    public String getShopPhone(UUID tenantId) {
        return tenantRepository.findById(tenantId)
                .map(Tenant::getPhone)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant not found"));
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
                request.whatsappIntakeTemplate(),
                request.whatsappQuoteTemplate(),
                request.whatsappReadyTemplate(),
                request.googleReviewUrl(),
                request.deliverySignatureEnabled()
        );
        return TenantSettingsResponse.from(tenantSettingsRepository.save(settings));
    }

    @Transactional
    public TenantSettingsResponse addCustomApplianceType(String name) {
        UUID tenantId = TenantContext.requireCurrentTenant();
        TenantSettings settings = findOrThrow(tenantId);
        settings.addCustomApplianceType(name);
        return TenantSettingsResponse.from(tenantSettingsRepository.save(settings));
    }

    @Transactional
    public TenantSettingsResponse removeCustomApplianceType(String name) {
        UUID tenantId = TenantContext.requireCurrentTenant();
        TenantSettings settings = findOrThrow(tenantId);
        settings.removeCustomApplianceType(name);
        return TenantSettingsResponse.from(tenantSettingsRepository.save(settings));
    }

    private TenantSettings findOrThrow(UUID tenantId) {
        return tenantSettingsRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant settings not found"));
    }
}
