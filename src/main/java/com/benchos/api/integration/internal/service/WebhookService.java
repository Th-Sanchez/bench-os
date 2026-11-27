package com.benchos.api.integration.internal.service;

import com.benchos.api.integration.TenantModuleQuery;
import com.benchos.api.integration.WebhookEventType;
import com.benchos.api.integration.internal.entity.WebhookDelivery;
import com.benchos.api.integration.internal.entity.WebhookEndpoint;
import com.benchos.api.integration.internal.repository.WebhookDeliveryRepository;
import com.benchos.api.integration.internal.repository.WebhookEndpointRepository;
import com.benchos.api.integration.internal.web.dto.CreateWebhookEndpointRequest;
import com.benchos.api.integration.internal.web.dto.UpdateWebhookEndpointRequest;
import com.benchos.api.integration.internal.web.dto.WebhookDeliveryResponse;
import com.benchos.api.integration.internal.web.dto.WebhookEndpointResponse;
import com.benchos.api.shared.exception.BusinessRuleException;
import com.benchos.api.shared.exception.ResourceNotFoundException;
import com.benchos.api.shared.id.UuidV7Generator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class WebhookService {

    private static final Logger log = LoggerFactory.getLogger(WebhookService.class);
    private static final int MAX_ENDPOINTS_PER_TENANT = 10;
    private static final int DEFAULT_DELIVERY_LIMIT = 25;
    private static final int MAX_DELIVERY_LIMIT = 100;

    public static final String HEADER_EVENT = "X-BenchOS-Event";
    public static final String HEADER_DELIVERY = "X-BenchOS-Delivery";
    public static final String HEADER_TIMESTAMP = "X-BenchOS-Timestamp";
    public static final String HEADER_SIGNATURE = "X-BenchOS-Signature";

    private final WebhookEndpointRepository endpointRepository;
    private final WebhookDeliveryRepository deliveryRepository;
    private final TenantModuleQuery tenantModuleQuery;
    private final WebhookUrlValidator urlValidator;
    private final WebhookSigner signer;
    private final WebhookHttpSender httpSender;
    private final ObjectMapper objectMapper;

    public WebhookService(
            WebhookEndpointRepository endpointRepository,
            WebhookDeliveryRepository deliveryRepository,
            TenantModuleQuery tenantModuleQuery,
            WebhookUrlValidator urlValidator,
            WebhookSigner signer,
            WebhookHttpSender httpSender,
            ObjectMapper objectMapper
    ) {
        this.endpointRepository = endpointRepository;
        this.deliveryRepository = deliveryRepository;
        this.tenantModuleQuery = tenantModuleQuery;
        this.urlValidator = urlValidator;
        this.signer = signer;
        this.httpSender = httpSender;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public List<WebhookEndpointResponse> listEndpointsForTenant(UUID tenantId) {
        return endpointRepository.findAllByTenantIdOrderByCreatedAtDesc(tenantId).stream()
                .map(ep -> WebhookEndpointResponse.from(ep, null))
                .toList();
    }

    @Transactional
    public WebhookEndpointResponse createEndpointForTenant(UUID tenantId, CreateWebhookEndpointRequest request) {
        if (endpointRepository.countByTenantId(tenantId) >= MAX_ENDPOINTS_PER_TENANT) {
            throw new BusinessRuleException(
                    "Maximum number of webhook endpoints (" + MAX_ENDPOINTS_PER_TENANT + ") reached for this shop"
            );
        }

        URI validatedUri = urlValidator.validateAndParse(request.targetUrl());
        List<WebhookEventType> events = parseSubscribableEvents(request.events());
        String secret = signer.generateSecret();
        boolean active = request.active() == null || request.active();

        WebhookEndpoint endpoint = new WebhookEndpoint(
                tenantId,
                request.name(),
                validatedUri.toString(),
                secret,
                events,
                active,
                Instant.now()
        );
        WebhookEndpoint saved = endpointRepository.saveAndFlush(endpoint);
        return WebhookEndpointResponse.from(saved, secret);
    }

    @Transactional
    public WebhookEndpointResponse updateEndpointForTenant(
            UUID tenantId,
            UUID endpointId,
            UpdateWebhookEndpointRequest request
    ) {
        WebhookEndpoint endpoint = findEndpointOrThrow(endpointId, tenantId);

        String nextUrl = null;
        if (request.targetUrl() != null && !request.targetUrl().isBlank()) {
            nextUrl = urlValidator.validateAndParse(request.targetUrl()).toString();
        }
        List<WebhookEventType> nextEvents = request.events() != null
                ? parseSubscribableEvents(request.events())
                : null;

        endpoint.update(request.name(), nextUrl, nextEvents, request.active(), Instant.now());
        WebhookEndpoint saved = endpointRepository.saveAndFlush(endpoint);
        return WebhookEndpointResponse.from(saved, null);
    }

    @Transactional
    public WebhookEndpointResponse rotateSecretForTenant(UUID tenantId, UUID endpointId) {
        WebhookEndpoint endpoint = findEndpointOrThrow(endpointId, tenantId);
        String newSecret = signer.generateSecret();
        endpoint.rotateSecret(newSecret, Instant.now());
        WebhookEndpoint saved = endpointRepository.saveAndFlush(endpoint);
        return WebhookEndpointResponse.from(saved, newSecret);
    }

    @Transactional
    public void deleteEndpointForTenant(UUID tenantId, UUID endpointId) {
        WebhookEndpoint endpoint = findEndpointOrThrow(endpointId, tenantId);
        endpointRepository.delete(endpoint);
    }

    @Transactional(readOnly = true)
    public List<WebhookDeliveryResponse> listDeliveriesForTenant(UUID tenantId, UUID endpointId, int limit) {
        findEndpointOrThrow(endpointId, tenantId);
        int clamped = limit < 1 ? DEFAULT_DELIVERY_LIMIT : Math.min(limit, MAX_DELIVERY_LIMIT);
        return deliveryRepository
                .findAllByTenantIdAndWebhookEndpointIdOrderByCreatedAtDesc(
                        tenantId,
                        endpointId,
                        PageRequest.of(0, clamped)
                )
                .stream()
                .map(WebhookDeliveryResponse::from)
                .toList();
    }

    @Transactional
    public WebhookDeliveryResponse sendTestEventForTenant(UUID tenantId, UUID endpointId) {
        WebhookEndpoint endpoint = findEndpointOrThrow(endpointId, tenantId);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("message", "Evento de teste enviado pelo BenchOS");
        data.put("endpointId", endpoint.getId());
        data.put("endpointName", endpoint.getName());

        WebhookDelivery delivery = executeDelivery(
                endpoint,
                WebhookEventType.WEBHOOK_TEST,
                UuidV7Generator.next(),
                data
        );
        return WebhookDeliveryResponse.from(delivery);
    }

    @Transactional
    public WebhookDeliveryResponse retryDeliveryForTenant(UUID tenantId, UUID endpointId, UUID deliveryId) {
        WebhookEndpoint endpoint = findEndpointOrThrow(endpointId, tenantId);
        WebhookDelivery delivery = deliveryRepository
                .findByIdAndTenantIdAndWebhookEndpointId(deliveryId, tenantId, endpointId)
                .orElseThrow(() -> new ResourceNotFoundException("Webhook delivery not found"));

        sendAndRecordAttempt(endpoint, delivery);
        return WebhookDeliveryResponse.from(deliveryRepository.saveAndFlush(delivery));
    }

    @Transactional
    public void dispatchEvent(UUID tenantId, WebhookEventType eventType, Map<String, Object> data) {
        if (!tenantModuleQuery.isEnabled(tenantId, "outbound_webhooks")) {
            return;
        }
        List<WebhookEndpoint> activeEndpoints = endpointRepository
                .findAllByTenantIdAndActiveTrueOrderByCreatedAtAsc(tenantId);
        if (activeEndpoints.isEmpty()) {
            return;
        }

        UUID eventId = UuidV7Generator.next();
        for (WebhookEndpoint endpoint : activeEndpoints) {
            if (!endpoint.subscribesTo(eventType)) {
                continue;
            }
            try {
                executeDelivery(endpoint, eventType, eventId, data);
            } catch (Exception ex) {
                log.warn("Unexpected error dispatching webhook event={} tenant={} endpoint={}: {}",
                        eventType.eventName(), tenantId, endpoint.getId(), ex.getMessage());
            }
        }
    }

    private WebhookDelivery executeDelivery(
            WebhookEndpoint endpoint,
            WebhookEventType eventType,
            UUID eventId,
            Map<String, Object> data
    ) {
        Instant now = Instant.now();
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("id", eventId);
        envelope.put("event", eventType.eventName());
        envelope.put("tenantId", endpoint.getTenantId());
        envelope.put("occurredAt", now);
        envelope.put("data", data != null ? data : Map.of());

        String payloadJson = objectMapper.writeValueAsString(envelope);
        WebhookDelivery delivery = new WebhookDelivery(
                endpoint.getTenantId(),
                endpoint.getId(),
                eventType.eventName(),
                eventId,
                payloadJson,
                now
        );

        sendAndRecordAttempt(endpoint, delivery);
        return deliveryRepository.saveAndFlush(delivery);
    }

    private void sendAndRecordAttempt(WebhookEndpoint endpoint, WebhookDelivery delivery) {
        Instant attemptTime = Instant.now();
        URI targetUri;
        try {
            targetUri = urlValidator.validateAndParse(endpoint.getTargetUrl());
        } catch (BusinessRuleException ex) {
            delivery.recordAttempt(false, null, 0, null, ex.getMessage(), attemptTime);
            return;
        }

        long ts = attemptTime.getEpochSecond();
        String signature = signer.computeSignatureHeader(endpoint.getSigningSecret(), ts, delivery.getPayloadJson());

        Map<String, String> headers = new LinkedHashMap<>();
        headers.put(HEADER_EVENT, delivery.getEventType());
        headers.put(HEADER_DELIVERY, delivery.getId().toString());
        headers.put(HEADER_TIMESTAMP, String.valueOf(ts));
        headers.put(HEADER_SIGNATURE, signature);

        WebhookHttpSender.SendResult result = httpSender.post(targetUri, delivery.getPayloadJson(), headers);
        delivery.recordAttempt(
                result.success(),
                result.httpStatus(),
                result.durationMs(),
                result.responseSnippet(),
                result.errorMessage(),
                Instant.now()
        );
    }

    private WebhookEndpoint findEndpointOrThrow(UUID endpointId, UUID tenantId) {
        return endpointRepository.findByIdAndTenantId(endpointId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Webhook endpoint not found"));
    }

    private static List<WebhookEventType> parseSubscribableEvents(List<String> rawEvents) {
        if (rawEvents == null || rawEvents.isEmpty()) {
            throw new BusinessRuleException("Select at least one webhook event");
        }
        List<WebhookEventType> resolved = rawEvents.stream()
                .map(raw -> WebhookEventType.fromEventName(raw)
                        .filter(type -> !type.isSystemTestOnly())
                        .orElseThrow(() -> new BusinessRuleException("Unsupported webhook event: " + raw)))
                .distinct()
                .toList();
        if (resolved.isEmpty()) {
            throw new BusinessRuleException("Select at least one webhook event");
        }
        return resolved;
    }
}
