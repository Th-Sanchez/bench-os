package com.benchos.api.integration.internal.service;

import com.benchos.api.integration.TenantModuleQuery;
import com.benchos.api.integration.TenantModuleSnapshot;
import com.benchos.api.integration.internal.entity.TenantModule;
import com.benchos.api.integration.internal.repository.TenantModuleRepository;
import com.benchos.api.integration.internal.web.dto.TenantModuleResponse;
import com.benchos.api.integration.internal.web.dto.UpsertTenantModuleRequest;
import com.benchos.api.shared.exception.BusinessRuleException;
import com.benchos.api.shared.tenant.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class TenantModuleService implements TenantModuleQuery {

    private static final Pattern MODULE_KEY_PATTERN = Pattern.compile("^[a-z0-9][a-z0-9_-]{2,63}$");
    private static final int MAX_CONFIG_BYTES = 16_384;

    public record BuiltInModuleDefinition(
            String moduleKey,
            String name,
            String description,
            boolean defaultEnabled
    ) {
    }

    public static final List<BuiltInModuleDefinition> BUILT_IN_CATALOG = List.of(
            new BuiltInModuleDefinition(
                    "outbound_webhooks",
                    "Webhooks & Automações",
                    "Dispara eventos assinados (HMAC-SHA256) para n8n, Make, Zapier ou scripts externos.",
                    true
            ),
            new BuiltInModuleDefinition(
                    "counter_tv_board",
                    "Painel TV de Balcão",
                    "Habilita configuração de painel de avisos / fila de retiradas para TV ou tela de balcão.",
                    false
            ),
            new BuiltInModuleDefinition(
                    "shop_mini_site",
                    "Mini-site da Oficina",
                    "Habilita configurações extras para página pública dedicada da assistência técnica.",
                    false
            ),
            new BuiltInModuleDefinition(
                    "whatsapp_cloud_api",
                    "WhatsApp Cloud API (Add-on)",
                    "Reserva de configuração para disparos automáticos via API oficial da Meta.",
                    false
            )
    );

    private static final Map<String, BuiltInModuleDefinition> CATALOG_BY_KEY = buildCatalogMap();

    private final TenantModuleRepository repository;
    private final ObjectMapper objectMapper;

    public TenantModuleService(TenantModuleRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isEnabled(UUID tenantId, String moduleKey) {
        String normalized = normalizeKey(moduleKey);
        return repository.findByTenantIdAndModuleKey(tenantId, normalized)
                .map(TenantModule::isEnabled)
                .orElseGet(() -> {
                    BuiltInModuleDefinition def = CATALOG_BY_KEY.get(normalized);
                    return def != null && def.defaultEnabled();
                });
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<TenantModuleSnapshot> findModule(UUID tenantId, String moduleKey) {
        String normalized = normalizeKey(moduleKey);
        return repository.findByTenantIdAndModuleKey(tenantId, normalized)
                .map(TenantModuleService::toSnapshot);
    }

    @Override
    @Transactional(readOnly = true)
    public List<TenantModuleSnapshot> listEnabledModules(UUID tenantId) {
        return repository.findAllByTenantIdAndEnabledTrueOrderByModuleKeyAsc(tenantId).stream()
                .map(TenantModuleService::toSnapshot)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<TenantModuleResponse> listForCurrentTenant() {
        UUID tenantId = TenantContext.requireCurrentTenant();
        Map<String, TenantModule> persisted = new LinkedHashMap<>();
        for (TenantModule mod : repository.findAllByTenantIdOrderByModuleKeyAsc(tenantId)) {
            persisted.put(mod.getModuleKey(), mod);
        }

        List<TenantModuleResponse> result = new ArrayList<>();
        for (BuiltInModuleDefinition def : BUILT_IN_CATALOG) {
            TenantModule existing = persisted.remove(def.moduleKey());
            if (existing != null) {
                result.add(new TenantModuleResponse(
                        existing.getModuleKey(),
                        def.name(),
                        def.description(),
                        true,
                        existing.isEnabled(),
                        parseConfigMap(existing.getConfigJson()),
                        existing.getUpdatedAt()
                ));
            } else {
                result.add(new TenantModuleResponse(
                        def.moduleKey(),
                        def.name(),
                        def.description(),
                        true,
                        def.defaultEnabled(),
                        Map.of(),
                        null
                ));
            }
        }

        for (TenantModule custom : persisted.values()) {
            result.add(new TenantModuleResponse(
                    custom.getModuleKey(),
                    custom.getModuleKey(),
                    "Módulo personalizado da oficina",
                    false,
                    custom.isEnabled(),
                    parseConfigMap(custom.getConfigJson()),
                    custom.getUpdatedAt()
            ));
        }

        return result;
    }

    @Transactional
    public TenantModuleResponse upsertForTenant(UUID tenantId, String rawModuleKey, UpsertTenantModuleRequest request) {
        String key = validateKey(rawModuleKey);
        String serializedConfig = serializeAndValidateConfig(request.config());
        Instant now = Instant.now();

        TenantModule module = repository.findByTenantIdAndModuleKey(tenantId, key)
                .map(existing -> {
                    existing.update(request.enabled(), serializedConfig, now);
                    return existing;
                })
                .orElseGet(() -> new TenantModule(tenantId, key, request.enabled(), serializedConfig, now));

        TenantModule saved = repository.saveAndFlush(module);
        BuiltInModuleDefinition def = CATALOG_BY_KEY.get(key);
        return new TenantModuleResponse(
                saved.getModuleKey(),
                def != null ? def.name() : saved.getModuleKey(),
                def != null ? def.description() : "Módulo personalizado da oficina",
                def != null,
                saved.isEnabled(),
                parseConfigMap(saved.getConfigJson()),
                saved.getUpdatedAt()
        );
    }

    private String serializeAndValidateConfig(Map<String, Object> config) {
        if (config == null || config.isEmpty()) {
            return "{}";
        }
        try {
            String json = objectMapper.writeValueAsString(config);
            if (json.length() > MAX_CONFIG_BYTES) {
                throw new BusinessRuleException("Module config exceeds maximum allowed size (16KB)");
            }
            return json;
        } catch (BusinessRuleException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessRuleException("Invalid module configuration JSON");
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseConfigMap(String configJson) {
        if (configJson == null || configJson.isBlank()) {
            return Map.of();
        }
        try {
            JsonNode node = objectMapper.readTree(configJson);
            if (node == null || !node.isObject()) {
                return Map.of();
            }
            return objectMapper.convertValue(node, Map.class);
        } catch (Exception ex) {
            return Map.of();
        }
    }

    private static String normalizeKey(String raw) {
        return raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
    }

    private static String validateKey(String raw) {
        String normalized = normalizeKey(raw);
        if (!MODULE_KEY_PATTERN.matcher(normalized).matches()) {
            throw new BusinessRuleException(
                    "Invalid moduleKey: use 3 to 64 lowercase letters, digits, underscores, or hyphens"
            );
        }
        return normalized;
    }

    private static TenantModuleSnapshot toSnapshot(TenantModule module) {
        return new TenantModuleSnapshot(
                module.getTenantId(),
                module.getModuleKey(),
                module.isEnabled(),
                module.getConfigJson(),
                module.getUpdatedAt()
        );
    }

    private static Map<String, BuiltInModuleDefinition> buildCatalogMap() {
        Map<String, BuiltInModuleDefinition> map = new LinkedHashMap<>();
        for (BuiltInModuleDefinition def : BUILT_IN_CATALOG) {
            map.put(def.moduleKey(), def);
        }
        return Map.copyOf(map);
    }
}
