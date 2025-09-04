package io.github.s1m0n;


import org.apache.avro.LogicalType;
import org.apache.avro.Schema;

import java.util.*;

public class AvroToJsonSchemaConverter {
    static Map<String, Object> convert(Schema schema) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("$schema", "http://json-schema.org/draft-07/schema#");
        Map<String, Object> def = toJsonSchema(schema, new HashMap<>());
        root.putAll(def);
        return root;
    }

    private static Map<String, Object> toJsonSchema(Schema schema, Map<String, Map<String, Object>> defs) {
        Map<String, Object> node = new LinkedHashMap<>();
        switch (schema.getType()) {
            case NULL: node.put("type", "null"); break;
            case BOOLEAN: node.put("type", "boolean"); break;
            case INT: node.put("type", "integer"); node.put("format", "int32"); break;
            case LONG:
                node.put("type", "integer");
                if (isLogical(schema, "timestamp-millis")) { node.put("type", "string"); node.put("format", "date-time"); }
                else if (isLogical(schema, "date")) { node.put("type", "string"); node.put("format", "date"); }
                break;
            case FLOAT: node.put("type", "number"); node.put("format", "float"); break;
            case DOUBLE: node.put("type", "number"); node.put("format", "double"); break;
            case BYTES:
                node.put("type", "string"); node.put("contentEncoding", "base64");
                break;
            case STRING: node.put("type", "string"); break;
            case ENUM:
                node.put("type", "string");
                node.put("enum", schema.getEnumSymbols());
                break;
            case FIXED:
                node.put("type", "string"); node.put("contentEncoding", "base64");
                break;
            case ARRAY:
                node.put("type", "array");
                node.put("items", toJsonSchema(schema.getElementType(), defs));
                break;
            case MAP:
                node.put("type", "object");
                node.put("additionalProperties", toJsonSchema(schema.getValueType(), defs));
                break;
            case RECORD:
                node.put("type", "object");
                Map<String, Object> props = new LinkedHashMap<>();
                List<String> required = new ArrayList<>();
                for (Schema.Field f : schema.getFields()) {
                    Schema fSchema = unwrapNullable(f.schema());
                    props.put(f.name(), toJsonSchema(fSchema, defs));
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
                    anyOf.add(toJsonSchema(t, defs));
                }
                if (nullable) {
                    anyOf.add(Collections.singletonMap("type", "null"));
                }
                if (anyOf.size() == 1) return anyOf.get(0);
                node.put("anyOf", anyOf);
                break;
            default:
                node.put("type", "object");
        }
        return node;
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
