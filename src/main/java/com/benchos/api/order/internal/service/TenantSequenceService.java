package com.benchos.api.order.internal.service;

import com.benchos.api.order.internal.entity.TenantSequence;
import com.benchos.api.order.internal.repository.TenantSequenceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class TenantSequenceService {

    private final TenantSequenceRepository tenantSequenceRepository;

    public TenantSequenceService(TenantSequenceRepository tenantSequenceRepository) {
        this.tenantSequenceRepository = tenantSequenceRepository;
    }

    @Transactional
    public long nextOrderNumber(UUID tenantId) {
        tenantSequenceRepository.insertIfAbsent(tenantId);
        TenantSequence sequence = tenantSequenceRepository.findByTenantIdForUpdate(tenantId)
                .orElseThrow(() -> new IllegalStateException("Tenant sequence row not found for tenant " + tenantId));
        long nextNumber = sequence.nextOrderNumber();
        tenantSequenceRepository.save(sequence);
        return nextNumber;
    }
}
