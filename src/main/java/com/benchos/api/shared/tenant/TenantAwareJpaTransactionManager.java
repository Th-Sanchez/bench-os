package com.benchos.api.shared.tenant;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.Session;
import org.springframework.orm.jpa.EntityManagerHolder;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.UUID;

/**
 * Custom {@link JpaTransactionManager} that deterministically activates the Hibernate
 * {@code tenantFilter} at the beginning of every JPA transaction whenever a tenant is bound to
 * {@link TenantContext}.
 *
 * <p>Note: Hibernate {@code @Filter} applies to JPQL/Criteria queries, NOT to
 * {@code EntityManager.find()} / {@code JpaRepository.findById()}. Single-entity lookups must
 * still use explicit tenant-scoped queries (e.g. {@code findByIdAndTenantId}).
 */
public class TenantAwareJpaTransactionManager extends JpaTransactionManager {

    public TenantAwareJpaTransactionManager(EntityManagerFactory entityManagerFactory) {
        super(entityManagerFactory);
    }

    @Override
    protected void doBegin(Object transaction, TransactionDefinition definition) {
        super.doBegin(transaction, definition);

        EntityManagerFactory emf = getEntityManagerFactory();
        if (emf == null) {
            return;
        }

        EntityManagerHolder holder = (EntityManagerHolder) TransactionSynchronizationManager.getResource(emf);
        if (holder == null) {
            return;
        }

        EntityManager entityManager = holder.getEntityManager();
        Session session = entityManager.unwrap(Session.class);

        UUID tenantId = TenantContext.getCurrentTenant().orElse(null);
        if (tenantId != null) {
            session.enableFilter(TenantAwareEntity.TENANT_FILTER)
                    .setParameter(TenantAwareEntity.TENANT_ID_PARAM, tenantId);
        } else {
            session.disableFilter(TenantAwareEntity.TENANT_FILTER);
        }
    }
}
