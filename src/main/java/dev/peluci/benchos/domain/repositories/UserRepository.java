package dev.peluci.benchos.domain.repositories;

import dev.peluci.benchos.domain.entities.User;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository {
    User save(User user);
    Optional<User> findById(UUID id);
    Optional<User> findByEmailAndTenantId(String email, UUID tenantId);
    List<User> findAllByTenantId(UUID tenantId);
}
