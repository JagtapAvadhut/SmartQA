package com.smartqa.intent;

import com.smartqa.debug.TraceLogger;
import com.smartqa.debug.TraceMeta;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Layer 2 ID safety: LLM-generated IDs are not authoritative.
 * Duplicate / blank scenario and step IDs are repaired before validation binds the contract.
 */
public final class IntentIdUniquifier {

    private IntentIdUniquifier() {
    }

    public static void uniquify(ObjectNode root) {
        if (root == null) {
            return;
        }
        JsonNode scenariosNode = root.get("scenarios");
        if (!(scenariosNode instanceof ArrayNode scenarios)) {
            return;
        }
        Set<String> scenarioIds = new HashSet<>();
        Set<String> stepIds = new HashSet<>();
        int repairs = 0;
        for (int s = 0; s < scenarios.size(); s++) {
            if (!(scenarios.get(s) instanceof ObjectNode scenario)) {
                continue;
            }
            String scenarioId = text(scenario, "id");
            String uniqueScenario = uniqueId(scenarioId, "s" + (s + 1), "s", s + 1, scenarioIds);
            if (!uniqueScenario.equals(scenarioId)) {
                scenario.put("id", uniqueScenario);
                repairs++;
            }
            JsonNode stepsNode = scenario.get("steps");
            if (!(stepsNode instanceof ArrayNode steps)) {
                continue;
            }
            for (int i = 0; i < steps.size(); i++) {
                if (!(steps.get(i) instanceof ObjectNode step)) {
                    continue;
                }
                step.put("scenarioId", uniqueScenario);
                String original = text(step, "id");
                String unique = uniqueId(original, uniqueScenario, uniqueScenario, i + 1, stepIds);
                if (!unique.equals(original)) {
                    step.put("id", unique);
                    repairs++;
                }
            }
        }
        if (repairs > 0) {
            TraceLogger.info("INTENT", "INTENT_STEP_IDS_REPAIRED", "Repaired duplicate or blank intent IDs",
                    TraceMeta.of("repairs", repairs, "scenarios", scenarios.size()));
        }
    }

    public static IntentContract uniquify(IntentContract contract) {
        if (contract == null || contract.scenarios() == null) {
            return contract;
        }
        Set<String> scenarioIds = new HashSet<>();
        Set<String> stepIds = new HashSet<>();
        List<IntentScenario> scenarios = new ArrayList<>();
        int repairs = 0;
        int scenarioIndex = 0;
        for (IntentScenario scenario : contract.scenarios()) {
            scenarioIndex++;
            if (scenario == null) {
                continue;
            }
            String scenarioId = uniqueId(scenario.id(), "s" + scenarioIndex, "s", scenarioIndex, scenarioIds);
            if (scenario.id() == null || !scenarioId.equals(scenario.id())) {
                repairs++;
            }
            List<IntentStep> steps = new ArrayList<>();
            int stepIndex = 0;
            if (scenario.steps() != null) {
                for (IntentStep step : scenario.steps()) {
                    stepIndex++;
                    if (step == null) {
                        continue;
                    }
                    String unique = uniqueId(step.id(), scenarioId, scenarioId, stepIndex, stepIds);
                    IntentStep next = step.withId(unique).withScenarioId(scenarioId);
                    if (step.id() == null || !unique.equals(step.id()) || scenarioIdNotEqual(step.scenarioId(), scenarioId)) {
                        repairs++;
                    }
                    steps.add(next);
                }
            }
            scenarios.add(new IntentScenario(scenarioId, scenario.name(), steps));
        }
        if (repairs > 0) {
            TraceLogger.info("INTENT", "INTENT_STEP_IDS_REPAIRED", "Repaired duplicate or blank intent IDs",
                    TraceMeta.of("repairs", repairs, "scenarios", scenarios.size()));
        }
        return new IntentContract(
                contract.status(),
                contract.testName(),
                contract.confidence(),
                scenarios,
                contract.clarifications() == null ? List.of() : contract.clarifications()
        );
    }

    static String uniqueId(String candidate, String fallback, Set<String> used) {
        return uniqueId(candidate, fallback, "s", 1, used);
    }

    static String uniqueId(String candidate, String fallback, String scenarioId, int stepIndex, Set<String> used) {
        String base = sanitize(candidate);
        if (base.isBlank()) {
            base = stableId(scenarioId, stepIndex, fallback);
        }
        if (base.isBlank()) {
            base = "step";
        }
        String id = base;
        int suffix = 0;
        while (!used.add(id.toLowerCase(Locale.ROOT))) {
            suffix++;
            if (suffix == 1 && !sanitize(candidate).isBlank()) {
                id = base + "_repaired";
            } else {
                id = stableId(scenarioId, stepIndex, fallback + "|" + suffix + "|" + candidate);
            }
        }
        return id;
    }

    static String stableId(String scenarioId, int stepIndex, String seed) {
        String sc = sanitize(scenarioId);
        if (sc.isBlank()) {
            sc = "s";
        }
        String material = sc + "|" + stepIndex + "|" + (seed == null ? "" : seed);
        return sc + "_s" + Math.max(1, stepIndex) + "_" + hash8(material);
    }

    private static String hash8(String material) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(material.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest).substring(0, 8);
        } catch (NoSuchAlgorithmException ex) {
            return Integer.toHexString(Math.abs(material.hashCode()));
        }
    }

    private static boolean scenarioIdNotEqual(String current, String expected) {
        if (expected == null || expected.isBlank()) {
            return false;
        }
        return current == null || !expected.equals(current);
    }

    private static String sanitize(String value) {
        if (value == null) {
            return "";
        }
        return value.trim();
    }

    private static String text(ObjectNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull() || !value.isString()) {
            if (value != null && !value.isNull()) {
                return value.asText("").trim();
            }
            return "";
        }
        return value.asText("").trim();
    }
}
