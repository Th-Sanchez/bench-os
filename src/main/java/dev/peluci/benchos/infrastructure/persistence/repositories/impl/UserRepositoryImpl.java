package dev.peluci.benchos.infrastructure.persistence.repositories.impl;

import dev.peluci.benchos.domain.entities.User;
import dev.peluci.benchos.domain.repositories.UserRepository;
import dev.peluci.benchos.infrastructure.persistence.mappers.UserMapper;
import dev.peluci.benchos.infrastructure.persistence.repositories.jpa.UserJpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class UserRepositoryImpl implements UserRepository {

    private final UserJpaRepository userJpaRepository;
    private final UserMapper userMapper;

    @Override
    public User save(User user) {
        return userMapper.toDomain(
            userJpaRepository.save(userMapper.toEntity(user))
        );
    }

    @Override
    public Optional<User> findById(UUID id) {
        return userJpaRepository.findById(id).map(userMapper::toDomain);
    }

    @Override
    public Optional<User> findByEmailAndTenantId(String email, UUID tenantId) {
        return userJpaRepository
            .findByEmailAndTenantId(email, tenantId)
            .map(userMapper::toDomain);
    }

    @Override
    public List<User> findAllByTenantId(UUID tenantId) {
        return userJpaRepository
            .findAllByTenantId(tenantId)
            .stream()
            .map(userMapper::toDomain)
            .toList();
    }
}
