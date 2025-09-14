package io.github.s1m0n;

import org.apache.avro.LogicalTypes;
import org.apache.avro.Schema;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class AvroToJsonSchemaConverterTest {

    private Schema parse(String avsc) {
        return new Schema.Parser().parse(avsc);
    }

    @Test
    void testPrimitiveTypes() {
        // string
        Schema s = Schema.create(Schema.Type.STRING);
        Map<String, Object> js = AvroToJsonSchemaConverter.convert(s);
        assertEquals("http://json-schema.org/draft-07/schema#", js.get("$schema"));
        assertEquals("string", js.get("type"));

        // int
        js = AvroToJsonSchemaConverter.convert(Schema.create(Schema.Type.INT));
        assertEquals("integer", js.get("type"));
        assertEquals("int32", js.get("format"));

        // boolean
        js = AvroToJsonSchemaConverter.convert(Schema.create(Schema.Type.BOOLEAN));
        assertEquals("boolean", js.get("type"));

        // bytes
        js = AvroToJsonSchemaConverter.convert(Schema.create(Schema.Type.BYTES));
        assertEquals("string", js.get("type"));
        assertEquals("base64", js.get("contentEncoding"));
    }

    @Test
    void testRecordRequiredOptionalAndDocsAndDefaults() {
        String avsc = "{\n" +
                "  \"type\": \"record\",\n" +
                "  \"name\": \"User\",\n" +
                "  \"doc\": \"User record\",\n" +
                "  \"fields\": [\n" +
                "    { \"name\": \"id\", \"type\": \"string\" },\n" +
                "    { \"name\": \"age\", \"type\": [\"null\", \"int\"], \"default\": null, \"doc\": \"Age in years\" }\n" +
                "  ]\n" +
                "}";
        Schema schema = parse(avsc);
        Map<String, Object> root = AvroToJsonSchemaConverter.convert(schema);
        assertEquals("object", root.get("type"));
        assertEquals("User record", root.get("description"));
        Map<String, Object> props = (Map<String, Object>) root.get("properties");
        assertTrue(props.containsKey("id"));
        assertTrue(props.containsKey("age"));
        // required should include id only
        List<String> required = (List<String>) root.get("required");
        assertNotNull(required);
        assertEquals(List.of("id"), required);
        Map<String, Object> ageSchema = (Map<String, Object>) props.get("age");
        assertEquals(List.of("integer", "null"), ageSchema.get("type"));
        assertEquals("Age in years", ageSchema.get("description"));
        assertNull(ageSchema.get("default"));
    }

    @Test
    void testNullableUnionAnyOfForComplexUnion() {
        String avsc = "{\n" +
                "  \"type\": \"record\",\n" +
                "  \"name\": \"R\",\n" +
                "  \"fields\": [\n" +
                "    { \"name\": \"v\", \"type\": [\"null\", \"string\", \"int\"] }\n" +
                "  ]\n" +
                "}";
        Schema schema = parse(avsc);
        Map<String, Object> root = AvroToJsonSchemaConverter.convert(schema);
        Map<String, Object> props = (Map<String, Object>) root.get("properties");
        Map<String, Object> v = (Map<String, Object>) props.get("v");
        List<Map<String, Object>> anyOf = (List<Map<String, Object>>) v.get("anyOf");
        assertNotNull(anyOf);
        // should contain string and integer types plus null
        assertEquals(3, anyOf.size());
    }

    @Test
    void testLogicalTypesTimestampAndDate() {
        // timestamp-millis on long
        Schema baseLong = Schema.create(Schema.Type.LONG);
        Schema ts = LogicalTypes.timestampMillis().addToSchema(baseLong);
        Map<String, Object> js = AvroToJsonSchemaConverter.convert(ts);
        assertEquals("string", js.get("type"));
        assertEquals("date-time", js.get("format"));

        // date on long
        Schema baseLong2 = Schema.create(Schema.Type.INT);
        Schema date = LogicalTypes.date().addToSchema(baseLong2);
        Map<String, Object> js2 = AvroToJsonSchemaConverter.convert(date);
        assertEquals("string", js2.get("type"));
        assertEquals("date", js2.get("format"));
    }

    @Test
    void testEnumFixedHandling() {
        Schema e = Schema.createEnum("Color", null, null, List.of("RED", "GREEN"));
        Map<String, Object> js = AvroToJsonSchemaConverter.convert(e);
        assertEquals("string", js.get("type"));
        assertEquals(List.of("RED", "GREEN"), js.get("enum"));

        Schema f = Schema.createFixed("F16", null, null, 16);
        Map<String, Object> jsf = AvroToJsonSchemaConverter.convert(f);
        assertEquals("string", jsf.get("type"));
        assertEquals("base64", jsf.get("contentEncoding"));
    }

    @Test
    void testArrayAndMapRecursion() {
        Schema array = Schema.createArray(Schema.create(Schema.Type.STRING));
        Map<String, Object> js = AvroToJsonSchemaConverter.convert(array);
        assertEquals("array", js.get("type"));
        assertTrue(js.containsKey("items"));

        Schema map = Schema.createMap(Schema.create(Schema.Type.INT));
        Map<String, Object> jsm = AvroToJsonSchemaConverter.convert(map);
        assertEquals("object", jsm.get("type"));
        assertTrue(jsm.containsKey("additionalProperties"));
    }
}
