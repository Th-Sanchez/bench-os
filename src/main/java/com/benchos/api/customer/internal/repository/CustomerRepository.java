package com.benchos.api.customer.internal.repository;

import com.benchos.api.customer.internal.entity.Customer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CustomerRepository extends JpaRepository<Customer, UUID> {

    Optional<Customer> findByIdAndTenantIdAndDeletedAtIsNull(UUID id, UUID tenantId);

    boolean existsByTenantIdAndPhoneAndDeletedAtIsNull(UUID tenantId, String phone);

    boolean existsByTenantIdAndPhoneAndDeletedAtIsNullAndIdNot(UUID tenantId, String phone, UUID id);

    List<Customer> findAllByTenantIdAndDeletedAtIsNullOrderByNameAscCreatedAtDesc(UUID tenantId);

    @Query("""
            SELECT c FROM Customer c
            WHERE c.tenantId = :tenantId
              AND c.deletedAt IS NULL
              AND LOWER(c.name) LIKE LOWER(CONCAT('%', :nameTerm, '%'))
            ORDER BY c.name ASC, c.createdAt DESC
            """)
    List<Customer> searchActiveByName(
            @Param("tenantId") UUID tenantId,
            @Param("nameTerm") String nameTerm
    );

    @Query("""
            SELECT c FROM Customer c
            WHERE c.tenantId = :tenantId
              AND c.deletedAt IS NULL
              AND (
                    LOWER(c.name) LIKE LOWER(CONCAT('%', :nameTerm, '%'))
                    OR c.phone LIKE CONCAT('%', :phoneDigits, '%')
                  )
            ORDER BY c.name ASC, c.createdAt DESC
            """)
    List<Customer> searchActiveByNameOrPhone(
            @Param("tenantId") UUID tenantId,
            @Param("nameTerm") String nameTerm,
            @Param("phoneDigits") String phoneDigits
    );
}
