package com.benchos.api.integration.internal.repository;

import com.benchos.api.integration.internal.entity.TenantModule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TenantModuleRepository extends JpaRepository<TenantModule, UUID> {

    List<TenantModule> findAllByTenantIdOrderByModuleKeyAsc(UUID tenantId);

    List<TenantModule> findAllByTenantIdAndEnabledTrueOrderByModuleKeyAsc(UUID tenantId);

    Optional<TenantModule> findByTenantIdAndModuleKey(UUID tenantId, String moduleKey);
}
