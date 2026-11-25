package com.benchos.api.order.internal.web;

import com.benchos.api.order.internal.service.PartCatalogService;
import com.benchos.api.order.internal.web.dto.PartCatalogResponse;
import com.benchos.api.order.internal.web.dto.UpsertPartCatalogRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/parts-catalog")
public class PartCatalogController {

    private final PartCatalogService partCatalogService;

    public PartCatalogController(PartCatalogService partCatalogService) {
        this.partCatalogService = partCatalogService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('OWNER', 'ATTENDANT', 'TECHNICIAN')")
    public List<PartCatalogResponse> list(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String category
    ) {
        return partCatalogService.list(query, category);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('OWNER', 'TECHNICIAN')")
    public PartCatalogResponse create(@Valid @RequestBody UpsertPartCatalogRequest request) {
        return partCatalogService.create(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('OWNER', 'TECHNICIAN')")
    public PartCatalogResponse update(
            @PathVariable UUID id,
            @Valid @RequestBody UpsertPartCatalogRequest request
    ) {
        return partCatalogService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('OWNER', 'TECHNICIAN')")
    public void delete(@PathVariable UUID id) {
        partCatalogService.delete(id);
    }

    @PostMapping("/seed-defaults")
    @PreAuthorize("hasAnyRole('OWNER', 'TECHNICIAN')")
    public List<PartCatalogResponse> seedDefaults() {
        return partCatalogService.seedDefaults();
    }
}
