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
 * Curated defaults and safe JSON serialization for post-delivery appliance care tips.
 */
public final class CareTipsTemplateCodec {

    public static final int MAX_CATEGORIES = 60;
    public static final int MAX_TIPS_PER_CATEGORY = 12;
    public static final int MAX_CATEGORY_LENGTH = 80;
    public static final int MAX_TIP_LENGTH = 240;

    private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();
    private static final TypeReference<LinkedHashMap<String, List<String>>> MAP_TYPE = new TypeReference<>() {};

    private static final Map<String, List<String>> DEFAULT_TEMPLATES;

    static {
        Map<String, List<String>> defaults = new LinkedHashMap<>();
        defaults.put("Micro-ondas", List.of(
                "Ligue sempre direto na tomada de 20A — evite benjamins, adaptadores ou extensões.",
                "Nunca ligue o aparelho vazio e não use recipientes metálicos ou com borda dourada.",
                "Mantenha o interior e a placa mica (lateral interna) limpos de gordura para evitar faíscas.",
                "Espere o bip terminar ou aperte Pausar antes de abrir a porta para preservar as microchaves."
        ));
        defaults.put("Air Fryer", List.of(
                "Ligue direto na tomada (evite extensões e adaptadores) devido à alta potência da resistência.",
                "Deixe pelo menos 10 cm de espaço livre atrás da saída de ar quente durante o uso.",
                "Aguarde esfriar antes de lavar o cesto e evite palha de aço no antiaderente.",
                "Não ultrapasse o limite do cesto para evitar respingos de gordura na resistência superior."
        ));
        defaults.put("Liquidificador", List.of(
                "Não ultrapasse o nível máximo do copo e evite bater massas muito pesadas por mais de 1 minuto seguido.",
                "Encaixe o copo firmemente na base antes de ligar para não desgastar o arrastador.",
                "Nunca molhe a base do motor — limpe apenas com pano levemente úmido."
        ));
        defaults.put("Ventilador", List.of(
                "Limpe as grades e a hélice periodicamente para evitar acúmulo de poeira no eixo e superaquecimento.",
                "Se notar o giro pesado ou barulho incomum, desligue imediatamente para evitar a queima do fusível térmico.",
                "Evite forçar ou travar a oscilação manualmente enquanto o aparelho estiver ligado."
        ));
        defaults.put("Cafeteira", List.of(
                "Faça descalcificação periódica para evitar entupimentos nas tubulações internas.",
                "Nunca ligue sem água no reservatório e dê preferência à água filtrada.",
                "Seque bem a base da jarra antes de apoiar na chapa aquecedora."
        ));
        defaults.put("Aspirador", List.of(
                "Esvazie o coletor e limpe ou troque o filtro com frequência — filtro entupido superaquece o motor.",
                "Não aspire líquidos, cinzas quentes ou pó de obra/gesso em modelos comuns.",
                "Evite puxar o aparelho pelo cabo elétrico para não romper o fio interno."
        ));
        defaults.put("Ferro de Passar", List.of(
                "Ligue direto na tomada compatível com a voltagem do aparelho, sem adaptadores.",
                "Esvazie o reservatório de água após o uso e guarde sempre na vertical depois de esfriar.",
                "Use água filtrada para reduzir incrustações nos furos de vapor."
        ));
        defaults.put("Forno Elétrico", List.of(
                "Ligue sempre direto na tomada apropriada, sem extensões ou benjamins.",
                "Mantenha as laterais e a parte traseira desobstruídas para ventilação adequada.",
                "Limpe respingos de gordura após esfriar, sem jogar água nas resistências."
        ));
        defaults.put("Panela Elétrica", List.of(
                "Seque bem o fundo externo da cuba antes de encaixar na base aquecedora.",
                "Nunca molhe a base elétrica nem ligue a panela sem a cuba interna.",
                "Use apenas utensílios de silicone ou madeira para preservar o antiaderente."
        ));
        defaults.put("Bebedouro", List.of(
                "Mantenha a traseira afastada pelo menos 15 cm da parede para ventilar o sistema de refrigeração.",
                "Higienize o reservatório e o suporte do galão a cada 3 meses.",
                "Não deixe ligado sem água no reservatório."
        ));
        DEFAULT_TEMPLATES = Collections.unmodifiableMap(defaults);
    }

    private CareTipsTemplateCodec() {
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
                List<String> tips = entry.getValue() == null
                        ? List.of()
                        : entry.getValue().stream()
                        .filter(s -> s != null && !s.isBlank())
                        .map(String::trim)
                        .toList();
                cleaned.put(entry.getKey().trim(), tips);
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
            throw new BusinessRuleException("At most " + MAX_CATEGORIES + " care-tip categories are allowed");
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
                        "Care-tip category name must be at most " + MAX_CATEGORY_LENGTH + " characters"
                );
            }
            String lowerKey = category.toLowerCase();
            if (!seenCategoriesLower.add(lowerKey)) {
                throw new BusinessRuleException("Duplicate care-tip category: " + category);
            }

            List<String> rawTips = entry.getValue() == null ? List.of() : entry.getValue();
            List<String> cleanTips = new ArrayList<>();
            Set<String> seenTipsLower = new LinkedHashSet<>();

            for (String rawTip : rawTips) {
                if (rawTip == null || rawTip.trim().isEmpty()) {
                    continue;
                }
                String tip = rawTip.trim();
                if (tip.length() > MAX_TIP_LENGTH) {
                    throw new BusinessRuleException(
                            "Care tip must be at most " + MAX_TIP_LENGTH + " characters"
                    );
                }
                if (seenTipsLower.add(tip.toLowerCase())) {
                    cleanTips.add(tip);
                }
            }

            if (cleanTips.size() > MAX_TIPS_PER_CATEGORY) {
                throw new BusinessRuleException(
                        "At most " + MAX_TIPS_PER_CATEGORY + " care tips per category are allowed"
                );
            }

            normalized.put(category, cleanTips);
        }

        return JSON_MAPPER.writeValueAsString(normalized);
    }
}
