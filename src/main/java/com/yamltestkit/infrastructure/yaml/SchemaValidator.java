package com.yamltestkit.infrastructure.yaml;

import com.fasterxml.jackson.databind.JsonNode;
import com.networknt.schema.Error;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaLocation;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import com.yamltestkit.domain.model.CaseDefinitionException;

import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Validates testkit files against {@code yaml-testkit.schema.json} (Draft 2020-12). Each file kind is validated
 * against its own definition, so errors are not mixed with those of the root's content-based dispatch (which only
 * serves IDEs).
 */
public final class SchemaValidator {

    public static final String SCHEMA_RESOURCE = "com/yamltestkit/yaml-testkit.schema.json";

    /** Unknown keys are reported first: they are the usual cause, other errors often follow from them. */
    private static final String ADDITIONAL_PROPERTIES = "additionalProperties";
    private static final String SCHEMA_FILE_NAME = "yaml-testkit.schema.json";
    private static final Pattern CASE_POINTER = Pattern.compile("^/cases/(\\d+)");

    private final Map<FileKind, Schema> schemas = new EnumMap<>(FileKind.class);

    public SchemaValidator() {
        SchemaRegistry registry = SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12);
        for (FileKind kind : FileKind.values()) {
            schemas.put(kind, registry.getSchema(
                    SchemaLocation.of("classpath:" + SCHEMA_RESOURCE + "#/$defs/" + kind.definition)));
        }
    }

    /**
     * @param label how the file is named in error messages
     * @throws CaseDefinitionException listing every violation with its JSON pointer and schema location
     */
    public void validate(JsonNode root, FileKind kind, String label) {
        List<Error> errors = schemas.get(kind).validate(root);
        if (!errors.isEmpty()) {
            String details = errors.stream()
                    .sorted(Comparator.comparing(error -> !ADDITIONAL_PROPERTIES.equals(error.getKeyword())))
                    .map(error -> "  - " + describe(error, root))
                    .distinct()
                    .collect(Collectors.joining("\n"));
            throw new CaseDefinitionException(label + ": does not match the YAML Testkit schema:\n" + details);
        }
    }

    private static String describe(Error error, JsonNode root) {
        String pointer = error.getInstanceLocation().toString();
        return (pointer.isEmpty() ? "/" : pointer) + caseName(pointer, root) + ": " + error.getMessage()
                + " (schema " + SCHEMA_FILE_NAME + "#" + error.getSchemaLocation().getFragment() + ")";
    }

    private static String caseName(String pointer, JsonNode root) {
        Matcher matcher = CASE_POINTER.matcher(pointer);
        if (!matcher.find()) {
            return "";
        }
        JsonNode name = root.path("cases").path(Integer.parseInt(matcher.group(1))).path("name");
        return name.isTextual() ? " (case '" + name.asText() + "')" : "";
    }

    /** The kinds of testkit files and their schema definitions. */
    public enum FileKind {
        CASE_FILE("caseFile"),
        CONTEXT_FILE("contextFile"),
        DEFAULTS_FILE("caseDefaultsFile");

        private final String definition;

        FileKind(String definition) {
            this.definition = definition;
        }
    }
}
