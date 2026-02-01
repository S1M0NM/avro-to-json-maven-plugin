package io.github.s1m0n;

/*
 * Copyright 2025 Simon Marksteiner
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import org.apache.avro.JsonProperties;
import org.apache.avro.LogicalType;
import org.apache.avro.Schema;
import org.apache.maven.plugin.logging.Log;
import tools.jackson.databind.JsonNode;

import java.util.*;

public class AvroToJsonSchemaConverter {

    private static Log LOG;

    static void setLog(Log log) {
        LOG = log;
    }

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
                if (isLogical(schema, "date")) {
                    node.put("type", "string");
                    node.put("format", "date");
                } else if (isLogical(schema, "time-millis")) {
                    node.put("type", "string");
                    node.put("format", "time");
                } else {
                    node.put("type", "integer");
                    node.put("format", "int32");
                }
                break;
            case LONG:
                if (isLogical(schema, "timestamp-millis") || isLogical(schema, "timestamp-micros")
                        || isLogical(schema, "local-timestamp-millis") || isLogical(schema, "local-timestamp-micros")) {
                    node.put("type", "string");
                    node.put("format", "date-time");
                } else if (isLogical(schema, "time-micros")) {
                    node.put("type", "string");
                    node.put("format", "time");
                } else {
                    node.put("type", "integer");
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
                if (isLogical(schema, "decimal")) {
                    convertDecimal(schema, node);
                } else {
                    node.put("type", "string");
                    node.put("contentEncoding", "base64");
                }
                break;
            case STRING:
                if (isLogical(schema, "uuid")) {
                    node.put("type", "string");
                    node.put("format", "uuid");
                } else {
                    node.put("type", "string");
                }
                break;
            case ENUM:
                node.put("type", "string");
                node.put("enum", schema.getEnumSymbols());
                break;
            case FIXED:
                if (isLogical(schema, "decimal")) {
                    convertDecimal(schema, node);
                } else if (isLogical(schema, "duration")) {
                    node.put("type", "string");
                    node.put("format", "duration");
                } else {
                    node.put("type", "string");
                    node.put("contentEncoding", "base64");
                }
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
                    // Do not unwrap nullable; pass full schema so JSON Schema includes null when applicable
                    Map<String, Object> propSchema = toJsonSchema(f.schema());
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
                List<Schema> nonNullTypes = new ArrayList<>();
                for (Schema t : types) if (t.getType() != Schema.Type.NULL) nonNullTypes.add(t);

                // If this is a simple nullable union with a single non-null type, prefer JSON Schema "type" array
                if (nullable && nonNullTypes.size() == 1) {
                    Map<String, Object> base = toJsonSchema(nonNullTypes.getFirst());
                    Object baseType = base.get("type");
                    if (baseType instanceof String s) {
                        node.putAll(base);
                        node.put("type", Arrays.asList(s, "null"));
                        return node;
                    }
                    // If base type is not a simple type string, fall back to anyOf representation below
                }

                List<Map<String, Object>> anyOf = new ArrayList<>();
                for (Schema t : types) {
                    if (t.getType() == Schema.Type.NULL) continue;
                    anyOf.add(toJsonSchema(t));
                }
                if (nullable) {
                    anyOf.add(Collections.singletonMap("type", "null"));
                }
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
            if (LOG != null) {
                LOG.warn("Failed to convert default value for field '" + f.name() + "': " + e.getMessage());
            }
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
        if (node.isString()) return node.asString();
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
            for (Map.Entry<String, JsonNode> entry : node.properties()) {
                map.put(entry.getKey(), jsonNodeToJava(entry.getValue()));
            }
            return map;
        }
        // numbers fallback
        if (node.isNumber()) return node.numberValue();
        return node.asString();
    }

    private static boolean isNullable(Schema schema) {
        if (schema.getType() == Schema.Type.UNION) {
            for (Schema t : schema.getTypes()) if (t.getType() == Schema.Type.NULL) return true;
        }
        return false;
    }

    private static boolean isLogical(Schema schema, String logical) {
        // as of Avro 1.11.4, decimal is a logical type, but not fully implemented as a logical type
        Object logicalType = schema.getObjectProp("logicalType");
        if (schema.getType() == Schema.Type.FIXED && logicalType != null && logicalType.equals(logical))
            return true;

        LogicalType lt = schema.getLogicalType();
        return lt != null && logical.equals(lt.getName());
    }

    private static void convertDecimal(Schema schema, Map<String, Object> node) {
        node.put("type", "string");
        try {
            String precision = schema.getObjectProp("precision") != null ? schema.getObjectProp("precision").toString() : null;
            String scale = schema.getObjectProp("scale") != null ? schema.getObjectProp("scale").toString() : null;
            node.put("x-avro-logicalType", "decimal");
            if (precision != null) node.put("x-precision", Integer.parseInt(precision));
            if (scale != null) node.put("x-scale", Integer.parseInt(scale));
        } catch (Exception ignored) {
            node.put("x-avro-logicalType", "decimal");
        }
    }
}
