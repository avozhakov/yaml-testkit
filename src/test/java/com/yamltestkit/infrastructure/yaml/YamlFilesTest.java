package com.yamltestkit.infrastructure.yaml;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.IntNode;
import com.fasterxml.jackson.databind.node.MissingNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Collections;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static com.yamltestkit.domain.json.JsonTestFixtures.json;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class YamlFilesTest {

    /** Jackson's plain YAML reading, as the testkit read files before it resolved anchors. */
    private static final ObjectMapper PLAIN_JACKSON = YAMLMapper.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .build();

    private static final String SCALARS = """
            booleans: [ true, false, yes, no, on, off, True, NO, y, n ]
            integers: [ 0, -7, 017, 0o17, 0x1F, 0b101, 1_000, 2147483648, 123456789012345678901234567890 ]
            floats: [ 1.5, -0.25, 1e3, 6.02e+23, 1_000.5, 3.4028236e38 ]
            nulls: [ ~, null, Null, "" ]
            strings: [ '123', "true", 2001-12-14, 2001-12-14t21:59:43.10-05:00, 12:30, 1.2.3, "#any", plain text ]
            tagged: [ !!str 5, !!int "7", !!float "1", !!binary aGVsbG8= ]
            blocks:
              literal: |
                line one
                line two
              folded: >
                folded
                text
            empty:
            nested: { list: [ { a: 1 }, [ 2, [ 3 ] ] ], map: { } }
            """;

    @Test
    void readTree_whenDocumentHasNoAnchors_shouldReadEveryScalarExactlyAsJacksonDoes() throws Exception {
        JsonNode expected = PLAIN_JACKSON.readTree(SCALARS);

        JsonNode result = YamlFiles.readTree(SCALARS);

        assertThat(result).isEqualTo(expected);
        assertThat(nodeTypes(result)).isEqualTo(nodeTypes(expected));
    }

    @Test
    void readTree_whenAliasPointsToAMapping_shouldInsertAnIndependentCopyOfIt() throws Exception {
        ObjectNode result = (ObjectNode) YamlFiles.readTree("""
                a: &user { id: u1, geo: { country: US } }
                b: *user
                """);
        ((ObjectNode) result.get("b").get("geo")).put("country", "DE");

        assertThat(result.get("a")).isEqualTo(json("{\"id\": \"u1\", \"geo\": {\"country\": \"US\"}}"));
        assertThat(result.get("b")).isEqualTo(json("{\"id\": \"u1\", \"geo\": {\"country\": \"DE\"}}"));
    }

    @Test
    void readTree_whenAliasPointsToASequence_shouldInsertACopyOfIt() throws Exception {
        JsonNode result = YamlFiles.readTree("""
                ids: &ids [ 1, two, { three: 3 } ]
                again: *ids
                """);

        assertThat(result).isEqualTo(json("{\"ids\": [1, \"two\", {\"three\": 3}], \"again\": [1, \"two\", {\"three\": 3}]}"));
    }

    @Test
    void readTree_whenAliasPointsToAScalar_shouldKeepTheScalarType() throws Exception {
        JsonNode result = YamlFiles.readTree("""
                count: &count 42
                name: &name running shoes
                flag: &flag yes
                values: [ *count, *name, *flag ]
                """);

        assertThat(result.get("values")).isEqualTo(json("[42, \"running shoes\", true]"));
        assertThat(result.get("values").get(0)).isInstanceOf(IntNode.class);
        assertThat(result.get("values").get(1)).isInstanceOf(TextNode.class);
    }

    @Test
    void readTree_whenAnchorsAreNested_shouldResolveTheInnerAndTheOuterAnchor() throws Exception {
        JsonNode result = YamlFiles.readTree("""
                base: &base
                  geo: &geo { country: US }
                  copies: [ *geo ]
                inner: *geo
                outer: *base
                """);

        assertThat(result.get("inner")).isEqualTo(json("{\"country\": \"US\"}"));
        assertThat(result.get("outer"))
                .isEqualTo(json("{\"geo\": {\"country\": \"US\"}, \"copies\": [{\"country\": \"US\"}]}"));
    }

    @Test
    void readTree_whenAnAnchorIsRedefined_shouldResolveLaterAliasesToTheLatestDefinition() throws Exception {
        JsonNode result = YamlFiles.readTree("""
                first: &v 1
                a: *v
                second: &v 2
                b: *v
                """);

        assertThat(result).isEqualTo(json("{\"first\": 1, \"a\": 1, \"second\": 2, \"b\": 2}"));
    }

    @Test
    void readTree_whenMappingHasAMergeKey_shouldLetItsOwnKeysWin() throws Exception {
        JsonNode result = YamlFiles.readTree("""
                base: &base { prompt: running shoes, ad_count: 2 }
                request:
                  ad_count: 1
                  <<: *base
                  chat: chat-1
                """);

        assertThat(result.get("request"))
                .isEqualTo(json("{\"prompt\": \"running shoes\", \"ad_count\": 1, \"chat\": \"chat-1\"}"));
        assertThat(result.get("request").propertyStream().map(Map.Entry::getKey).toList())
                .containsExactly("prompt", "ad_count", "chat");
    }

    @Test
    void readTree_whenMergeKeyListsSeveralMappings_shouldLetTheEarlierMappingWin() throws Exception {
        JsonNode result = YamlFiles.readTree("""
                a: &a { x: from-a, y: from-a }
                b: &b { y: from-b, z: from-b }
                merged: { <<: [ *a, *b ], z: own }
                """);

        assertThat(result.get("merged")).isEqualTo(json("{\"x\": \"from-a\", \"y\": \"from-a\", \"z\": \"own\"}"));
    }

    @Test
    void readTree_whenMergedAndOwnValuesAreBothMappings_shouldReplaceTheMergedMappingNotMergeIt() throws Exception {
        JsonNode result = YamlFiles.readTree("""
                base: &base { intent: { prompt: shoes, ad_count: 2 }, meta: { id: r1 } }
                request: { <<: *base, intent: { prompt: boots } }
                """);

        assertThat(result.get("request"))
                .isEqualTo(json("{\"intent\": {\"prompt\": \"boots\"}, \"meta\": {\"id\": \"r1\"}}"));
    }

    @Test
    void readTree_whenMergeKeyHasAnInlineMapping_shouldMergeIt() throws Exception {
        JsonNode result = YamlFiles.readTree("request: { <<: { a: 1, b: 2 }, b: 3 }\n");

        assertThat(result.get("request")).isEqualTo(json("{\"a\": 1, \"b\": 3}"));
    }

    @Test
    void readTree_whenAliasNamesAnUnknownAnchor_shouldFailWithItsLine() {
        assertThatThrownBy(() -> YamlFiles.readTree("""
                a: &known 1
                b: *nowhere
                """))
                .isInstanceOf(JsonParseException.class)
                .hasMessageContaining("unknown alias '*nowhere': no anchor '&nowhere' is defined before it")
                .hasMessageContaining("line: 2, column: 4");
    }

    @Test
    void readTree_whenAliasIsInsideItsOwnAnchoredNode_shouldFailAsUnknown() {
        assertThatThrownBy(() -> YamlFiles.readTree("loop: &loop [ 1, *loop ]\n"))
                .isInstanceOf(JsonParseException.class)
                .hasMessageContaining("unknown alias '*loop'")
                .hasMessageContaining("line: 1");
    }

    @ParameterizedTest
    @ValueSource(strings = {"base", "[ *a, 1 ]", "~", "*s"})
    void readTree_whenMergeValueIsNotAMappingOrAListOfMappings_shouldFailWithTheKeyLine(String mergeValue) {
        String yaml = "a: &a { x: 1 }\ns: &s text\nb:\n  <<: " + mergeValue + "\n  y: 2\n";

        assertThatThrownBy(() -> YamlFiles.readTree(yaml))
                .isInstanceOf(JsonParseException.class)
                .hasMessageContaining("merge key '<<' takes a mapping or a list of mappings")
                .hasMessageContaining("line: 4, column: 3");
    }

    @Test
    void readTree_whenNestedAliasesExpandBeyondTheLimit_shouldFailInsteadOfExpanding() {
        String laughs = "l0: &l0 [ " + repeat("x", 10) + " ]\n" + IntStream.rangeClosed(1, 6)
                .mapToObj(level -> "l" + level + ": &l" + level + " [ " + repeat("*l" + (level - 1), 10) + " ]")
                .collect(Collectors.joining("\n"));

        assertThatThrownBy(() -> YamlFiles.readTree(laughs))
                .isInstanceOf(JsonParseException.class)
                .hasMessageContaining("aliases expand to more than " + YamlTreeReader.MAX_ALIAS_NODES + " nodes");
    }

    @Test
    void readTree_whenInputIsEmpty_shouldReturnAMissingNode() throws Exception {
        JsonNode result = YamlFiles.readTree("# only a comment\n");

        assertThat(result).isEqualTo(MissingNode.getInstance());
    }

    private static String repeat(String item, int times) {
        return String.join(", ", Collections.nCopies(times, item));
    }

    /** The node type of every node, in document order, so an IntNode never passes for a LongNode. */
    private static String nodeTypes(JsonNode node) {
        StringBuilder types = new StringBuilder(node.getClass().getSimpleName());
        node.forEach(child -> types.append('(').append(nodeTypes(child)).append(')'));
        return types.toString();
    }
}
