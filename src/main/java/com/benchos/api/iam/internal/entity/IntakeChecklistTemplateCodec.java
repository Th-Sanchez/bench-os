package com.benchos.api.iam.internal.entity;

import com.benchos.api.shared.exception.BusinessRuleException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Curated defaults and safe JSON serialization for per-category intake checklists.
 */
public final class IntakeChecklistTemplateCodec {

    public static final int MAX_CATEGORIES = 60;
    public static final int MAX_ITEMS_PER_CATEGORY = 20;
    public static final int MAX_CATEGORY_LENGTH = 80;
    public static final int MAX_ITEM_LENGTH = 160;

    private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();
    private static final TypeReference<LinkedHashMap<String, List<String>>> MAP_TYPE = new TypeReference<>() {};

    private static final Map<String, List<String>> DEFAULT_TEMPLATES;

    static {
        Map<String, List<String>> defaults = new LinkedHashMap<>();
        defaults.put("Micro-ondas", List.of(
                "Prato giratório devolvido ao cliente",
                "Aro/cruzeta retirado",
                "Liga na tomada",
                "Sem amassados ou vidro trincado"
        ));
        defaults.put("Air Fryer", List.of(
                "Cesto e grelha presentes",
                "Liga na tomada",
                "Carcaça sem rachaduras"
        ));
        defaults.put("Liquidificador", List.of(
                "Copo e tampa entregues",
                "Lâmina/base intactas",
                "Liga na tomada"
        ));
        defaults.put("Ventilador", List.of(
                "Grade e hélice intactas",
                "Cabo sem emendas",
                "Liga na tomada"
        ));
        defaults.put("Cafeteira", List.of(
                "Jarra/reservatório entregues",
                "Porta-filtro presente",
                "Liga na tomada"
        ));
        defaults.put("Aspirador", List.of(
                "Mangueira e acessórios entregues",
                "Filtro presente",
                "Liga na tomada"
        ));
        defaults.put("Forno Elétrico", List.of(
                "Grade/bandeja retiradas",
                "Vidro da porta intacto",
                "Liga na tomada"
        ));
        defaults.put("Panela Elétrica", List.of(
                "Cuba interna e tampa entregues",
                "Cabo de força entregue",
                "Liga na tomada"
        ));
        DEFAULT_TEMPLATES = Collections.unmodifiableMap(defaults);
    }

    private IntakeChecklistTemplateCodec() {
    }

    public static Map<String, List<String>> defaultTemplates() {
        Map<String, List<String>> copy = new LinkedHashMap<>();
        DEFAULT_TEMPLATES.forEach((k, v) -> copy.put(k, List.copyOf(v)));
        return Collections.unmodifiableMap(copy);
    }

    public static Map<String, List<String>> decodeOrDefault(String rawJson) {
        if (rawJson == null || rawJson.isBlank()) {
            return defaultTemplates();
        }
        try {
            LinkedHashMap<String, List<String>> parsed = JSON_MAPPER.readValue(rawJson, MAP_TYPE);
            if (parsed == null) {
                return defaultTemplates();
            }
            Map<String, List<String>> cleaned = new LinkedHashMap<>();
            for (Map.Entry<String, List<String>> entry : parsed.entrySet()) {
                if (entry.getKey() == null || entry.getKey().isBlank()) {
                    continue;
                }
                List<String> items = entry.getValue() == null
                        ? List.of()
                        : entry.getValue().stream()
                        .filter(s -> s != null && !s.isBlank())
                        .map(String::trim)
                        .toList();
                cleaned.put(entry.getKey().trim(), items);
            }
            return Collections.unmodifiableMap(cleaned);
        } catch (RuntimeException ex) {
            return defaultTemplates();
        }
    }

    public static String encodeAndValidate(Map<String, List<String>> rawTemplates) {
        if (rawTemplates == null) {
            return null;
        }
        if (rawTemplates.size() > MAX_CATEGORIES) {
            throw new BusinessRuleException("At most " + MAX_CATEGORIES + " checklist categories are allowed");
        }

        Map<String, List<String>> normalized = new LinkedHashMap<>();
        Set<String> seenCategoriesLower = new LinkedHashSet<>();

        for (Map.Entry<String, List<String>> entry : rawTemplates.entrySet()) {
            String rawKey = entry.getKey();
            if (rawKey == null || rawKey.trim().isEmpty()) {
                continue;
            }
            String category = rawKey.trim();
            if (category.length() > MAX_CATEGORY_LENGTH) {
                throw new BusinessRuleException(
                        "Checklist category name must be at most " + MAX_CATEGORY_LENGTH + " characters"
                );
            }
            String lowerKey = category.toLowerCase();
            if (!seenCategoriesLower.add(lowerKey)) {
                throw new BusinessRuleException("Duplicate checklist category: " + category);
            }

            List<String> rawItems = entry.getValue() == null ? List.of() : entry.getValue();
            List<String> cleanItems = new ArrayList<>();
            Set<String> seenItemsLower = new LinkedHashSet<>();

            for (String rawItem : rawItems) {
                if (rawItem == null || rawItem.trim().isEmpty()) {
                    continue;
                }
                String item = rawItem.trim();
                if (item.length() > MAX_ITEM_LENGTH) {
                    throw new BusinessRuleException(
                            "Checklist item must be at most " + MAX_ITEM_LENGTH + " characters"
                    );
                }
                if (seenItemsLower.add(item.toLowerCase())) {
                    cleanItems.add(item);
                }
            }

            if (cleanItems.size() > MAX_ITEMS_PER_CATEGORY) {
                throw new BusinessRuleException(
                        "At most " + MAX_ITEMS_PER_CATEGORY + " checklist items per category are allowed"
                );
            }

            normalized.put(category, cleanItems);
        }

        return JSON_MAPPER.writeValueAsString(normalized);
    }
}
