package com.benchos.api.iam.internal.service;

import com.benchos.api.iam.PushTokenQuery;
import com.benchos.api.iam.Role;
import com.benchos.api.iam.internal.entity.UserPushToken;
import com.benchos.api.iam.internal.repository.UserPushTokenRepository;
import com.benchos.api.iam.internal.web.dto.PushTokenResponse;
import com.benchos.api.iam.internal.web.dto.RegisterPushTokenRequest;
import com.benchos.api.shared.exception.BusinessRuleException;
import com.benchos.api.shared.security.AuthenticatedUser;
import com.benchos.api.shared.security.SecurityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class PushTokenService implements PushTokenQuery {

    private static final Logger log = LoggerFactory.getLogger(PushTokenService.class);
    private static final Pattern EXPO_TOKEN_PATTERN = Pattern.compile("^(ExponentPushToken|ExpoPushToken)\\[[^\\s\\]]+\\]$");

    private final UserPushTokenRepository pushTokenRepository;

    public PushTokenService(UserPushTokenRepository pushTokenRepository) {
        this.pushTokenRepository = pushTokenRepository;
    }

    @Transactional
    public PushTokenResponse registerForCurrentUser(RegisterPushTokenRequest request) {
        AuthenticatedUser currentUser = SecurityUtils.requireCurrentUser();
        String normalizedToken = normalizeAndValidateToken(request.expoPushToken());
        Instant now = Instant.now();

        Optional<UserPushToken> existingForCurrentUser = pushTokenRepository
                .findByExpoPushTokenAndTenantIdAndUserId(normalizedToken, currentUser.tenantId(), currentUser.userId());

        if (existingForCurrentUser.isPresent()) {
            UserPushToken token = existingForCurrentUser.get();
            token.refresh(request.platform(), request.deviceName(), now);
            return PushTokenResponse.from(pushTokenRepository.save(token));
        }

        // Physical device switched accounts or shops: detach any prior binding before saving.
        pushTokenRepository.deleteByExpoPushTokenNative(normalizedToken);

        UserPushToken created = new UserPushToken(
                currentUser.tenantId(),
                currentUser.userId(),
                normalizedToken,
                request.platform(),
                request.deviceName(),
                now
        );
        return PushTokenResponse.from(pushTokenRepository.save(created));
    }

    @Transactional
    public void unregisterForCurrentUser(String expoPushToken) {
        if (expoPushToken == null || expoPushToken.isBlank()) {
            return;
        }
        AuthenticatedUser currentUser = SecurityUtils.requireCurrentUser();
        pushTokenRepository.deleteByExpoPushTokenAndTenantIdAndUserId(
                expoPushToken.trim(),
                currentUser.tenantId(),
                currentUser.userId()
        );
    }

    @Override
    @Transactional(readOnly = true)
    public List<String> findActiveTokensForTenant(UUID tenantId) {
        return pushTokenRepository.findActiveTokensForTenant(tenantId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<String> findActiveTokensForTenantRoles(UUID tenantId, Collection<Role> roles) {
        if (roles == null || roles.isEmpty()) {
            return List.of();
        }
        return pushTokenRepository.findActiveTokensForTenantRoles(tenantId, roles);
    }

    @Override
    @Transactional
    public void revokeInvalidTokens(Collection<String> invalidTokens) {
        if (invalidTokens == null || invalidTokens.isEmpty()) {
            return;
        }
        int deleted = pushTokenRepository.deleteAllByExpoPushTokenInNative(invalidTokens);
        if (deleted > 0) {
            log.info("Pruned {} unregistered Expo push token(s)", deleted);
        }
    }

    private static String normalizeAndValidateToken(String rawToken) {
        String trimmed = rawToken == null ? "" : rawToken.trim();
        if (!EXPO_TOKEN_PATTERN.matcher(trimmed).matches()) {
            throw new BusinessRuleException(
                    "Invalid Expo push token format. Expected ExponentPushToken[...] or ExpoPushToken[...]"
            );
        }
        return trimmed;
    }
}
