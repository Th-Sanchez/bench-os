package com.benchos.api.iam.internal.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "tenant_settings")
public class TenantSettings {

    public static final BigDecimal DEFAULT_LABOR_COST = BigDecimal.ZERO.setScale(2);
    public static final int DEFAULT_ESTIMATED_DAYS = 3;
    public static final int DEFAULT_ABANDONMENT_DAYS = 180;
    public static final String DEFAULT_WHATSAPP_QUOTE_TEMPLATE =
            "Hello {customerName}, here is the repair quote for your {appliance} (Order #{orderNumber}): {totalPrice}. Track status: {trackingUrl}";

    @Id
    @Column(name = "tenant_id")
    private UUID tenantId;

    @Column(name = "default_labor_cost", nullable = false, precision = 10, scale = 2)
    private BigDecimal defaultLaborCost;

    @Column(name = "default_estimated_days", nullable = false)
    private int defaultEstimatedDays;

    @Column(name = "abandonment_days", nullable = false)
    private int abandonmentDays;

    @Column(name = "whatsapp_quote_template", columnDefinition = "TEXT")
    private String whatsappQuoteTemplate;

    protected TenantSettings() {
    }

    public static TenantSettings defaultsFor(UUID tenantId) {
        TenantSettings settings = new TenantSettings();
        settings.tenantId = tenantId;
        settings.defaultLaborCost = DEFAULT_LABOR_COST;
        settings.defaultEstimatedDays = DEFAULT_ESTIMATED_DAYS;
        settings.abandonmentDays = DEFAULT_ABANDONMENT_DAYS;
        settings.whatsappQuoteTemplate = DEFAULT_WHATSAPP_QUOTE_TEMPLATE;
        return settings;
    }

    public void update(
            BigDecimal defaultLaborCost,
            int defaultEstimatedDays,
            int abandonmentDays,
            String whatsappQuoteTemplate
    ) {
        this.defaultLaborCost = defaultLaborCost;
        this.defaultEstimatedDays = defaultEstimatedDays;
        this.abandonmentDays = abandonmentDays;
        this.whatsappQuoteTemplate = whatsappQuoteTemplate;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public BigDecimal getDefaultLaborCost() {
        return defaultLaborCost;
    }

    public int getDefaultEstimatedDays() {
        return defaultEstimatedDays;
    }

    public int getAbandonmentDays() {
        return abandonmentDays;
    }

    public String getWhatsappQuoteTemplate() {
        return whatsappQuoteTemplate;
    }
}
