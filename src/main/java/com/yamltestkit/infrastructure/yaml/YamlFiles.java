package com.yamltestkit.infrastructure.yaml;

import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import com.yamltestkit.domain.model.CaseDefinitionException;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.io.InputStream;

/**
 * Reads YAML files of the testkit into Jackson trees. Duplicate keys are rejected, since a repeated key would
 * silently drop half of a case.
 */
public final class YamlFiles {

    private static final ObjectMapper YAML = YAMLMapper.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .build();

    private YamlFiles() {
    }

    public static ObjectMapper mapper() {
        return YAML;
    }

    /**
     * @param label how the file is named in error messages
     * @throws CaseDefinitionException when the file can't be read, is not valid YAML, or is empty
     */
    public static JsonNode read(Resource resource, String label) {
        JsonNode root;
        try (InputStream in = resource.getInputStream()) {
            root = YAML.readTree(in);
        } catch (IOException e) {
            throw new CaseDefinitionException(label + ": invalid YAML: " + e.getMessage(), e);
        }
        if (root == null || root.isMissingNode() || root.isNull()) {
            throw new CaseDefinitionException(label + ": the file is empty");
        }
        return root;
    }
}
