package io.github.s1m0n;

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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Map;

@Mojo(name = "avsc-to-json", defaultPhase = LifecyclePhase.GENERATE_RESOURCES, requiresDependencyResolution = ResolutionScope.NONE)
public class AvroToJsonMojo extends AbstractMojo {
    /**
     * Input file or directory containing Avro schema JSON files (.avsc).
     */
    @Parameter(property = "avro.input", required = true)
    private File input;

    /**
     * Output directory where JSON Schema files will be written.
     */
    @Parameter(defaultValue = "${project.build.directory}/generated-schemas", property = "avro.output", required = true)
    private File outputDirectory;

    /**
     * Whether to search directories recursively for .avsc files.
     */
    @Parameter(property = "avro.recursive", defaultValue = "true")
    private boolean recursive;

    public void execute() throws MojoExecutionException {
        if (input == null) {
            throw new MojoExecutionException("Parameter 'input' is required (avro.input)");
        }
        if (!outputDirectory.exists() && !outputDirectory.mkdirs()) {
            throw new MojoExecutionException("Could not create output directory: " + outputDirectory);
        }
        if (!input.exists()) {
            throw new MojoExecutionException("Input path does not exist: " + input);
        }
        try {
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
        if (files == null) return;
        for (File f : files) {
            if (f.isDirectory() && recursive) {
                processDirectory(f);
            } else if (f.isFile() && f.getName().endsWith(".avsc")) {
                processFile(f, relativize(input, f));
            }
        }
    }

    private String relativize(File base, File file) throws IOException {
        String basePath = base.getCanonicalFile().toPath().toString();
        String filePath = file.getCanonicalFile().toPath().toString();
        String rel = file.getCanonicalFile().toPath().toString();
        try {
            rel = base.getCanonicalFile().toPath().relativize(file.getCanonicalFile().toPath()).toString();
        } catch (Exception ignored) {}
        return rel;
    }

    private void processFile(File avscFile, String relativeName) throws IOException, MojoExecutionException {
        String content = new String(Files.readAllBytes(avscFile.toPath()), StandardCharsets.UTF_8);
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
        java.nio.file.Files.write(outFile.toPath(), json.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        getLog().info("Converted " + avscFile + " -> " + outFile);
    }
}
