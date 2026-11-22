package com.benchos.api.shared.security;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;
import java.util.UUID;

/**
 * Converts a validated Spring Security Jwt into an Authentication whose principal is {@link AuthenticatedUser}.
 */
public class BenchOsJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        UUID userId = parseUuidClaim(jwt.getSubject(), "sub");
        UUID tenantId = parseUuidClaim(jwt.getClaimAsString(JwtTokenService.CLAIM_TENANT_ID), JwtTokenService.CLAIM_TENANT_ID);
        String role = jwt.getClaimAsString(JwtTokenService.CLAIM_ROLE);

        if (role == null || role.isBlank()) {
            throw new BadCredentialsException("JWT is missing required 'role' claim");
        }

        AuthenticatedUser principal = new AuthenticatedUser(userId, tenantId, role);
        List<SimpleGrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("ROLE_" + role));
        return UsernamePasswordAuthenticationToken.authenticated(principal, jwt, authorities);
    }

    private UUID parseUuidClaim(String rawValue, String claimName) {
        if (rawValue == null || rawValue.isBlank()) {
            throw new BadCredentialsException("JWT is missing required '" + claimName + "' claim");
        }
        try {
            return UUID.fromString(rawValue);
        } catch (IllegalArgumentException ex) {
            throw new BadCredentialsException("JWT claim '" + claimName + "' is not a valid UUID", ex);
        }
    }
}
