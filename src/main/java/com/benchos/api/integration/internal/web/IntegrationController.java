package com.benchos.api.integration.internal.web;

import com.benchos.api.integration.internal.service.TenantModuleService;
import com.benchos.api.integration.internal.web.dto.TenantModuleResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Read-only tenant endpoint exposing which platform-managed modules are enabled for the
 * authenticated workshop. Provisioning of custom modules and outbound webhooks is intentionally
 * restricted to platform/super-admin operations (no tenant self-service).
 */
@RestController
@RequestMapping("/api/v1/integrations")
public class IntegrationController {

    private final TenantModuleService tenantModuleService;

    public IntegrationController(TenantModuleService tenantModuleService) {
        this.tenantModuleService = tenantModuleService;
    }

    @GetMapping("/modules")
    public List<TenantModuleResponse> listModules() {
        return tenantModuleService.listForCurrentTenant();
    }
}
