package com.benchos.api.iam.internal.service;

import com.benchos.api.iam.Role;
import com.benchos.api.iam.internal.entity.Tenant;
import com.benchos.api.iam.internal.entity.TenantSettings;
import com.benchos.api.iam.internal.entity.User;
import com.benchos.api.iam.internal.repository.TenantRepository;
import com.benchos.api.iam.internal.repository.TenantSettingsRepository;
import com.benchos.api.iam.internal.repository.UserRepository;
import com.benchos.api.iam.internal.web.dto.AuthResponse;
import com.benchos.api.iam.internal.web.dto.LoginRequest;
import com.benchos.api.iam.internal.web.dto.RegisterTenantRequest;
import com.benchos.api.iam.internal.web.dto.TenantResponse;
import com.benchos.api.iam.internal.web.dto.UserResponse;
import com.benchos.api.shared.exception.ConflictException;
import com.benchos.api.shared.security.JwtTokenService;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final TenantRepository tenantRepository;
    private final TenantSettingsRepository tenantSettingsRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService jwtTokenService;

    public AuthService(
            TenantRepository tenantRepository,
            TenantSettingsRepository tenantSettingsRepository,
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtTokenService jwtTokenService
    ) {
        this.tenantRepository = tenantRepository;
        this.tenantSettingsRepository = tenantSettingsRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenService = jwtTokenService;
    }

    @Transactional
    public AuthResponse register(RegisterTenantRequest request) {
        String normalizedEmail = User.normalizeEmail(request.email());
        if (userRepository.existsByEmailIgnoreCase(normalizedEmail)) {
            throw new ConflictException("Email is already registered");
        }

        Tenant tenant = tenantRepository.save(new Tenant(request.shopName(), request.document(), request.phone()));
        tenantSettingsRepository.save(TenantSettings.defaultsFor(tenant.getId()));

        String passwordHash = passwordEncoder.encode(request.password());
        User owner = userRepository.save(
                new User(tenant.getId(), request.ownerName(), normalizedEmail, passwordHash, Role.OWNER)
        );

        return issueAuthResponse(owner, tenant);
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        String normalizedEmail = User.normalizeEmail(request.email());
        User user = userRepository.findByEmailIgnoreCase(normalizedEmail)
                .orElseThrow(() -> new BadCredentialsException("Invalid email or password"));

        if (!user.isActive() || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BadCredentialsException("Invalid email or password");
        }

        Tenant tenant = tenantRepository.findById(user.getTenantId())
                .filter(Tenant::isActive)
                .orElseThrow(() -> new BadCredentialsException("Invalid email or password"));

        return issueAuthResponse(user, tenant);
    }

    private AuthResponse issueAuthResponse(User user, Tenant tenant) {
        JwtTokenService.IssuedToken token = jwtTokenService.generateToken(
                user.getId(),
                tenant.getId(),
                user.getRole().name()
        );
        return new AuthResponse(
                token.accessToken(),
                token.expiresAt(),
                UserResponse.from(user),
                TenantResponse.from(tenant)
        );
    }
}
