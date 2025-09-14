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

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.avro.Schema;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Map;

@Mojo(name = "avsc-to-json", defaultPhase = LifecyclePhase.GENERATE_RESOURCES, requiresDependencyResolution = ResolutionScope.NONE)
public class AvroToJsonMojo extends AbstractMojo {
    /**
     * Input file or directory containing Avro schema JSON files (.avsc).
     */
    @Parameter(property = "input", alias = "input", required = true)
    private File input;

    /**
     * Output directory where JSON Schema files will be written.
     */
    @Parameter(property = "outputDirectory", alias = "outputDirectory", defaultValue = "${project.build.directory}/generated-schemas", required = true)
    private File outputDirectory;

    /**
     * Whether to search directories recursively for .avsc files.
     */
    @Parameter(property = "recursive", alias = "recursive", defaultValue = "true")
    private boolean recursive;

    public void execute() throws MojoExecutionException {
        if (input == null) {
            throw new MojoExecutionException("Parameter 'input' is required. Use -Dinput or <input> configuration.");
        }
        if (!outputDirectory.exists() && !outputDirectory.mkdirs()) {
            throw new MojoExecutionException("Could not create output directory: " + outputDirectory);
        }
        if (!input.exists()) {
            throw new MojoExecutionException("Input path does not exist: " + input);
        }
        try {
            AvroToJsonSchemaConverter.setLog(getLog());
            if (input.isDirectory()) {
                processDirectory(input);
            } else {
                processFile(input, input.getName());
            }
        } catch (IOException e) {
            throw new MojoExecutionException("Conversion failed", e);
        }
    }

    private void processDirectory(File dir) throws IOException, MojoExecutionException {
        File[] files = dir.listFiles();
        if (files == null) {
            return;
        }
        for (File f : files) {
            if (f.isDirectory() && recursive) {
                processDirectory(f);
            } else if (f.isFile() && f.getName().endsWith(".avsc")) {
                processFile(f, relativize(input, f));
            }
        }
    }

    private String relativize(File base, File file) throws IOException {
        String rel = file.getCanonicalFile().toPath().toString();
        try {
            rel = base.getCanonicalFile().toPath().relativize(file.getCanonicalFile().toPath()).toString();
        } catch (Exception ignored) {
        }
        return rel;
    }

    private void processFile(File avscFile, String relativeName) throws IOException, MojoExecutionException {
        String content = Files.readString(avscFile.toPath());
        org.apache.avro.Schema avroSchema;
        try {
            avroSchema = new Schema.Parser().parse(content);
        } catch (Exception e) {
            throw new MojoExecutionException("Failed to parse Avro schema: " + avscFile, e);
        }
        Map<String, Object> jsonSchema = AvroToJsonSchemaConverter.convert(avroSchema);

        // Determine output file path
        String outName = relativeName;
        if (outName.endsWith(".avsc")) outName = outName.substring(0, outName.length() - 5);
        File outFile = new File(outputDirectory, outName + ".schema.json");
        File parent = outFile.getParentFile();
        if (!parent.exists() && !parent.mkdirs()) {
            throw new MojoExecutionException("Could not create directory: " + parent);
        }
        String json = new ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(jsonSchema);
        Files.writeString(outFile.toPath(), json);
        getLog().info("Converted " + avscFile + " -> " + outFile);
    }
}
