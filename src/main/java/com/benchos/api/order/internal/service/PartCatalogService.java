package com.benchos.api.order.internal.service;

import com.benchos.api.order.internal.entity.PartCatalogItem;
import com.benchos.api.order.internal.repository.PartCatalogRepository;
import com.benchos.api.order.internal.web.dto.PartCatalogResponse;
import com.benchos.api.order.internal.web.dto.UpsertPartCatalogRequest;
import com.benchos.api.shared.exception.BusinessRuleException;
import com.benchos.api.shared.exception.ResourceNotFoundException;
import com.benchos.api.shared.tenant.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class PartCatalogService {

    private record DefaultPartSeed(
            String name,
            String applianceCategory,
            BigDecimal averageCost,
            BigDecimal suggestedPrice,
            String notes
    ) {
    }

    private static final List<DefaultPartSeed> DEFAULT_SEEDS = List.of(
            new DefaultPartSeed("Magnetron Universal", "Micro-ondas", new BigDecimal("65.00"), new BigDecimal("130.00"), "Compatível 2M219J / 2M246"),
            new DefaultPartSeed("Fusível Alta Tensão 0.7A", "Micro-ondas", new BigDecimal("6.00"), new BigDecimal("25.00"), "Com porta-fusível HV"),
            new DefaultPartSeed("Capacitor Alta Tensão", "Micro-ondas", new BigDecimal("22.00"), new BigDecimal("55.00"), "0.90uF / 0.95uF 2100V"),
            new DefaultPartSeed("Microchave da Porta", "Micro-ondas, Air Fryer", new BigDecimal("5.00"), new BigDecimal("20.00"), "Chave 16A NA/NF"),
            new DefaultPartSeed("Diodo Alta Tensão", "Micro-ondas", new BigDecimal("5.00"), new BigDecimal("20.00"), "HVR-1X / CL01-12"),
            new DefaultPartSeed("Membrana do Painel", "Micro-ondas", new BigDecimal("24.00"), new BigDecimal("65.00"), "Teclado frontal adesivo"),
            new DefaultPartSeed("Resistência Air Fryer", "Air Fryer", new BigDecimal("35.00"), new BigDecimal("85.00"), "127V/220V conforme modelo"),
            new DefaultPartSeed("Fusível Térmico 185°C", "Air Fryer, Cafeteira, Ferro de Passar", new BigDecimal("4.00"), new BigDecimal("20.00"), "Proteção térmica 10A/15A"),
            new DefaultPartSeed("Acoplamento / Arraste", "Liquidificador", new BigDecimal("9.00"), new BigDecimal("30.00"), "Engrenagem do motor/copo"),
            new DefaultPartSeed("Capacitor de Partida", "Ventilador", new BigDecimal("7.00"), new BigDecimal("25.00"), "2uF a 4uF 250V/400V")
    );

    private final PartCatalogRepository partCatalogRepository;

    public PartCatalogService(PartCatalogRepository partCatalogRepository) {
        this.partCatalogRepository = partCatalogRepository;
    }

    @Transactional(readOnly = true)
    public List<PartCatalogResponse> list(String query, String applianceCategory) {
        UUID tenantId = TenantContext.requireCurrentTenant();
        String normalizedQuery = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        String normalizedCategory = applianceCategory == null ? "" : applianceCategory.trim().toLowerCase(Locale.ROOT);

        return partCatalogRepository.findAllByTenantIdAndActiveTrueOrderByNameAsc(tenantId).stream()
                .filter(item -> {
                    if (!normalizedCategory.isEmpty() && !matchesCategory(item.getApplianceCategory(), normalizedCategory)) {
                        return false;
                    }
                    if (!normalizedQuery.isEmpty()) {
                        String haystack = (item.getName() + " "
                                + (item.getApplianceCategory() == null ? "" : item.getApplianceCategory()) + " "
                                + (item.getNotes() == null ? "" : item.getNotes()))
                                .toLowerCase(Locale.ROOT);
                        return haystack.contains(normalizedQuery);
                    }
                    return true;
                })
                .map(PartCatalogResponse::from)
                .toList();
    }

    private static boolean matchesCategory(String itemCategoriesRaw, String targetCategoryLower) {
        if (itemCategoriesRaw == null || itemCategoriesRaw.isBlank()) {
            return true;
        }
        for (String token : itemCategoriesRaw.split("[,;]")) {
            String normalized = token.trim().toLowerCase(Locale.ROOT);
            if (normalized.isEmpty()
                    || normalized.equals("geral")
                    || normalized.equals("universal")
                    || normalized.equals("todos")
                    || normalized.equals(targetCategoryLower)) {
                return true;
            }
        }
        return false;
    }

    @Transactional
    public PartCatalogResponse create(UpsertPartCatalogRequest request) {
        UUID tenantId = TenantContext.requireCurrentTenant();
        String trimmedName = request.name().trim();

        if (partCatalogRepository.existsByTenantIdAndActiveTrueAndNameIgnoreCase(tenantId, trimmedName)) {
            throw new BusinessRuleException("Já existe uma peça cadastrada com este nome");
        }

        PartCatalogItem item = new PartCatalogItem(
                tenantId,
                trimmedName,
                request.applianceCategory(),
                request.averageCost(),
                request.suggestedPrice(),
                request.notes()
        );
        return PartCatalogResponse.from(partCatalogRepository.save(item));
    }

    @Transactional
    public PartCatalogResponse update(UUID id, UpsertPartCatalogRequest request) {
        UUID tenantId = TenantContext.requireCurrentTenant();
        PartCatalogItem existing = partCatalogRepository.findByIdAndTenantIdAndActiveTrue(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Part catalog item not found"));

        String trimmedName = request.name().trim();
        if (partCatalogRepository.existsByTenantIdAndActiveTrueAndNameIgnoreCaseAndIdNot(tenantId, trimmedName, id)) {
            throw new BusinessRuleException("Já existe outra peça cadastrada com este nome");
        }

        existing.update(
                trimmedName,
                request.applianceCategory(),
                request.averageCost(),
                request.suggestedPrice(),
                request.notes()
        );
        return PartCatalogResponse.from(partCatalogRepository.save(existing));
    }

    @Transactional
    public void delete(UUID id) {
        UUID tenantId = TenantContext.requireCurrentTenant();
        PartCatalogItem existing = partCatalogRepository.findByIdAndTenantIdAndActiveTrue(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Part catalog item not found"));
        existing.deactivate();
        partCatalogRepository.save(existing);
    }

    @Transactional
    public List<PartCatalogResponse> seedDefaults() {
        UUID tenantId = TenantContext.requireCurrentTenant();
        List<PartCatalogItem> toCreate = new ArrayList<>();
        for (DefaultPartSeed seed : DEFAULT_SEEDS) {
            if (!partCatalogRepository.existsByTenantIdAndActiveTrueAndNameIgnoreCase(tenantId, seed.name())) {
                toCreate.add(new PartCatalogItem(
                        tenantId,
                        seed.name(),
                        seed.applianceCategory(),
                        seed.averageCost(),
                        seed.suggestedPrice(),
                        seed.notes()
                ));
            }
        }
        if (!toCreate.isEmpty()) {
            partCatalogRepository.saveAll(toCreate);
        }
        return partCatalogRepository.findAllByTenantIdAndActiveTrueOrderByNameAsc(tenantId).stream()
                .map(PartCatalogResponse::from)
                .toList();
    }
}
