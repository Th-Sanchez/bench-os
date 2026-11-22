package com.benchos.api.iam.internal.web;

import com.benchos.api.iam.internal.service.TenantSettingsService;
import com.benchos.api.iam.internal.web.dto.TenantSettingsResponse;
import com.benchos.api.iam.internal.web.dto.UpdateTenantSettingsRequest;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/tenant/settings")
public class TenantSettingsController {

    private final TenantSettingsService tenantSettingsService;

    public TenantSettingsController(TenantSettingsService tenantSettingsService) {
        this.tenantSettingsService = tenantSettingsService;
    }

    @GetMapping
    public TenantSettingsResponse getSettings() {
        return tenantSettingsService.getCurrentTenantSettings();
    }

    @PutMapping
    @PreAuthorize("hasRole('OWNER')")
    public TenantSettingsResponse updateSettings(@Valid @RequestBody UpdateTenantSettingsRequest request) {
        return tenantSettingsService.updateCurrentTenantSettings(request);
    }
}
