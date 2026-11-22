package com.benchos.api.iam.internal.repository;

import com.benchos.api.iam.internal.entity.TenantSettings;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface TenantSettingsRepository extends JpaRepository<TenantSettings, UUID> {
}
