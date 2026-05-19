package com.avalon.dnd.shared;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Shared JSON helpers for opaque editor/runtime payloads.
 *
 * The project intentionally carries some map-editor metadata through the
 * shared/server boundary without depending on the editor model classes.
 * JsonNode gives us a typed transport shape while still remaining flexible.
 */
public final class JsonPayloads {

    private static final ObjectMapper MAPPER = new ObjectMapper().findAndRegisterModules();

    private JsonPayloads() {
    }

    public static JsonNode toNode(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof JsonNode node) {
            return node;
        }
        return MAPPER.valueToTree(value);
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> toMap(JsonNode value) {
        if (value == null || value.isNull() || value.isMissingNode()) {
            return Collections.emptyMap();
        }
        if (value.isObject()) {
            JavaType type = MAPPER.getTypeFactory().constructMapType(LinkedHashMap.class, String.class, Object.class);
            return MAPPER.convertValue(value, type);
        }
        return Collections.emptyMap();
    }

    @SuppressWarnings("unchecked")
    public static List<Object> toList(JsonNode value) {
        if (value == null || value.isNull() || value.isMissingNode()) {
            return Collections.emptyList();
        }
        if (value.isArray()) {
            JavaType type = MAPPER.getTypeFactory().constructCollectionType(List.class, Object.class);
            return MAPPER.convertValue(value, type);
        }
        return Collections.emptyList();
    }
}
