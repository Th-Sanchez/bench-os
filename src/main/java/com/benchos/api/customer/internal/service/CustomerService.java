package com.benchos.api.customer.internal.service;

import com.benchos.api.customer.CustomerQuery;
import com.benchos.api.customer.CustomerSnapshot;
import com.benchos.api.customer.internal.entity.Customer;
import com.benchos.api.customer.internal.repository.CustomerRepository;
import com.benchos.api.customer.internal.web.dto.BatchImportCustomersRequest;
import com.benchos.api.customer.internal.web.dto.BatchImportCustomersResponse;
import com.benchos.api.customer.internal.web.dto.CreateCustomerRequest;
import com.benchos.api.customer.internal.web.dto.CustomerResponse;
import com.benchos.api.customer.internal.web.dto.UpdateCustomerRequest;
import com.benchos.api.shared.exception.BusinessRuleException;
import com.benchos.api.shared.exception.ConflictException;
import com.benchos.api.shared.exception.ResourceNotFoundException;
import com.benchos.api.shared.tenant.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class CustomerService implements CustomerQuery {

    private final CustomerRepository customerRepository;

    public CustomerService(CustomerRepository customerRepository) {
        this.customerRepository = customerRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<CustomerSnapshot> findActiveById(UUID customerId, UUID tenantId) {
        return customerRepository.findByIdAndTenantIdAndDeletedAtIsNull(customerId, tenantId)
                .map(CustomerService::toSnapshot);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, CustomerSnapshot> findAllByIds(Collection<UUID> customerIds, UUID tenantId) {
        if (customerIds == null || customerIds.isEmpty()) {
            return Map.of();
        }
        return customerRepository.findAllByIdInAndTenantId(customerIds, tenantId)
                .stream()
                .map(CustomerService::toSnapshot)
                .collect(Collectors.toMap(CustomerSnapshot::id, Function.identity()));
    }

    private static CustomerSnapshot toSnapshot(Customer customer) {
        return new CustomerSnapshot(
                customer.getId(),
                customer.getTenantId(),
                customer.getName(),
                customer.getPhone()
        );
    }

    @Transactional
    public CustomerResponse createCustomer(CreateCustomerRequest request) {
        UUID tenantId = TenantContext.requireCurrentTenant();
        String normalizedPhone = Customer.normalizePhone(request.phone());

        if (customerRepository.existsByTenantIdAndPhoneAndDeletedAtIsNull(tenantId, normalizedPhone)) {
            throw new ConflictException("A customer with this phone number already exists");
        }

        Customer customer = customerRepository.save(
                new Customer(tenantId, request.name(), normalizedPhone, request.document(), request.notes())
        );
        return CustomerResponse.from(customer);
    }

    @Transactional
    public BatchImportCustomersResponse batchImportCustomers(BatchImportCustomersRequest request) {
        UUID tenantId = TenantContext.requireCurrentTenant();

        record NormalizedEntry(CreateCustomerRequest source, String normalizedPhone) {
        }

        List<NormalizedEntry> validCandidates = new ArrayList<>();
        Set<String> candidatePhones = new HashSet<>();
        int invalidSkipped = 0;

        for (CreateCustomerRequest item : request.customers()) {
            try {
                String normalizedPhone = Customer.normalizePhone(item.phone());
                validCandidates.add(new NormalizedEntry(item, normalizedPhone));
                candidatePhones.add(normalizedPhone);
            } catch (BusinessRuleException ex) {
                invalidSkipped++;
            }
        }

        Set<String> seenPhones = candidatePhones.isEmpty()
                ? new HashSet<>()
                : new HashSet<>(customerRepository.findActivePhonesByTenantIdAndPhoneIn(tenantId, candidatePhones));

        List<Customer> toSave = new ArrayList<>();
        int duplicatesSkipped = 0;

        for (NormalizedEntry candidate : validCandidates) {
            if (!seenPhones.add(candidate.normalizedPhone())) {
                duplicatesSkipped++;
                continue;
            }
            CreateCustomerRequest src = candidate.source();
            toSave.add(new Customer(tenantId, src.name(), candidate.normalizedPhone(), src.document(), src.notes()));
        }

        List<CustomerResponse> saved = toSave.isEmpty()
                ? List.of()
                : customerRepository.saveAll(toSave).stream()
                        .map(CustomerResponse::from)
                        .toList();

        return new BatchImportCustomersResponse(saved.size(), duplicatesSkipped, invalidSkipped, saved);
    }

    @Transactional(readOnly = true)
    public List<CustomerResponse> searchCustomers(String query) {
        UUID tenantId = TenantContext.requireCurrentTenant();
        List<Customer> customers;

        if (query == null || query.isBlank()) {
            customers = customerRepository.findAllByTenantIdAndDeletedAtIsNullOrderByNameAscCreatedAtDesc(tenantId);
        } else {
            String trimmedQuery = query.trim();
            String phoneDigits = Customer.extractDigits(trimmedQuery);
            if (phoneDigits.isEmpty()) {
                customers = customerRepository.searchActiveByName(tenantId, trimmedQuery);
            } else {
                customers = customerRepository.searchActiveByNameOrPhone(tenantId, trimmedQuery, phoneDigits);
            }
        }

        return customers.stream()
                .map(CustomerResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public CustomerResponse getCustomer(UUID customerId) {
        UUID tenantId = TenantContext.requireCurrentTenant();
        Customer customer = findActiveOrThrow(customerId, tenantId);
        return CustomerResponse.from(customer);
    }

    @Transactional
    public CustomerResponse updateCustomer(UUID customerId, UpdateCustomerRequest request) {
        UUID tenantId = TenantContext.requireCurrentTenant();
        Customer customer = findActiveOrThrow(customerId, tenantId);
        String normalizedPhone = Customer.normalizePhone(request.phone());

        if (customerRepository.existsByTenantIdAndPhoneAndDeletedAtIsNullAndIdNot(tenantId, normalizedPhone, customerId)) {
            throw new ConflictException("A customer with this phone number already exists");
        }

        customer.update(request.name(), normalizedPhone, request.document(), request.notes());
        return CustomerResponse.from(customerRepository.save(customer));
    }

    @Transactional
    public void deleteCustomer(UUID customerId) {
        UUID tenantId = TenantContext.requireCurrentTenant();
        Customer customer = findActiveOrThrow(customerId, tenantId);
        customer.softDelete();
        customerRepository.save(customer);
    }

    private Customer findActiveOrThrow(UUID customerId, UUID tenantId) {
        return customerRepository.findByIdAndTenantIdAndDeletedAtIsNull(customerId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found"));
    }
}
