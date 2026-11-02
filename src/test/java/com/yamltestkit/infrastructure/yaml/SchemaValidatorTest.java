package com.yamltestkit.infrastructure.yaml;

import com.fasterxml.jackson.databind.JsonNode;
import com.networknt.schema.Error;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaLocation;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import com.yamltestkit.domain.model.CaseDefinitionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.io.ClassPathResource;

import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SchemaValidatorTest {

    private static final String FIXTURES = "com/yamltestkit/loader/";
    private static final String STUB_WITH_TWO_ANSWERS = """
            name: two answers
            call: { http: GET /is }
            mocks:
              - { bean: providers, method: find, returns: 1, returnsArg: 0 }
            """;

    private static final String EXACT_BODY = """
            name: exact body
            call: { http: GET /is }
            expect:
              body:
                mode: exact
            """;

    private static final String CALL_WITH_UNKNOWN_KEY = """
            name: path beside http
            call: { http: GET /is, path: /other }
            """;

    private static final String ANCHORS_AND_MERGE_KEYS = """
            caseDefaults:
              call: { http: POST /discover, body: &request { intent: { prompt: shoes }, meta: { id: r1 } } }
            cases:
              - name: as anchored
                expect: &ok { status: 200 }
              - name: merged
                call: { body: { <<: *request, intent: { prompt: boots } } }
                expect: { <<: *ok, headers: { Content-Type: application/json } }
            """;

    private static final String DEFINE_IN_A_LIST_CASE = """
            cases:
              - name: define inside a case
                define: { ok: &ok { status: 200 } }
                call: { http: GET /is }
                expect: *ok
            """;

    private static final String DEFINE_IN_DEFAULTS = """
            caseDefaults:
              define: { ok: &ok { status: 200 } }
              call: { http: GET /is }
            cases:
              - name: define inside caseDefaults
                expect: *ok
            """;

    private SchemaValidator underTest;

    @BeforeEach
    void setUp() {
        underTest = new SchemaValidator();
    }

    @Test
    void validate_whenCaseFileUsesTheFullFormat_shouldPass() {
        JsonNode root = read("format/full-format.yaml");

        assertThatCode(() -> underTest.validate(root, SchemaValidator.FileKind.CASE_FILE, "full-format.yaml"))
                .doesNotThrowAnyException();
    }

    @Test
    void validate_whenContextFileUsesEveryKey_shouldPass() {
        JsonNode root = read("discovery/_context.yaml");

        assertThatCode(() -> underTest.validate(root, SchemaValidator.FileKind.CONTEXT_FILE, "_context.yaml"))
                .doesNotThrowAnyException();
    }

    @Test
    void validate_whenDefaultsFileUsesEveryKey_shouldPass() {
        JsonNode root = read("discovery/_caseDefaults.yaml");

        assertThatCode(() -> underTest.validate(root, SchemaValidator.FileKind.DEFAULTS_FILE, "_caseDefaults.yaml"))
                .doesNotThrowAnyException();
    }

    @Test
    void validate_whenKeyIsMisspelled_shouldNameFileKeyPointerAndSchemaLocation() {
        JsonNode root = read("schema/typo.yaml");

        assertThatThrownBy(() -> underTest.validate(root, SchemaValidator.FileKind.CASE_FILE, "cases/typo.yaml"))
                .isInstanceOf(CaseDefinitionException.class)
                .hasMessageContaining("cases/typo.yaml")
                .hasMessageContaining("'expct'")
                .hasMessageContaining("/: ")
                .hasMessageContaining("#/$defs/caseObject/additionalProperties");
    }

    @Test
    void validate_whenKeyInListCaseIsMisspelled_shouldNameTheCase() {
        JsonNode root = read("schema/typo-in-list.yaml");

        assertThatThrownBy(() -> underTest.validate(root, SchemaValidator.FileKind.CASE_FILE, "typo-in-list.yaml"))
                .isInstanceOf(CaseDefinitionException.class)
                .hasMessageContaining("/cases/1/expect/assert/0 (case 'typo in assert')")
                .hasMessageContaining("'sise'");
    }

    @ParameterizedTest
    @ValueSource(strings = {"/is", "GET /a?b=1", "GET /ads ", "get /ads", "GET /a b", "GET  /ads"})
    void validate_whenHttpCallIsNotMethodAndPath_shouldRejectTheCall(String http) throws Exception {
        JsonNode root = YamlFiles.readTree("name: malformed http\ncall: { http: \"" + http + "\" }\n");

        assertThatThrownBy(() -> underTest.validate(root, SchemaValidator.FileKind.CASE_FILE, "malformed.yaml"))
                .isInstanceOf(CaseDefinitionException.class)
                .hasMessageContaining("/call/http")
                .hasMessageContaining("#/$defs/call/properties/http/pattern");
    }

    @Test
    void validate_whenCallHasAnUnknownKey_shouldRejectTheKey() throws Exception {
        JsonNode root = YamlFiles.readTree(CALL_WITH_UNKNOWN_KEY);

        assertThatThrownBy(() -> underTest.validate(root, SchemaValidator.FileKind.CASE_FILE, "unknown-key.yaml"))
                .isInstanceOf(CaseDefinitionException.class)
                .hasMessageContaining("/call")
                .hasMessageContaining("'path'");
    }

    @Test
    void validate_whenContextFileHasUnknownKey_shouldRejectIt() {
        JsonNode root = YamlFiles.read(new ClassPathResource(FIXTURES + "schema/typo.yaml"), "typo.yaml");

        assertThatThrownBy(() -> underTest.validate(root, SchemaValidator.FileKind.CONTEXT_FILE, "_context.yaml"))
                .isInstanceOf(CaseDefinitionException.class)
                .hasMessageContaining("_context.yaml")
                .hasMessageContaining("'name'");
    }

    @Test
    void validate_whenStubHasTwoAnswers_shouldRejectTheStub() throws Exception {
        JsonNode root = YamlFiles.readTree(STUB_WITH_TWO_ANSWERS);

        assertThatThrownBy(() -> underTest.validate(root, SchemaValidator.FileKind.CASE_FILE, "two-answers.yaml"))
                .isInstanceOf(CaseDefinitionException.class)
                .hasMessageContaining("/mocks/0");
    }

    @Test
    void validate_whenExactBodyValueIsNotAString_shouldRejectTheBody() throws Exception {
        JsonNode exactObject = YamlFiles.readTree(EXACT_BODY + "    value: { a: 1 }");
        JsonNode exactText = YamlFiles.readTree(EXACT_BODY + "    value: '{\"a\":1}'");

        assertThatThrownBy(() -> underTest.validate(exactObject, SchemaValidator.FileKind.CASE_FILE, "exact.yaml"))
                .isInstanceOf(CaseDefinitionException.class)
                .hasMessageContaining("/expect/body/value");
        assertThatCode(() -> underTest.validate(exactText, SchemaValidator.FileKind.CASE_FILE, "exact.yaml"))
                .doesNotThrowAnyException();
    }

    @Test
    void validate_whenFileUsesAnchorsAndMergeKeys_shouldSeeTheResolvedTree() throws Exception {
        JsonNode root = YamlFiles.readTree(ANCHORS_AND_MERGE_KEYS);

        assertThatCode(() -> underTest.validate(root, SchemaValidator.FileKind.CASE_FILE, "anchors.yaml"))
                .doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(strings = {"define/define-in-list.yaml", "define/define-in-single-case.yaml"})
    void validate_whenCaseFileHasDefine_shouldAcceptAnyContentInIt(String path) {
        JsonNode root = read(path);

        assertThatCode(() -> underTest.validate(root, SchemaValidator.FileKind.CASE_FILE, path))
                .doesNotThrowAnyException();
    }

    @Test
    void validate_whenContextFileHasDefine_shouldAcceptAnyContentInIt() {
        JsonNode root = read("configuration/define/_context.yaml");

        assertThatCode(() -> underTest.validate(root, SchemaValidator.FileKind.CONTEXT_FILE, "_context.yaml"))
                .doesNotThrowAnyException();
    }

    @Test
    void validate_whenDefaultsFileHasDefine_shouldAcceptAnyContentInIt() {
        JsonNode root = read("configuration/define/_caseDefaults.yaml");

        assertThatCode(() -> underTest.validate(root, SchemaValidator.FileKind.DEFAULTS_FILE, "_caseDefaults.yaml"))
                .doesNotThrowAnyException();
    }

    @ParameterizedTest
    @MethodSource("defineBelowTheTopLevel")
    void validate_whenDefineIsBelowTheTopLevel_shouldRejectIt(String yaml, String pointer, String schemaLocation)
            throws Exception {
        JsonNode root = YamlFiles.readTree(yaml);

        assertThatThrownBy(() -> underTest.validate(root, SchemaValidator.FileKind.CASE_FILE, "nested-define.yaml"))
                .isInstanceOf(CaseDefinitionException.class)
                .hasMessageContaining(pointer)
                .hasMessageContaining(schemaLocation);
    }

    @ParameterizedTest
    @ValueSource(strings = {"format/full-format.yaml", "discovery/_context.yaml", "discovery/_caseDefaults.yaml",
            "references/references.yaml", "case-defaults/case-defaults-merge.yaml", "define/define-in-list.yaml",
            "define/define-in-single-case.yaml", "configuration/define/_context.yaml",
            "configuration/define/_caseDefaults.yaml"})
    void rootSchema_whenFileIsValid_shouldDispatchToItsDefinitionForIdes(String path) {
        Schema rootSchema = SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12)
                .getSchema(SchemaLocation.of("classpath:" + SchemaValidator.SCHEMA_RESOURCE));

        List<Error> result = rootSchema.validate(read(path));

        assertThat(result).isEmpty();
    }

    private static Stream<Arguments> defineBelowTheTopLevel() {
        return Stream.of(
                Arguments.of(DEFINE_IN_A_LIST_CASE, "/cases/0/define (case 'define inside a case')",
                        "#/$defs/listCase/properties/define"),
                Arguments.of(DEFINE_IN_DEFAULTS, "/caseDefaults/define", "#/$defs/caseDefaults/properties/define"));
    }

    private static JsonNode read(String path) {
        return YamlFiles.read(new ClassPathResource(FIXTURES + path), path);
    }
}
