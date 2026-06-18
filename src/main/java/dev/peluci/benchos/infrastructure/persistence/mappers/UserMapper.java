package dev.peluci.benchos.infrastructure.persistence.mappers;

import dev.peluci.benchos.domain.entities.User;
import dev.peluci.benchos.infrastructure.persistence.entities.UserEntity;

public class UserMapper {

    public User toDomain(UserEntity entity) {
        return new User(
            entity.getId(),
            entity.isActive(),
            entity.getDtInsert(),
            entity.getDtUpdate(),
            entity.getDtDelete(),
            entity.getTenantId(),
            entity.getName(),
            entity.getEmail(),
            entity.getPasswordHash(),
            entity.getRole(),
            entity.isFirstLogin()
        );
    }

    public UserEntity toEntity(User domain) {
        return UserEntity.builder()
            .id(domain.getId())
            .active(domain.isActive())
            .dtInsert(domain.getDtInsert())
            .dtUpdate(domain.getDtUpdate())
            .dtDelete(domain.getDtDelete())
            .tenantId(domain.getTenantId())
            .name(domain.getName())
            .email(domain.getEmail())
            .passwordHash(domain.getPasswordHash())
            .role(domain.getRole())
            .firstLogin(domain.isFirstLogin())
            .build();
    }
}
