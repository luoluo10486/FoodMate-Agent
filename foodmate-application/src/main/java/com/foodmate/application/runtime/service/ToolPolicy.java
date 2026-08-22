package com.foodmate.application.runtime.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.foodmate.shared.error.ErrorCode;

/** 统一工具策略结果；策略层只做授权判断，不执行业务副作用。 */
public final class ToolPolicy {
    private ToolPolicy() {}

    public static String validateInput(ToolRegistryService.ToolView tool, JsonNode input) {
        if (input == null) return ErrorCode.TOOL_INPUT_INVALID.code();
        JsonNode schema = tool.inputSchema();
        String typeError = validateType(schema.path("type"), input);
        if (typeError != null) return typeError;
        JsonNode properties = schema.path("properties");
        if (input.isObject() && properties.isObject()) {
            if (!schema.path("additionalProperties").asBoolean(true)) {
                var fields = input.fieldNames();
                while (fields.hasNext())
                    if (!properties.has(fields.next()))
                        return ErrorCode.TOOL_SCHEMA_UNSUPPORTED.code();
            }
            var required = schema.path("required");
            if (required.isArray())
                for (JsonNode name : required)
                    if (!input.has(name.asText())) return ErrorCode.TOOL_INPUT_INVALID.code();
            var fields = input.fields();
            while (fields.hasNext()) {
                var entry = fields.next();
                JsonNode property = properties.path(entry.getKey());
                String propertyError = validateProperty(property, entry.getValue());
                if (propertyError != null) return propertyError;
            }
        }
        return null;
    }

    public static boolean requiresConfirmation(ToolRegistryService.ToolView tool) {
        return "required".equalsIgnoreCase(tool.permissions().path("approval").asText())
                || "high".equalsIgnoreCase(tool.riskLevel());
    }

    private static String validateProperty(JsonNode schema, JsonNode value) {
        String typeError = validateType(schema.path("type"), value);
        if (typeError != null) return typeError;
        if (value.isTextual()) {
            int length = value.textValue().length();
            if (schema.has("minLength") && length < schema.path("minLength").asInt())
                return ErrorCode.TOOL_INPUT_INVALID.code();
            if (schema.has("maxLength") && length > schema.path("maxLength").asInt())
                return ErrorCode.TOOL_INPUT_INVALID.code();
        }
        if (value.isArray()
                && schema.has("maxItems")
                && value.size() > schema.path("maxItems").asInt())
            return ErrorCode.TOOL_INPUT_INVALID.code();
        if (value.isNumber()
                && schema.has("minimum")
                && value.decimalValue().compareTo(schema.path("minimum").decimalValue()) < 0)
            return ErrorCode.TOOL_INPUT_INVALID.code();
        if (schema.path("enum").isArray()) {
            boolean matches = false;
            for (JsonNode candidate : schema.path("enum"))
                if (candidate.equals(value)) matches = true;
            if (!matches) return ErrorCode.TOOL_INPUT_INVALID.code();
        }
        return null;
    }

    private static String validateType(JsonNode type, JsonNode value) {
        if (!type.isTextual()) return null;
        boolean valid =
                switch (type.asText()) {
                    case "object" -> value.isObject();
                    case "array" -> value.isArray();
                    case "string" -> value.isTextual();
                    case "integer" -> value.isIntegralNumber();
                    case "number" -> value.isNumber();
                    case "boolean" -> value.isBoolean();
                    default -> false;
                };
        return valid ? null : ErrorCode.TOOL_INPUT_INVALID.code();
    }
}
