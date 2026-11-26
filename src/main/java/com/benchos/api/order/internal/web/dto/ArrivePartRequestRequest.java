package com.benchos.api.order.internal.web.dto;

import com.benchos.api.order.PaymentMethod;
import jakarta.validation.constraints.DecimalMin;

import java.math.BigDecimal;

public record ArrivePartRequestRequest(
        @DecimalMin("0.00")
        BigDecimal actualUnitCost,
        Boolean addToItemPartsCost,
        Boolean moveItemToInRepair,
        Boolean recordExpense,
        PaymentMethod paymentMethod
) {
}
