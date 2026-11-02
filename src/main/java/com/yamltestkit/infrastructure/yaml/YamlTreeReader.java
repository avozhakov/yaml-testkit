package com.yamltestkit.infrastructure.yaml;

import com.fasterxml.jackson.core.JsonLocation;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.MissingNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.yamltestkit.domain.json.Abbreviations;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds the tree of one YAML document with anchors, aliases and merge keys resolved. Jackson's own tree reader turns
 * an alias into its anchor name as a plain string and a merge key into a literal {@code "<<"} key.
 * <ul>
 *   <li>An alias ({@code *name}) becomes a deep copy of the node anchored as {@code &name} earlier in the document.
 *       An unknown alias is an error.</li>
 *   <li>A merge key ({@code <<: *base} or {@code <<: [*a, *b]}) copies the top-level keys of the given mappings into
 *       its mapping (YAML merge-key semantics: shallow; a key written in the mapping wins over a merged one; in a list,
 *       an earlier mapping wins). Merged keys come first. A value other than a mapping or a list of mappings is an
 *       error.</li>
 *   <li>Aliases may add at most {@value #MAX_ALIAS_NODES} nodes to a document, so nested aliases can't expand into
 *       an "alias bomb".</li>
 * </ul>
 * Scalars are read by the mapper, so their typing is exactly Jackson's. Errors are {@link JsonParseException}s with
 * the line and column of the alias or merge key. One instance reads one document.
 */
final class YamlTreeReader {

    static final int MAX_ALIAS_NODES = 100_000;

    private static final String MERGE_KEY = "<<";

    private final ObjectMapper mapper;
    private final AnchorAwareYamlParser parser;
    private final Map<String, Anchored> anchors = new HashMap<>();
    /** Nodes built so far, alias copies included; the difference over a subtree is its size. */
    private int nodes;
    private int aliasNodes;

    YamlTreeReader(ObjectMapper mapper, AnchorAwareYamlParser parser) {
        this.mapper = mapper;
        this.parser = parser;
    }

    /** The first document of the input, or a {@link MissingNode} when the input has no content. */
    JsonNode read() throws IOException {
        return parser.nextToken() == null ? MissingNode.getInstance() : value();
    }

    private JsonNode value() throws IOException {
        if (parser.isCurrentAlias()) {
            return alias();
        }
        String anchor = parser.valueAnchor();
        int start = nodes++;
        JsonNode node = switch (parser.currentToken()) {
            case START_OBJECT -> object();
            case START_ARRAY -> array();
            default -> mapper.readTree(parser);
        };
        if (anchor != null) {
            anchors.put(anchor, new Anchored(node, nodes - start));
        }
        return node;
    }

    private JsonNode alias() throws IOException {
        String name = parser.getText();
        Anchored anchored = anchors.get(name);
        if (anchored == null) {
            throw error("unknown alias '*" + name + "': no anchor '&" + name + "' is defined before it",
                    parser.currentTokenLocation());
        }
        aliasNodes += anchored.size();
        if (aliasNodes > MAX_ALIAS_NODES) {
            throw error("aliases expand to more than " + MAX_ALIAS_NODES + " nodes (nested aliases multiply);"
                    + " write the shared data with fewer levels of aliases", parser.currentTokenLocation());
        }
        nodes += anchored.size();
        return anchored.node().deepCopy();
    }

    private ArrayNode array() throws IOException {
        ArrayNode array = mapper.getNodeFactory().arrayNode();
        while (parser.nextToken() != JsonToken.END_ARRAY) {
            array.add(value());
        }
        return array;
    }

    private ObjectNode object() throws IOException {
        ObjectNode explicit = mapper.getNodeFactory().objectNode();
        List<ObjectNode> merged = List.of();
        while (parser.nextToken() == JsonToken.FIELD_NAME) {
            String key = parser.currentName();
            JsonLocation keyLocation = parser.currentTokenLocation();
            parser.nextToken();
            JsonNode value = value();
            if (MERGE_KEY.equals(key)) {
                merged = mergeSources(value, keyLocation);
            } else {
                explicit.set(key, value);
            }
        }
        return merged.isEmpty() ? explicit : merge(explicit, merged);
    }

    private List<ObjectNode> mergeSources(JsonNode value, JsonLocation keyLocation) throws JsonParseException {
        List<JsonNode> sources = value.isArray() ? value.valueStream().toList() : List.of(value);
        if (sources.stream().anyMatch(source -> !source.isObject())) {
            throw error("merge key '<<' takes a mapping or a list of mappings (such as *base or [*a, *b]), but its"
                    + " value is " + Abbreviations.abbreviate(value), keyLocation);
        }
        return sources.stream().map(ObjectNode.class::cast).toList();
    }

    private ObjectNode merge(ObjectNode explicit, List<ObjectNode> sources) {
        ObjectNode result = mapper.getNodeFactory().objectNode();
        for (ObjectNode source : sources) {
            for (Map.Entry<String, JsonNode> field : source.properties()) {
                if (!explicit.has(field.getKey()) && !result.has(field.getKey())) {
                    result.set(field.getKey(), field.getValue());
                }
            }
        }
        result.setAll(explicit);
        return result;
    }

    private JsonParseException error(String message, JsonLocation location) {
        return new JsonParseException(parser, message, location);
    }

    private record Anchored(JsonNode node, int size) {
    }
}
