package com.benchos.api.iam.internal.service;

import com.benchos.api.iam.Role;
import com.benchos.api.iam.internal.entity.User;
import com.benchos.api.iam.internal.repository.PasswordResetTokenRepository;
import com.benchos.api.iam.internal.repository.UserRepository;
import com.benchos.api.iam.internal.web.dto.CreateUserRequest;
import com.benchos.api.iam.internal.web.dto.UserResponse;
import com.benchos.api.shared.exception.BusinessRuleException;
import com.benchos.api.shared.exception.ConflictException;
import com.benchos.api.shared.exception.ResourceNotFoundException;
import com.benchos.api.shared.security.SecurityUtils;
import com.benchos.api.shared.tenant.TenantContext;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class UserManagementService {

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
                new User(tenantId, request.name(), normalizedEmail, passwordHash, request.role())
        );
        return UserResponse.from(user);
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
