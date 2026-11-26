package com.benchos.api.iam.internal.repository;

import com.benchos.api.iam.Role;
import com.benchos.api.iam.internal.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    Optional<User> findByIdAndTenantId(UUID id, UUID tenantId);

    Optional<User> findByIdAndTenantIdAndActiveTrue(UUID id, UUID tenantId);

    List<User> findAllByTenantIdOrderByCreatedAtAsc(UUID tenantId);

    List<User> findAllByTenantIdAndIdIn(UUID tenantId, Collection<UUID> ids);

    List<User> findAllByTenantIdAndActiveTrueAndRoleInOrderByNameAsc(UUID tenantId, Collection<Role> roles);
}
