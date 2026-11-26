package com.benchos.api.iam.internal.entity;

import com.benchos.api.shared.exception.BusinessRuleException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "tenant_settings")
public class TenantSettings {

    public static final BigDecimal DEFAULT_LABOR_COST = BigDecimal.ZERO.setScale(2);
    public static final int DEFAULT_ESTIMATED_DAYS = 3;
    public static final int DEFAULT_ABANDONMENT_DAYS = 180;
    public static final boolean DEFAULT_DELIVERY_SIGNATURE_ENABLED = false;
    public static final String DEFAULT_WHATSAPP_INTAKE_TEMPLATE =
            "Olá {customerName}! Registramos sua OS #{orderNumber}:\n{appliances}\nAcompanhe o andamento em tempo real: {trackingUrl}";
    public static final String DEFAULT_WHATSAPP_QUOTE_TEMPLATE =
            "Olá {customerName}! Segue o orçamento da OS #{orderNumber}:\n{appliances}\nTotal: {totalPrice}\nAprove ou acompanhe por aqui: {trackingUrl}";
    public static final String DEFAULT_WHATSAPP_READY_TEMPLATE =
            "Olá {customerName}! Seus aparelhos da OS #{orderNumber} estão prontos para retirada:\n{appliances}\nTotal: {totalPrice}\nDetalhes: {trackingUrl}";

    @Id
    @Column(name = "tenant_id")
    private UUID tenantId;

    @Column(name = "default_labor_cost", nullable = false, precision = 10, scale = 2)
    private BigDecimal defaultLaborCost;

    @Column(name = "default_estimated_days", nullable = false)
    private int defaultEstimatedDays;

    @Column(name = "abandonment_days", nullable = false)
    private int abandonmentDays;

    @Column(name = "whatsapp_intake_template", columnDefinition = "TEXT")
    private String whatsappIntakeTemplate;

    @Column(name = "whatsapp_quote_template", columnDefinition = "TEXT")
    private String whatsappQuoteTemplate;

    @Column(name = "whatsapp_ready_template", columnDefinition = "TEXT")
    private String whatsappReadyTemplate;

    @Column(name = "custom_appliance_types", columnDefinition = "TEXT")
    private String customApplianceTypes;

    @Column(name = "google_review_url", length = 500)
    private String googleReviewUrl;

    @Column(name = "delivery_signature_enabled", nullable = false)
    private boolean deliverySignatureEnabled;

    @Column(name = "intake_checklist_templates", columnDefinition = "TEXT")
    private String intakeChecklistTemplates;

    protected TenantSettings() {
    }

    public static TenantSettings defaultsFor(UUID tenantId) {
        TenantSettings settings = new TenantSettings();
        settings.tenantId = tenantId;
        settings.defaultLaborCost = DEFAULT_LABOR_COST;
        settings.defaultEstimatedDays = DEFAULT_ESTIMATED_DAYS;
        settings.abandonmentDays = DEFAULT_ABANDONMENT_DAYS;
        settings.whatsappIntakeTemplate = DEFAULT_WHATSAPP_INTAKE_TEMPLATE;
        settings.whatsappQuoteTemplate = DEFAULT_WHATSAPP_QUOTE_TEMPLATE;
        settings.whatsappReadyTemplate = DEFAULT_WHATSAPP_READY_TEMPLATE;
        settings.customApplianceTypes = null;
        settings.googleReviewUrl = null;
        settings.deliverySignatureEnabled = DEFAULT_DELIVERY_SIGNATURE_ENABLED;
        settings.intakeChecklistTemplates = null;
        return settings;
    }

    public void update(
            BigDecimal defaultLaborCost,
            int defaultEstimatedDays,
            int abandonmentDays,
            String whatsappIntakeTemplate,
            String whatsappQuoteTemplate,
            String whatsappReadyTemplate
    ) {
        update(
                defaultLaborCost,
                defaultEstimatedDays,
                abandonmentDays,
                whatsappIntakeTemplate,
                whatsappQuoteTemplate,
                whatsappReadyTemplate,
                null,
                null
        );
    }

    public void update(
            BigDecimal defaultLaborCost,
            int defaultEstimatedDays,
            int abandonmentDays,
            String whatsappIntakeTemplate,
            String whatsappQuoteTemplate,
            String whatsappReadyTemplate,
            String googleReviewUrl
    ) {
        update(
                defaultLaborCost,
                defaultEstimatedDays,
                abandonmentDays,
                whatsappIntakeTemplate,
                whatsappQuoteTemplate,
                whatsappReadyTemplate,
                googleReviewUrl,
                null
        );
    }

    public void update(
            BigDecimal defaultLaborCost,
            int defaultEstimatedDays,
            int abandonmentDays,
            String whatsappIntakeTemplate,
            String whatsappQuoteTemplate,
            String whatsappReadyTemplate,
            String googleReviewUrl,
            Boolean deliverySignatureEnabled
    ) {
        this.defaultLaborCost = defaultLaborCost;
        this.defaultEstimatedDays = defaultEstimatedDays;
        this.abandonmentDays = abandonmentDays;
        if (whatsappIntakeTemplate != null) {
            this.whatsappIntakeTemplate = whatsappIntakeTemplate;
        }
        this.whatsappQuoteTemplate = whatsappQuoteTemplate;
        if (whatsappReadyTemplate != null) {
            this.whatsappReadyTemplate = whatsappReadyTemplate;
        }
        if (googleReviewUrl != null) {
            this.googleReviewUrl = normalizeAndValidateUrl(googleReviewUrl);
        }
        if (deliverySignatureEnabled != null) {
            this.deliverySignatureEnabled = deliverySignatureEnabled;
        }
    }

    private static String normalizeAndValidateUrl(String rawUrl) {
        String trimmed = rawUrl.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        if (trimmed.length() > 500) {
            throw new BusinessRuleException("Google review URL must be at most 500 characters");
        }
        try {
            URI uri = URI.create(trimmed);
            String scheme = uri.getScheme();
            String host = uri.getHost();
            if (scheme == null || (!"https".equalsIgnoreCase(scheme) && !"http".equalsIgnoreCase(scheme))
                    || host == null || host.isBlank()) {
                throw new BusinessRuleException("Google review URL must be a valid http(s) URL");
            }
            return trimmed;
        } catch (IllegalArgumentException ex) {
            throw new BusinessRuleException("Google review URL must be a valid http(s) URL");
        }
    }

    public List<String> getCustomApplianceTypeList() {
        if (this.customApplianceTypes == null || this.customApplianceTypes.isBlank()) {
            return List.of();
        }
        return Arrays.stream(this.customApplianceTypes.split("\n"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    public void addCustomApplianceType(String rawName) {
        String normalized = rawName == null ? "" : rawName.trim();
        if (normalized.isEmpty()) {
            return;
        }
        List<String> current = new ArrayList<>(getCustomApplianceTypeList());
        boolean exists = current.stream().anyMatch(item -> item.equalsIgnoreCase(normalized));
        if (!exists) {
            current.add(normalized);
            this.customApplianceTypes = String.join("\n", current);
        }
    }

    public void removeCustomApplianceType(String rawName) {
        if (rawName == null || rawName.isBlank()) {
            return;
        }
        String target = rawName.trim();
        List<String> updated = getCustomApplianceTypeList().stream()
                .filter(item -> !item.equalsIgnoreCase(target))
                .toList();
        this.customApplianceTypes = updated.isEmpty() ? null : String.join("\n", updated);
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

    public String getWhatsappIntakeTemplate() {
        return whatsappIntakeTemplate;
    }

    public String getWhatsappQuoteTemplate() {
        return whatsappQuoteTemplate;
    }

    public String getWhatsappReadyTemplate() {
        return whatsappReadyTemplate;
    }

    public String getGoogleReviewUrl() {
        return googleReviewUrl;
    }

    public boolean isDeliverySignatureEnabled() {
        return deliverySignatureEnabled;
    }

    public Map<String, List<String>> getIntakeChecklistTemplateMap() {
        return IntakeChecklistTemplateCodec.decodeOrDefault(this.intakeChecklistTemplates);
    }

    public void updateIntakeChecklistTemplates(Map<String, List<String>> templates) {
        this.intakeChecklistTemplates = IntakeChecklistTemplateCodec.encodeAndValidate(templates);
    }

    public void resetIntakeChecklistTemplates() {
        this.intakeChecklistTemplates = null;
    }
}
