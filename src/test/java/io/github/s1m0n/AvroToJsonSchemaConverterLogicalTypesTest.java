package io.github.s1m0n;

import org.apache.avro.LogicalTypes;
import org.apache.avro.Schema;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests that verify JSON Schema conversion for all supported Avro logical types.
 */
public class AvroToJsonSchemaConverterLogicalTypesTest {

    @Test
    void int_logical_date_and_time_millis() {
        // date (int)
        Schema intSchema = Schema.create(Schema.Type.INT);
        Schema date = LogicalTypes.date().addToSchema(intSchema);
        Map<String, Object> js = AvroToJsonSchemaConverter.convert(date);
        assertEquals("string", js.get("type"));
        assertEquals("date", js.get("format"));

        // time-millis (int)
        Schema intSchema2 = Schema.create(Schema.Type.INT);
        Schema timeMillis = LogicalTypes.timeMillis().addToSchema(intSchema2);
        Map<String, Object> js2 = AvroToJsonSchemaConverter.convert(timeMillis);
        assertEquals("string", js2.get("type"));
        assertEquals("time", js2.get("format"));
    }

    @Test
    void long_logical_time_and_timestamps() {
        // time-micros (long)
        Schema longSchema = Schema.create(Schema.Type.LONG);
        Schema timeMicros = LogicalTypes.timeMicros().addToSchema(longSchema);
        Map<String, Object> js = AvroToJsonSchemaConverter.convert(timeMicros);
        assertEquals("string", js.get("type"));
        assertEquals("time", js.get("format"));

        // timestamp-millis (long)
        Schema longSchema2 = Schema.create(Schema.Type.LONG);
        Schema tsMillis = LogicalTypes.timestampMillis().addToSchema(longSchema2);
        Map<String, Object> js2 = AvroToJsonSchemaConverter.convert(tsMillis);
        assertEquals("string", js2.get("type"));
        assertEquals("date-time", js2.get("format"));

        // timestamp-micros (long)
        Schema longSchema3 = Schema.create(Schema.Type.LONG);
        Schema tsMicros = LogicalTypes.timestampMicros().addToSchema(longSchema3);
        Map<String, Object> js3 = AvroToJsonSchemaConverter.convert(tsMicros);
        assertEquals("string", js3.get("type"));
        assertEquals("date-time", js3.get("format"));

        // local-timestamp-millis (long)
        Schema longSchema4 = Schema.create(Schema.Type.LONG);
        Schema ltsMillis = LogicalTypes.localTimestampMillis().addToSchema(longSchema4);
        Map<String, Object> js4 = AvroToJsonSchemaConverter.convert(ltsMillis);
        assertEquals("string", js4.get("type"));
        assertEquals("date-time", js4.get("format"));

        // local-timestamp-micros (long)
        Schema longSchema5 = Schema.create(Schema.Type.LONG);
        Schema ltsMicros = LogicalTypes.localTimestampMicros().addToSchema(longSchema5);
        Map<String, Object> js5 = AvroToJsonSchemaConverter.convert(ltsMicros);
        assertEquals("string", js5.get("type"));
        assertEquals("date-time", js5.get("format"));
    }

    @Test
    void string_logical_uuid() {
        Schema str = Schema.create(Schema.Type.STRING);
        Schema uuid = LogicalTypes.uuid().addToSchema(str);
        Map<String, Object> js = AvroToJsonSchemaConverter.convert(uuid);
        assertEquals("string", js.get("type"));
        assertEquals("uuid", js.get("format"));
    }

    @Test
    void bytes_and_fixed_decimal_and_fixed_duration() {
        // bytes decimal with precision/scale
        Schema bytes = Schema.create(Schema.Type.BYTES);
        Schema decBytes = LogicalTypes.decimal(10, 2).addToSchema(bytes);
        Map<String, Object> js = AvroToJsonSchemaConverter.convert(decBytes);
        assertEquals("string", js.get("type"));
        assertEquals("decimal", js.get("x-avro-logicalType"));
        assertEquals(10, js.get("x-precision"));
        assertEquals(2, js.get("x-scale"));

        // fixed decimal with precision/scale
        Schema fixed = Schema.createFixed("F16", null, null, 16);
        Schema decFixed = LogicalTypes.decimal(20, 4).addToSchema(fixed);
        Map<String, Object> js2 = AvroToJsonSchemaConverter.convert(decFixed);
        assertEquals("string", js2.get("type"));
        assertEquals("decimal", js2.get("x-avro-logicalType"));
        assertEquals(20, js2.get("x-precision"));
        assertEquals(4, js2.get("x-scale"));

        // fixed duration (Avro 1.11.4: annotate fixed(12) with logicalType property "duration")
        Schema dur = Schema.createFixed("Dur", null, null, 12);
        dur.addProp("logicalType", "duration");
        Map<String, Object> js3 = AvroToJsonSchemaConverter.convert(dur);
        assertEquals("string", js3.get("type"));
        // Implementation maps duration to format "duration"
        assertEquals("duration", js3.get("format"));
    }

    @Test
    void nullable_union_with_logical_type_keeps_nullability() {
        // nullable timestamp-millis
        Schema ts = LogicalTypes.timestampMillis().addToSchema(Schema.create(Schema.Type.LONG));
        Schema union = Schema.createUnion(Schema.create(Schema.Type.NULL), ts);
        Map<String, Object> js = AvroToJsonSchemaConverter.convert(union);
        // For simple nullable with single non-null type, converter emits the base schema with type as array
        assertEquals(java.util.Arrays.asList("string", "null"), js.get("type"));
        assertEquals("date-time", js.get("format"));
    }
}
