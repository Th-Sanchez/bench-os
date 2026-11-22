package com.benchos.api.iam.internal.repository;

import com.benchos.api.iam.internal.entity.Tenant;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface TenantRepository extends JpaRepository<Tenant, UUID> {
}
