package com.benchos.api.finance.internal.service;

import com.benchos.api.finance.internal.entity.Supplier;
import com.benchos.api.finance.internal.repository.SupplierRepository;
import com.benchos.api.finance.internal.web.dto.CreateSupplierRequest;
import com.benchos.api.finance.internal.web.dto.SupplierResponse;
import com.benchos.api.finance.internal.web.dto.UpdateSupplierRequest;
import com.benchos.api.shared.exception.ConflictException;
import com.benchos.api.shared.exception.ResourceNotFoundException;
import com.benchos.api.shared.tenant.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class SupplierService {

    private final SupplierRepository supplierRepository;

    public SupplierService(SupplierRepository supplierRepository) {
        this.supplierRepository = supplierRepository;
    }

    @Transactional
    public SupplierResponse createSupplier(CreateSupplierRequest request) {
        UUID tenantId = TenantContext.requireCurrentTenant();
        String trimmedName = request.name().trim();

        if (supplierRepository.existsByTenantIdAndNameIgnoreCaseAndDeletedAtIsNull(tenantId, trimmedName)) {
            throw new ConflictException("A supplier with this name already exists");
        }

        Supplier saved = supplierRepository.save(
                new Supplier(tenantId, trimmedName, request.phone(), request.notes())
        );
        return SupplierResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public List<SupplierResponse> listSuppliers(String query) {
        UUID tenantId = TenantContext.requireCurrentTenant();
        List<Supplier> suppliers = (query == null || query.isBlank())
                ? supplierRepository.findAllByTenantIdAndDeletedAtIsNullOrderByNameAscCreatedAtDesc(tenantId)
                : supplierRepository.searchActiveByName(tenantId, query.trim());

        return suppliers.stream()
                .map(SupplierResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public SupplierResponse getSupplier(UUID supplierId) {
        UUID tenantId = TenantContext.requireCurrentTenant();
        return SupplierResponse.from(findActiveOrThrow(supplierId, tenantId));
    }

    @Transactional
    public SupplierResponse updateSupplier(UUID supplierId, UpdateSupplierRequest request) {
        UUID tenantId = TenantContext.requireCurrentTenant();
        Supplier supplier = findActiveOrThrow(supplierId, tenantId);
        String trimmedName = request.name().trim();

        if (supplierRepository.existsByTenantIdAndNameIgnoreCaseAndDeletedAtIsNullAndIdNot(
                tenantId, trimmedName, supplierId)) {
            throw new ConflictException("A supplier with this name already exists");
        }

        supplier.update(trimmedName, request.phone(), request.notes());
        return SupplierResponse.from(supplierRepository.save(supplier));
    }

    @Transactional
    public void deleteSupplier(UUID supplierId) {
        UUID tenantId = TenantContext.requireCurrentTenant();
        Supplier supplier = findActiveOrThrow(supplierId, tenantId);
        supplier.softDelete();
        supplierRepository.save(supplier);
    }

    private Supplier findActiveOrThrow(UUID supplierId, UUID tenantId) {
        return supplierRepository.findByIdAndTenantIdAndDeletedAtIsNull(supplierId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Supplier not found"));
    }
}
