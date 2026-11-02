package com.yamltestkit.infrastructure.yaml;

import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import com.yamltestkit.domain.model.CaseDefinitionException;
import org.springframework.core.io.Resource;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * The one place where the testkit reads YAML (case files, {@code _context.yaml}, {@code _caseDefaults.yaml}, YAML
 * payload files) into Jackson trees. Anchors, aliases and {@code <<} merge keys are resolved
 * ({@link YamlTreeReader}); scalars keep Jackson's typing. Duplicate keys are rejected, since a repeated key would
 * silently drop half of a case.
 */
public final class YamlFiles {

    private static final ObjectMapper YAML = YAMLMapper.builder(new AnchorAwareYamlFactory())
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .build();

    private YamlFiles() {
    }

    /**
     * @param label how the file is named in error messages
     * @throws CaseDefinitionException when the file can't be read, is not valid YAML, or is empty
     */
    public static JsonNode read(Resource resource, String label) {
        JsonNode root;
        try (InputStream in = resource.getInputStream()) {
            root = readTree(in);
        } catch (IOException e) {
            throw new CaseDefinitionException(label + ": invalid YAML: " + e.getMessage(), e);
        }
        if (root == null || root.isMissingNode() || root.isNull()) {
            throw new CaseDefinitionException(label + ": the file is empty");
        }
        return root;
    }

    /** The first document of the stream; a {@code MissingNode} when it has no content. */
    static JsonNode readTree(InputStream in) throws IOException {
        try (AnchorAwareYamlParser parser = (AnchorAwareYamlParser) YAML.createParser(in)) {
            return new YamlTreeReader(YAML, parser).read();
        }
    }

    /** The first document of the text; a {@code MissingNode} when it has no content. */
    static JsonNode readTree(String yaml) throws IOException {
        return readTree(new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8)));
    }
}
