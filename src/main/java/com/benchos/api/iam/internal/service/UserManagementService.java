package com.benchos.api.iam.internal.service;

import com.benchos.api.iam.Role;
import com.benchos.api.iam.UserDirectoryQuery;
import com.benchos.api.iam.UserSnapshot;
import com.benchos.api.iam.internal.entity.User;
import com.benchos.api.iam.internal.repository.PasswordResetTokenRepository;
import com.benchos.api.iam.internal.repository.UserRepository;
import com.benchos.api.iam.internal.web.dto.CreateUserRequest;
import com.benchos.api.iam.internal.web.dto.TechnicianOptionResponse;
import com.benchos.api.iam.internal.web.dto.UserResponse;
import com.benchos.api.shared.exception.BusinessRuleException;
import com.benchos.api.shared.exception.ConflictException;
import com.benchos.api.shared.exception.ResourceNotFoundException;
import com.benchos.api.shared.security.SecurityUtils;
import com.benchos.api.shared.tenant.TenantContext;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class UserManagementService implements UserDirectoryQuery {

    private static final List<Role> ASSIGNABLE_TECH_ROLES = List.of(Role.OWNER, Role.TECHNICIAN);

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final PasswordEncoder passwordEncoder;

    public UserManagementService(
            UserRepository userRepository,
            PasswordResetTokenRepository passwordResetTokenRepository,
            PasswordEncoder passwordEncoder
    ) {
        this.userRepository = userRepository;
        this.passwordResetTokenRepository = passwordResetTokenRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public UserResponse getCurrentUser() {
        UUID userId = SecurityUtils.requireCurrentUserId();
        UUID tenantId = TenantContext.requireCurrentTenant();
        User user = userRepository.findByIdAndTenantId(userId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        return UserResponse.from(user);
    }

    @Transactional(readOnly = true)
    public List<UserResponse> listUsers() {
        UUID tenantId = TenantContext.requireCurrentTenant();
        return userRepository.findAllByTenantIdOrderByCreatedAtAsc(tenantId)
                .stream()
                .map(UserResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public UserResponse getUser(UUID userId) {
        UUID tenantId = TenantContext.requireCurrentTenant();
        User user = userRepository.findByIdAndTenantId(userId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        return UserResponse.from(user);
    }

    @Transactional
    public UserResponse createUser(CreateUserRequest request) {
        UUID tenantId = TenantContext.requireCurrentTenant();
        String normalizedEmail = User.normalizeEmail(request.email());

        if (userRepository.existsByEmailIgnoreCase(normalizedEmail)) {
            throw new ConflictException("Email is already registered");
        }

        String passwordHash = passwordEncoder.encode(request.password());
        User user = userRepository.save(
                new User(
                        tenantId,
                        request.name(),
                        normalizedEmail,
                        passwordHash,
                        request.role(),
                        request.commissionPercent()
                )
        );
        return UserResponse.from(user);
    }

    @Transactional
    public UserResponse updateUserCommission(UUID userId, BigDecimal commissionPercent) {
        UUID tenantId = TenantContext.requireCurrentTenant();
        User user = userRepository.findByIdAndTenantId(userId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        user.updateCommissionPercent(commissionPercent);
        return UserResponse.from(userRepository.save(user));
    }

    @Transactional(readOnly = true)
    public List<TechnicianOptionResponse> listTechnicianOptions() {
        UUID tenantId = TenantContext.requireCurrentTenant();
        return listAssignableTechnicians(tenantId).stream()
                .map(TechnicianOptionResponse::from)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<UserSnapshot> findActiveById(UUID userId, UUID tenantId) {
        return userRepository.findByIdAndTenantIdAndActiveTrue(userId, tenantId)
                .map(UserManagementService::toSnapshot);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, UserSnapshot> findAllByIds(Collection<UUID> userIds, UUID tenantId) {
        if (userIds == null || userIds.isEmpty()) {
            return Map.of();
        }
        return userRepository.findAllByTenantIdAndIdIn(tenantId, userIds).stream()
                .collect(Collectors.toMap(User::getId, UserManagementService::toSnapshot));
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserSnapshot> listAssignableTechnicians(UUID tenantId) {
        return userRepository.findAllByTenantIdAndActiveTrueAndRoleInOrderByNameAsc(
                        tenantId,
                        ASSIGNABLE_TECH_ROLES
                )
                .stream()
                .map(UserManagementService::toSnapshot)
                .toList();
    }

    private static UserSnapshot toSnapshot(User user) {
        return new UserSnapshot(
                user.getId(),
                user.getName(),
                user.getRole(),
                user.isActive(),
                user.getCommissionPercent()
        );
    }

    @Transactional
    public UserResponse updateUserStatus(UUID userId, boolean active) {
        UUID tenantId = TenantContext.requireCurrentTenant();
        UUID currentUserId = SecurityUtils.requireCurrentUserId();

        if (!active && currentUserId.equals(userId)) {
            throw new BusinessRuleException("You cannot deactivate your own account");
        }

        User user = userRepository.findByIdAndTenantId(userId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        user.setActive(active);
        return UserResponse.from(userRepository.save(user));
    }

    @Transactional
    public UserResponse resetUserPassword(UUID userId, String newPassword) {
        UUID tenantId = TenantContext.requireCurrentTenant();
        UUID currentUserId = SecurityUtils.requireCurrentUserId();

        User user = userRepository.findByIdAndTenantId(userId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (user.getRole() == Role.OWNER && !currentUserId.equals(userId)) {
            throw new BusinessRuleException("You cannot reset another owner's password");
        }

        user.updatePasswordHash(passwordEncoder.encode(newPassword));
        passwordResetTokenRepository.invalidateAllActiveForUser(user.getId(), Instant.now());
        return UserResponse.from(userRepository.save(user));
    }
}
