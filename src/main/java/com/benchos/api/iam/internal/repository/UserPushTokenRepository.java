package com.benchos.api.iam.internal.repository;

import com.benchos.api.iam.Role;
import com.benchos.api.iam.internal.entity.UserPushToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserPushTokenRepository extends JpaRepository<UserPushToken, UUID> {

    Optional<UserPushToken> findByExpoPushTokenAndTenantIdAndUserId(
            String expoPushToken,
            UUID tenantId,
            UUID userId
    );

    long deleteByExpoPushTokenAndTenantIdAndUserId(
            String expoPushToken,
            UUID tenantId,
            UUID userId
    );

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "DELETE FROM user_push_tokens WHERE expo_push_token = :token", nativeQuery = true)
    int deleteByExpoPushTokenNative(@Param("token") String token);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "DELETE FROM user_push_tokens WHERE expo_push_token IN (:tokens)", nativeQuery = true)
    int deleteAllByExpoPushTokenInNative(@Param("tokens") Collection<String> tokens);

    @Query("""
            SELECT DISTINCT t.expoPushToken
            FROM UserPushToken t
            JOIN User u ON u.id = t.userId AND u.tenantId = t.tenantId
            WHERE t.tenantId = :tenantId
              AND u.active = true
            """)
    List<String> findActiveTokensForTenant(@Param("tenantId") UUID tenantId);

    @Query("""
            SELECT DISTINCT t.expoPushToken
            FROM UserPushToken t
            JOIN User u ON u.id = t.userId AND u.tenantId = t.tenantId
            WHERE t.tenantId = :tenantId
              AND u.active = true
              AND u.role IN :roles
            """)
    List<String> findActiveTokensForTenantRoles(
            @Param("tenantId") UUID tenantId,
            @Param("roles") Collection<Role> roles
    );
}
