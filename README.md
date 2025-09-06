# Avro to JSON Schema Maven Plugin

[![GitHub license](https://img.shields.io/github/license/S1M0NM/avro-to-json-maven-plugin.svg)](https://github.com/S1M0NM/avro-to-json-maven-plugin/blob/main/LICENSE)

Convert Apache Avro `.avsc` schemas to JSON Schema (Draft-07) during your Maven build.

This plugin scans a given file or directory for `.avsc` files and generates corresponding `*.schema.json` files into a
build directory. It can be bound to your lifecycle so the JSON Schemas are always up to date.

- Project URL: https://github.com/S1M0NM/avro-to-json-maven-plugin
- Coordinates: `io.github.s1m0nm:avro-to-json-plugin`

## Why

Many teams keep their canonical data definitions in Avro but also need JSON Schema for documentation, validation, or
integration tooling. This plugin automates the conversion as part of your Maven build.

## Getting Started

Add the plugin to your project `pom.xml`. You can execute it on demand or bind it to a phase (default is
`generate-resources`).

```xml

<build>
    <plugins>
        <plugin>
            <groupId>io.github.s1m0nm</groupId>
            <artifactId>avro-to-json-plugin</artifactId>
            <version>1.0.0</version>
            <executions>
                <execution>
                    <goals>
                        <goal>avsc-to-json</goal>
                    </goals>
                    <!-- optional: explicitly bind the phase -->
                    <phase>generate-resources</phase>
                    <configuration>
                        <input>${project.basedir}/src/main/avro</input>
                        <outputDirectory>${project.build.directory}/generated-schemas</outputDirectory>
                        <recursive>true</recursive>
                    </configuration>
                </execution>
            </executions>
        </plugin>
    </plugins>
</build>
```

Alternatively, run it directly:

```
mvn io.github.s1m0nm:avro-to-json-plugin:1.0.0:avsc-to-json \
  -Dinput=src/main/avro \
  -DoutputDirectory=target/generated-schemas \
  -Drecursive=true
```

Show plugin help:

```
mvn io.github.s1m0nm:avro-to-json-plugin:1.0.0:help -Ddetail -Dgoal=avsc-to-json
```

## Goal

- `avro-to-json:avsc-to-json` (default phase: `generate-resources`)

## Configuration Parameters

- `input` (required) — File or directory to scan for `.avsc` files.
    - Property: `input`
- `outputDirectory` (required, with default) — Output directory for generated JSON Schemas.
    - Default: `${project.build.directory}/generated-schemas`
    - Property: `outputDirectory`
- `recursive` — Whether to recurse into subdirectories when `input` is a directory.
    - Default: `true`
    - Property: `recursive`

Example minimal configuration:

```xml

<configuration>
    <input>${project.basedir}/src/main/avro</input>
</configuration>
```

## What gets generated

For every `X.avsc` found, the plugin writes `X.schema.json` into the configured output directory, preserving relative
subdirectories when scanning recursively.

Example:

- Input: `src/main/avro/example.avsc`
- Output: `target/generated-schemas/example.schema.json`

## Supported Features and JSON Schema Attributes

Generated schemas target JSON Schema Draft-07 and include the following mappings and features:

- Root `$schema`: `http://json-schema.org/draft-07/schema#`
- Avro doc strings → JSON Schema `description` on records and fields
- Required vs optional fields:
    - Non-nullable Avro fields are listed in `required`
    - Nullable unions (e.g. `["null", "string"]`) yield either `type: ["string", "null"]` when possible, or `anyOf` with
      `null`
- Types:
    - `null` → `{ "type": "null" }`
    - `boolean` → `{ "type": "boolean" }`
    - `int` → `{ "type": "integer", "format": "int32" }`
    - `long` → `{ "type": "integer" }` (with logical types handled below)
    - `float` → `{ "type": "number", "format": "float" }`
    - `double` → `{ "type": "number", "format": "double" }`
    - `bytes` → `{ "type": "string", "contentEncoding": "base64" }`
    - `fixed` → `{ "type": "string", "contentEncoding": "base64" }`
    - `string` → `{ "type": "string" }`
    - `enum` → `{ "type": "string", "enum": [ ... ] }`
    - `array` → `{ "type": "array", "items": <element schema> }`
    - `map` → `{ "type": "object", "additionalProperties": <value schema> }`
    - `record` → `{ "type": "object", "properties": { ... }, "required": [ ... ] }`
- Logical types:
    - `long` with `logicalType: "timestamp-millis"` → `{ "type": "string", "format": "date-time" }`
    - `long` with `logicalType: "date"` → `{ "type": "string", "format": "date" }`
- Default values:
    - Avro field defaults are converted to JSON-native values where possible and emitted as JSON Schema `default`.

Notes and limitations:

- Complex unions (more than one non-null Avro type) are represented using `anyOf` (plus `null` when nullable).
- `$ref`/definitions reuse is not emitted; repeated record types are inlined in the current implementation.

## Examples

See the integration tests for concrete examples:

- Simple schema: `src/it/simple-it/src/main/avro/example.avsc` →
  `target/it/simple-it/target/generated-schemas/example.schema.json`
- Optional/union: `src/it/optional-union-it/src/main/avro/optional-union.avsc`
- Complex nested: `src/it/complex-nested-it/src/main/avro/complex.avsc`

You can run the ITs locally:

```
mvn -Prun-its verify
```

## Version and Compatibility

- Requires Java 21 (as configured by the plugin project)
- Uses Apache Avro 1.11.x and emits JSON Schema Draft-07

## Troubleshooting

- "Input path does not exist": ensure `-Dinput` or `<input>` points to a valid file or directory.
- No files generated: verify your `.avsc` files are present and the `recursive` flag covers nested directories as
  needed.
- JSON defaults: complex Avro defaults are converted on a best-effort basis; unexpected structures may be skipped with a
  log warning.

## License

This project follows the license of the repository. See the LICENSE file if present in the repository.
