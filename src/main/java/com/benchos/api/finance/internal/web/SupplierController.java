package com.benchos.api.finance.internal.web;

import com.benchos.api.finance.internal.service.SupplierService;
import com.benchos.api.finance.internal.web.dto.CreateSupplierRequest;
import com.benchos.api.finance.internal.web.dto.SupplierResponse;
import com.benchos.api.finance.internal.web.dto.UpdateSupplierRequest;
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
@RequestMapping("/api/v1/finance/suppliers")
@PreAuthorize("hasRole('OWNER')")
public class SupplierController {

    private final SupplierService supplierService;

    public SupplierController(SupplierService supplierService) {
        this.supplierService = supplierService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SupplierResponse createSupplier(@Valid @RequestBody CreateSupplierRequest request) {
        return supplierService.createSupplier(request);
    }

    @GetMapping
    public List<SupplierResponse> listSuppliers(@RequestParam(required = false) String query) {
        return supplierService.listSuppliers(query);
    }

    @GetMapping("/{id}")
    public SupplierResponse getSupplier(@PathVariable UUID id) {
        return supplierService.getSupplier(id);
    }

    @PutMapping("/{id}")
    public SupplierResponse updateSupplier(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateSupplierRequest request
    ) {
        return supplierService.updateSupplier(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteSupplier(@PathVariable UUID id) {
        supplierService.deleteSupplier(id);
    }
}
