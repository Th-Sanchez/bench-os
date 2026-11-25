package com.benchos.api.finance.internal.web;

import com.benchos.api.finance.TransactionCategory;
import com.benchos.api.finance.TransactionPaymentMethod;
import com.benchos.api.finance.TransactionStatus;
import com.benchos.api.finance.TransactionType;
import com.benchos.api.finance.internal.service.FinanceService;
import com.benchos.api.finance.internal.web.dto.CreateFinancialTransactionRequest;
import com.benchos.api.finance.internal.web.dto.DailyCashClosingResponse;
import com.benchos.api.finance.internal.web.dto.FinancialSummaryResponse;
import com.benchos.api.finance.internal.web.dto.FinancialTransactionResponse;
import com.benchos.api.finance.internal.web.dto.ItemPaymentSummaryResponse;
import com.benchos.api.finance.internal.web.dto.PayTransactionRequest;
import com.benchos.api.shared.web.PageResponse;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/finance")
@PreAuthorize("hasRole('OWNER')")
public class FinanceController {

    private final FinanceService financeService;

    public FinanceController(FinanceService financeService) {
        this.financeService = financeService;
    }

    @PostMapping("/transactions")
    @ResponseStatus(HttpStatus.CREATED)
    public FinancialTransactionResponse createTransaction(
            @Valid @RequestBody CreateFinancialTransactionRequest request
    ) {
        return financeService.createTransaction(request);
    }

    @GetMapping("/transactions")
    public PageResponse<FinancialTransactionResponse> listTransactions(
            @RequestParam(required = false) TransactionType type,
            @RequestParam(required = false) TransactionCategory category,
            @RequestParam(required = false) TransactionStatus status,
            @RequestParam(required = false) TransactionPaymentMethod paymentMethod,
            @RequestParam(required = false) UUID supplierId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Boolean overdueOnly,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return financeService.listTransactions(
                type, category, status, paymentMethod, supplierId, from, to, overdueOnly, page, size
        );
    }

    @GetMapping("/transactions/{id}")
    public FinancialTransactionResponse getTransaction(@PathVariable UUID id) {
        return financeService.getTransaction(id);
    }

    @PostMapping("/transactions/{id}/pay")
    public FinancialTransactionResponse payTransaction(
            @PathVariable UUID id,
            @RequestBody(required = false) PayTransactionRequest request
    ) {
        return financeService.markTransactionAsPaid(id, request);
    }

    @GetMapping("/summary")
    public FinancialSummaryResponse getSummary(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        return financeService.getSummary(from, to);
    }

    @GetMapping("/daily-closing")
    public DailyCashClosingResponse getDailyCashClosing(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) String zone
    ) {
        return financeService.getDailyCashClosing(date, zone);
    }

    @GetMapping("/order-items/{itemId}/payments")
    @PreAuthorize("hasAnyRole('OWNER', 'ATTENDANT', 'TECHNICIAN')")
    public List<ItemPaymentSummaryResponse> listRepairPaymentsForItem(@PathVariable UUID itemId) {
        return financeService.listRepairPaymentsForItem(itemId);
    }
}
