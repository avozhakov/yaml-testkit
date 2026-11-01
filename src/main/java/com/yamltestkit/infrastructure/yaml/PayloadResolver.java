package com.yamltestkit.infrastructure.yaml;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.TextNode;
import com.yamltestkit.domain.json.JsonMappers;
import com.yamltestkit.domain.model.CaseDefinitionException;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * Resolves {@code { file: <path> }} payloads relative to the case file. JSON and YAML files become trees, any other
 * file a text node; {@link #text} always reads raw text (for exact golden compares and text matchers).
 */
@RequiredArgsConstructor
final class PayloadResolver {

    private final Resource caseFile;

    static boolean isFileRef(JsonNode node) {
        return node.isObject() && node.size() == 1 && node.path("file").isTextual();
    }

    /** The node itself, or the parsed content of the file it references. */
    JsonNode json(JsonNode node) {
        return isFileRef(node) ? jsonFile(node.get("file").asText()) : node;
    }

    /** Content of a payload file: a tree for JSON / YAML files, a text node otherwise. */
    JsonNode jsonFile(String path) {
        String lowerCase = path.toLowerCase(Locale.ROOT);
        if (lowerCase.endsWith(".json")) {
            return parse(path, JsonMappers.plain());
        }
        if (lowerCase.endsWith(".yaml") || lowerCase.endsWith(".yml")) {
            return parse(path, YamlFiles.mapper());
        }
        return TextNode.valueOf(readText(path));
    }

    /** The node itself, or the raw text of the file it references. */
    JsonNode text(JsonNode node) {
        return isFileRef(node) ? TextNode.valueOf(readText(node.get("file").asText())) : node;
    }

    String readText(String path) {
        try (InputStream in = open(path)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new CaseDefinitionException("can't read payload file " + path + ": " + e.getMessage(), e);
        }
    }

    private JsonNode parse(String path, ObjectMapper mapper) {
        try (InputStream in = open(path)) {
            return mapper.readTree(in);
        } catch (IOException e) {
            throw new CaseDefinitionException("invalid payload file " + path + ": " + e.getMessage(), e);
        }
    }

    private InputStream open(String path) throws IOException {
        Resource payload = caseFile.createRelative(path);
        if (!payload.exists()) {
            throw new CaseDefinitionException("payload file not found: " + path + " (relative to the case file)");
        }
        return payload.getInputStream();
    }
}
