package io.github.s1m0n;


import com.fasterxml.jackson.databind.JsonNode;
import org.apache.avro.JsonProperties;
import org.apache.avro.LogicalType;
import org.apache.avro.Schema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public class AvroToJsonSchemaConverter {

    private static final Logger logger = LoggerFactory.getLogger(AvroToJsonSchemaConverter.class);

    static Map<String, Object> convert(Schema schema) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("$schema", "http://json-schema.org/draft-07/schema#");
        Map<String, Object> def = toJsonSchema(schema);
        root.putAll(def);
        return root;
    }

    private static Map<String, Object> toJsonSchema(Schema schema) {
        Map<String, Object> node = new LinkedHashMap<>();
        // Add description from Avro schema doc when present
        if (schema.getDoc() != null && !schema.getDoc().isEmpty()) {
            node.put("description", schema.getDoc());
        }
        switch (schema.getType()) {
            case NULL:
                node.put("type", "null");
                break;
            case BOOLEAN:
                node.put("type", "boolean");
                break;
            case INT:
                node.put("type", "integer");
                node.put("format", "int32");
                break;
            case LONG:
                node.put("type", "integer");
                if (isLogical(schema, "timestamp-millis")) {
                    node.put("type", "string");
                    node.put("format", "date-time");
                } else if (isLogical(schema, "date")) {
                    node.put("type", "string");
                    node.put("format", "date");
                }
                break;
            case FLOAT:
                node.put("type", "number");
                node.put("format", "float");
                break;
            case DOUBLE:
                node.put("type", "number");
                node.put("format", "double");
                break;
            case BYTES:
                node.put("type", "string");
                node.put("contentEncoding", "base64");
                break;
            case STRING:
                node.put("type", "string");
                break;
            case ENUM:
                node.put("type", "string");
                node.put("enum", schema.getEnumSymbols());
                break;
            case FIXED:
                node.put("type", "string");
                node.put("contentEncoding", "base64");
                break;
            case ARRAY:
                node.put("type", "array");
                node.put("items", toJsonSchema(schema.getElementType()));
                break;
            case MAP:
                node.put("type", "object");
                node.put("additionalProperties", toJsonSchema(schema.getValueType()));
                break;
            case RECORD:
                node.put("type", "object");
                Map<String, Object> props = new LinkedHashMap<>();
                List<String> required = new ArrayList<>();
                for (Schema.Field f : schema.getFields()) {
                    Schema fSchema = unwrapNullable(f.schema());
                    Map<String, Object> propSchema = toJsonSchema(fSchema);
                    // Add field description from Avro field doc when present
                    if (f.doc() != null && !f.doc().isEmpty()) {
                        propSchema.put("description", f.doc());
                    }
                    // Add default value if present in Avro field
                    if (f.hasDefaultValue()) {
                        Object defaultVal = getDefaultValue(f);
                        propSchema.put("default", defaultVal);
                    }
                    props.put(f.name(), propSchema);
                    if (!isNullable(f.schema())) {
                        required.add(f.name());
                    }
                }
                node.put("properties", props);
                if (!required.isEmpty()) node.put("required", required);
                break;
            case UNION:
                List<Schema> types = schema.getTypes();
                boolean nullable = types.stream().anyMatch(t -> t.getType() == Schema.Type.NULL);
                List<Map<String, Object>> anyOf = new ArrayList<>();
                for (Schema t : types) {
                    if (t.getType() == Schema.Type.NULL) continue;
                    anyOf.add(toJsonSchema(t));
                }
                if (nullable) {
                    anyOf.add(Collections.singletonMap("type", "null"));
                }
                if (anyOf.size() == 1) return anyOf.getFirst();
                node.put("anyOf", anyOf);
                break;
            default:
                node.put("type", "object");
        }
        return node;
    }

    private static Object getDefaultValue(Schema.Field f) {
        try {
            if (!f.hasDefaultValue()) {
                return null;
            }
            return convertDefaultValue(f.defaultVal());
        } catch (Exception e) {
            // In case of unexpected default structure, skip adding default
            logger.warn("Failed to convert default value for field '{}': {}", f.name(), e.getMessage());
            return null;
        }
    }

    private static Object convertDefaultValue(Object defaultValue) {
        if (defaultValue == null) return null;
        if (defaultValue == JsonProperties.NULL_VALUE) return null;
        if (defaultValue instanceof JsonNode jn) {
            return jsonNodeToJava(jn);
        }
        if (defaultValue instanceof CharSequence cs) return cs.toString();
        if (defaultValue instanceof Number || defaultValue instanceof Boolean) return defaultValue;
        if (defaultValue instanceof Map<?, ?> m) return m;
        if (defaultValue instanceof List<?> l) return l;
        // Fallback to string representation
        return defaultValue.toString();
    }

    private static Object jsonNodeToJava(JsonNode node) {
        if (node == null || node.isNull()) return null;
        if (node.isTextual()) return node.asText();
        if (node.isBoolean()) return node.asBoolean();
        if (node.isInt() || node.isLong()) return node.longValue();
        if (node.isFloat() || node.isDouble() || node.isBigDecimal()) return node.doubleValue();
        if (node.isBigInteger()) return node.bigIntegerValue();
        if (node.isArray()) {
            List<Object> list = new ArrayList<>(node.size());
            for (JsonNode el : node) list.add(jsonNodeToJava(el));
            return list;
        }
        if (node.isObject()) {
            Map<String, Object> map = new LinkedHashMap<>();
            var fields = node.fields();
            while (fields.hasNext()) {
                var entry = fields.next();
                map.put(entry.getKey(), jsonNodeToJava(entry.getValue()));
            }
            return map;
        }
        // numbers fallback
        if (node.isNumber()) return node.numberValue();
        return node.asText();
    }

    private static boolean isNullable(Schema schema) {
        if (schema.getType() == Schema.Type.UNION) {
            for (Schema t : schema.getTypes()) if (t.getType() == Schema.Type.NULL) return true;
        }
        return false;
    }

    private static Schema unwrapNullable(Schema schema) {
        if (schema.getType() == Schema.Type.UNION) {
            for (Schema t : schema.getTypes()) if (t.getType() != Schema.Type.NULL) return t;
        }
        return schema;
    }

    private static boolean isLogical(Schema schema, String logical) {
        LogicalType lt = schema.getLogicalType();
        return lt != null && logical.equals(lt.getName());
    }
}
