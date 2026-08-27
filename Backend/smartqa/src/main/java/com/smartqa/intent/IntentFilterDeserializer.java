package com.smartqa.intent;

import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.node.ObjectNode;

/**
 * Accepts object, null, or blank/"null" strings without crashing the pipeline.
 */
public class IntentFilterDeserializer extends ValueDeserializer<IntentFilter> {

    @Override
    public IntentFilter deserialize(JsonParser parser, DeserializationContext context) {
        JsonNode node = context.readTree(parser);
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isTextual()) {
            String text = node.asText("");
            if (text == null || text.isBlank()
                    || "null".equalsIgnoreCase(text.trim())
                    || "undefined".equalsIgnoreCase(text.trim())
                    || "none".equalsIgnoreCase(text.trim())) {
                return null;
            }
            return parseLoose(text);
        }
        if (!node.isObject()) {
            return null;
        }
        ObjectNode object = (ObjectNode) node;
        String field = textOrNull(object, "field");
        String operator = textOrNull(object, "operator");
        String value = textOrNull(object, "value");
        Double min = numberOrNull(object, "min");
        Double max = numberOrNull(object, "max");
        if (field == null && operator == null && value == null && min == null && max == null) {
            return null;
        }
        return new IntentFilter(field, operator, value, min, max);
    }

    private static IntentFilter parseLoose(String text) {
        return FilterIntentParser.parse(text);
    }

    private static String textOrNull(ObjectNode object, String name) {
        JsonNode value = object.get(name);
        if (value == null || value.isNull()) {
            return null;
        }
        if (!value.isTextual() && !value.isNumber() && !value.isBoolean()) {
            return null;
        }
        String text = value.asText("").trim();
        if (text.isEmpty() || "null".equalsIgnoreCase(text) || "undefined".equalsIgnoreCase(text)) {
            return null;
        }
        return text;
    }

    private static Double numberOrNull(ObjectNode object, String name) {
        JsonNode value = object.get(name);
        if (value == null || value.isNull()) {
            return null;
        }
        if (value.isNumber()) {
            return value.asDouble();
        }
        if (value.isTextual()) {
            String text = value.asText("").trim();
            if (text.isEmpty() || "null".equalsIgnoreCase(text)) {
                return null;
            }
            try {
                return Double.parseDouble(text);
            } catch (NumberFormatException ex) {
                return null;
            }
        }
        return null;
    }
}
