package com.yamltestkit.infrastructure.yaml;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.yamltestkit.domain.model.ArgMatcher;
import com.yamltestkit.domain.model.BeanEntry;
import com.yamltestkit.domain.model.BodyMode;
import com.yamltestkit.domain.model.BrokenCase;
import com.yamltestkit.domain.model.Call;
import com.yamltestkit.domain.model.CallKind;
import com.yamltestkit.domain.model.CaseDefinitionException;
import com.yamltestkit.domain.model.CaseEntry;
import com.yamltestkit.domain.model.CaseFile;
import com.yamltestkit.domain.model.CaseSource;
import com.yamltestkit.domain.model.MethodRef;
import com.yamltestkit.domain.model.Query;
import com.yamltestkit.domain.model.Step;
import com.yamltestkit.domain.model.Stub;
import com.yamltestkit.domain.model.StubAnswer;
import com.yamltestkit.domain.model.TextOperator;
import com.yamltestkit.domain.model.VerifyCheck;
import com.yamltestkit.domain.model.YamlCase;
import org.assertj.core.groups.Tuple;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.net.URI;
import java.util.List;
import java.util.Map;

import static com.yamltestkit.domain.json.JsonTestFixtures.json;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class CaseFileLoaderTest {

    private static final String ROOT = "com/yamltestkit/loader/";
    private static final JsonNode REQUEST_JSON = json("{\"items\": [1, 2]}");
    private static final JsonNode EXPECTED_JSON = json("{\"ads\": [{\"id\": 7}]}");

    private SchemaValidator mockSchemaValidator;

    private CaseFileLoader underTest;

    @BeforeEach
    void setUp() {
        mockSchemaValidator = mock(SchemaValidator.class);
        underTest = new CaseFileLoader(mockSchemaValidator);
    }

    @Test
    void load_whenFileIsValid_shouldValidateItAgainstTheCaseFileSchema() {
        CaseResource resource = resource("schema/typo.yaml");
        JsonNode root = YamlFiles.read(resource.resource(), "typo.yaml");

        underTest.load(resource);

        verify(mockSchemaValidator).validate(root, SchemaValidator.FileKind.CASE_FILE, ROOT + "schema/typo.yaml");
    }

    @Test
    void load_whenSchemaValidationFails_shouldReturnOneBrokenEntryWithTheValidationMessage() {
        CaseResource resource = resource("schema/typo.yaml");
        JsonNode root = YamlFiles.read(resource.resource(), "typo.yaml");
        doThrow(new CaseDefinitionException("typo.yaml: property 'expct' is not defined"))
                .when(mockSchemaValidator)
                .validate(root, SchemaValidator.FileKind.CASE_FILE, ROOT + "schema/typo.yaml");

        CaseFile result = underTest.load(resource);

        assertThat(result.cases()).singleElement()
                .isEqualTo(new BrokenCase(CaseFileLoader.INVALID_FILE, resource.source(),
                        "typo.yaml: property 'expct' is not defined"));
    }

    @Test
    void load_whenYamlIsInvalid_shouldReturnBrokenEntryNamingTheFile() {
        CaseFile result = underTest.load(resource("schema/invalid-yaml.yaml"));

        assertThat(brokenReason(result.cases().getFirst()))
                .startsWith(ROOT + "schema/invalid-yaml.yaml: invalid YAML");
        verify(mockSchemaValidator, never()).validate(any(), any(), any());
    }

    @Test
    void load_whenAKeyIsRepeated_shouldRejectTheFileNamingTheKey() {
        CaseFile result = underTest.load(resource("schema/duplicate-key.yaml"));

        assertThat(result.cases()).singleElement().extracting(CaseFileLoaderTest::brokenReason).asString()
                .startsWith(ROOT + "schema/duplicate-key.yaml: invalid YAML")
                .contains("Duplicate field 'method'");
        verify(mockSchemaValidator, never()).validate(any(), any(), any());
    }

    @Test
    void load_whenAnAliasHasNoAnchor_shouldRejectTheFileNamingTheAliasAndItsLine() {
        CaseFile result = underTest.load(resource("schema/unknown-alias.yaml"));

        assertThat(result.cases()).singleElement().extracting(CaseFileLoaderTest::brokenReason).asString()
                .startsWith(ROOT + "schema/unknown-alias.yaml: invalid YAML")
                .contains("unknown alias '*nowhere'", "line: 5, column: 11");
        verify(mockSchemaValidator, never()).validate(any(), any(), any());
    }

    @Test
    void load_whenDefaultsMergeReturnsIntoACaseThatExpectsThrows_shouldRejectThatCase() {
        CaseFile result = underTest.load(resource("rules/returns-and-throws.yaml"));

        assertOutcomes(result.cases(),
                "returns: valid",
                "throws: step 'main' (class call): `returns` and `throws` exclude each other; one of them may come from"
                        + " the file's `caseDefaults`");
    }

    @Test
    void load_whenACallHasTwoKinds_shouldBreakOnlyThatCaseNamingTheConflictingKeys() {
        CaseFile result = underTest.load(resource("rules/two-call-kinds.yaml"));

        assertOutcomes(result.cases(),
                "valid call: valid",
                "two kinds in one call: step 'main': call has conflicting keys [http, bean]; exactly one of http, bean,"
                        + " class is allowed",
                "two kinds in a step's call: step 'second': call has conflicting keys [bean, class]");
        assertThat(brokenReason(result.cases().get(1))).doesNotContain("caseDefaults");
    }

    @Test
    void load_whenTheDefaultCallHasTwoKinds_shouldBreakEveryCaseOfTheFile() {
        CaseFile result = underTest.load(resource("case-defaults/two-kinds-in-case-defaults.yaml"));

        assertOutcomes(result.cases(),
                "first: step 'main': call has conflicting keys [http, bean]; exactly one of http, bean, class is"
                        + " allowed (one of them may come from the file's `caseDefaults`)",
                "second: step 'main': call has conflicting keys [http, bean]");
    }

    @Test
    void load_whenPayloadsReferenceFiles_shouldResolveThemRelativeToTheCaseFile() {
        CaseFile result = underTest.load(resource("payload/payload-files.yaml"));

        Step step = singleStep(result.cases().getFirst());
        assertThat(((Call.Http) step.call()).body()).isEqualTo(REQUEST_JSON);
        assertThat(step.expect().body().expected()).isEqualTo(EXPECTED_JSON);
        assertThat(step.expect().body().mode()).isEqualTo(BodyMode.LENIENT);
        assertThat(step.expect().bodyText().matchers())
                .containsEntry(TextOperator.CONTAINS, List.of("needle", "inline"));
    }

    @Test
    void load_whenPayloadFileIsYaml_shouldResolveItsAliasesAndMergeKeys() {
        CaseFile result = underTest.load(resource("payload/yaml-payload.yaml"));

        Step step = singleStep(result.cases().getFirst());
        assertThat(((Call.Http) step.call()).body()).isEqualTo(json("""
                {"base": {"prompt": "running shoes", "ad_count": 2},
                 "intent": {"prompt": "running shoes", "ad_count": 1},
                 "copy": {"prompt": "running shoes", "ad_count": 2}}"""));
    }

    @Test
    void load_whenExactBodyComesFromAFile_shouldKeepTheRawText() {
        CaseFile result = underTest.load(resource("payload/payload-files.yaml"));

        Step step = singleStep(result.cases().get(1));
        assertThat(step.expect().body().mode()).isEqualTo(BodyMode.EXACT);
        assertThat(step.expect().body().expected()).isEqualTo(TextNode.valueOf("{ \"ads\" : [ ] }\n"));
    }

    @Test
    void load_whenBodyHasOptions_shouldBindModeAndIgnoredPaths() {
        CaseFile result = underTest.load(resource("payload/payload-files.yaml"));

        Step step = singleStep(result.cases().get(2));
        assertThat(step.expect().body().expected()).isEqualTo(EXPECTED_JSON);
        assertThat(step.expect().body().mode()).isEqualTo(BodyMode.STRICT);
        assertThat(step.expect().body().ignore()).containsExactly("$.requestId");
    }

    @Test
    void load_whenPayloadFileIsNotJson_shouldGiveItsText() {
        CaseFile result = underTest.load(resource("payload/payload-files.yaml"));

        Call.Bean call = (Call.Bean) singleStep(result.cases().get(3)).call();
        assertThat(call.args()).containsExactly(TextNode.valueOf("plain text\n"));
    }

    @Test
    void load_whenPayloadFileIsMissing_shouldBreakOnlyThatCaseNamingFileCaseAndPath() {
        CaseFile result = underTest.load(resource("payload/payload-files.yaml"));

        assertThat(result.cases()).hasSize(5);
        assertThat(result.cases().subList(0, 4)).allMatch(YamlCase.class::isInstance);
        assertThat(brokenReason(result.cases().get(4)))
                .contains(ROOT + "payload/payload-files.yaml", "case 'missing payload file'", "data/nope.json");
    }

    @Test
    void load_whenCaseHasSingleCall_shouldCreateTheImplicitMainStep() {
        CaseFile result = underTest.load(resource("payload/null-values.yaml"));

        YamlCase yamlCase = (YamlCase) result.cases().getFirst();
        assertThat(yamlCase.steps()).extracting(Step::name).containsExactly(Step.IMPLICIT_NAME);
        assertThat(yamlCase.kinds()).containsExactly(CallKind.BEAN);
    }

    @Test
    void load_whenYamlHoldsNull_shouldKeepItAsNull() {
        CaseFile result = underTest.load(resource("payload/null-values.yaml"));

        YamlCase yamlCase = (YamlCase) result.cases().getFirst();
        Step step = yamlCase.steps().getFirst();
        assertThat(((Call.Bean) step.call()).args()).containsExactly(NullNode.getInstance(), TextNode.valueOf("amazon"));
        assertThat(step.expect().returns()).isEqualTo(NullNode.getInstance());
        assertThat(yamlCase.stubs().mocks().getFirst().stubs().getFirst().answer())
                .isEqualTo(new StubAnswer.Returns(NullNode.getInstance()));
    }

    @Test
    void load_whenReturnsIsAbsent_shouldUseMissingNodeNotNull() {
        CaseFile result = underTest.load(resource("payload/payload-files.yaml"));

        assertThat(singleStep(result.cases().get(3)).expect().returns().isMissingNode()).isTrue();
    }

    @Test
    void load_whenFileHasDefaults_shouldMergeThemIntoEachCaseWithCaseValuesWinning() {
        CaseFile result = underTest.load(resource("case-defaults/case-defaults-merge.yaml"));

        Step inherited = singleStep(result.cases().get(0));
        Step overridden = singleStep(result.cases().get(1));
        Call.Http inheritedCall = (Call.Http) inherited.call();
        Call.Http overriddenCall = (Call.Http) overridden.call();
        assertThat(inheritedCall.path()).isEqualTo("/is");
        assertThat(inheritedCall.query()).isEqualTo(new Query.Params(Map.of("plid", List.of("123"))));
        assertThat(inherited.expect().status()).isEqualTo(200);
        assertThat(overriddenCall.query())
                .isEqualTo(new Query.Params(Map.of("plid", List.of("123"), "qt", List.of("shoes"))));
        assertThat(overriddenCall.headers()).containsEntry("x-version", "3.0");
        assertThat(overridden.expect().status()).isEqualTo(204);
        assertThat(overridden.expect().body().expected()).isEqualTo(json("{\"other\": true}"));
    }

    @Test
    void load_whenFileHasDefaultStubs_shouldKeepThemAsTheirOwnLayer() {
        CaseFile result = underTest.load(resource("case-defaults/case-defaults-merge.yaml"));

        YamlCase inherited = (YamlCase) result.cases().get(0);
        YamlCase overridden = (YamlCase) result.cases().get(1);
        assertThat(inherited.stubs().isEmpty()).isTrue();
        assertThat(returnedId(inherited.fileDefaults().mocks().getFirst())).isEqualTo(1);
        assertThat(returnedId(overridden.fileDefaults().mocks().getFirst())).isEqualTo(1);
        assertThat(returnedId(overridden.stubs().mocks().getFirst())).isEqualTo(2);
    }

    @Test
    void load_whenDefaultCallKindDiffersFromTheCase_shouldReportConflictingKeys() {
        CaseFile result = underTest.load(resource("case-defaults/case-defaults-conflict.yaml"));

        assertThat(brokenReason(result.cases().getFirst()))
                .contains("case 'bean call over an http default'", "conflicting keys [http, bean]");
    }

    @Test
    void load_whenACaseAddsAFieldOfAnotherKindToTheDefaultCall_shouldBreakThatCase() {
        CaseFile result = underTest.load(resource("case-defaults/case-defaults-conflict.yaml"));

        assertThat(brokenReason(result.cases().get(1))).isEqualTo(ROOT + "case-defaults/case-defaults-conflict.yaml,"
                + " case 'deps over an http default': step 'main': call has `deps`, which does not apply to http"
                + " calls (it may come from the file's `caseDefaults`); http calls take query, rawQuery, headers,"
                + " body");
    }

    @Test
    void load_whenACaseSetsTheDefaultHttpToNullBesideABean_shouldReportConflictingKeys() {
        CaseFile result = underTest.load(resource("case-defaults/case-defaults-conflict.yaml"));

        assertThat(brokenReason(result.cases().get(2))).contains("case 'null http beside a bean over an http default'",
                "step 'main': call has conflicting keys [http, bean]");
    }

    @Test
    void load_whenTheDefaultsGiveTheKindAndTheCaseTheMethod_shouldBindTheMergedCall() {
        CaseFile result = underTest.load(resource("rules/returns-and-throws.yaml"));

        Call.Unit call = (Call.Unit) singleStep(result.cases().getFirst()).call();
        assertThat(call.className()).isEqualTo("com.example.Unit");
        assertThat(call.method().name()).isEqualTo("run");
    }

    @Test
    void load_whenDefaultQueryMeetsACaseRawQuery_shouldBreakThatCase() {
        CaseFile result = underTest.load(resource("case-defaults/query-conflict.yaml"));

        assertThat(brokenReason(result.cases().getFirst())).isEqualTo(ROOT + "case-defaults/query-conflict.yaml, case"
                + " 'rawQuery over a default query': step 'main': call has both `query` and `rawQuery` (one of them"
                + " may come from the file's `caseDefaults`); use one of them");
    }

    @Test
    void load_whenSectionsDoNotApplyToTheCallKind_shouldBreakThoseCases() {
        CaseFile result = underTest.load(resource("rules/kind-rules.yaml"));

        assertOutcomes(result.cases(),
                "class case with mocks: `mocks` / `spies` / `http` is not allowed in a case whose calls are all `class`",
                "class case with context: `context` is not allowed in a case whose calls are all `class`",
                "class case with freshContext: `freshContext` is not allowed in a case whose calls are all `class`",
                "class step with http stub: step 'only': `mocks` / `spies` / `http` is not allowed",
                "returns on http call: step 'main' (http call): `returns` / `throws` apply only to bean and class calls",
                "status on bean call: step 'main' (bean call): `status` / `headers` / `body` / `bodyText` apply only",
                "verify bean in class call: step 'main' (class call): verify on bean 'auditService'",
                "verify dep in http call: step 'main' (http call): verify on dep 'converter'",
                "mixed case may stub beans: valid",
                "http without a path: step 'main': call.http `GET` is not \"<METHOD> <path>\"",
                "http with an unknown method: step 'main': call.http `FETCH /is` is not \"<METHOD> <path>\"",
                "http with a query in the path: step 'main': call.http `GET /a?b=1` is not \"<METHOD> <path>\"",
                "http with a trailing space: step 'main': call.http `GET /ads ` is not \"<METHOD> <path>\"",
                "http with a lowercase method: step 'main': call.http `get /ads` is not \"<METHOD> <path>\"",
                "deps on a bean call: step 'main': call has `deps`, which does not apply to bean calls",
                "body on a class call: step 'main': call has `body`, which does not apply to class calls; class calls"
                        + " take method, paramTypes, args, constructor, deps",
                "query on a bean call: step 'main': call has `query`, which does not apply to bean calls",
                "call without a kind: step 'main': call has none of http, bean, class; exactly one is required",
                "bean call without a method: step 'main': call (bean) has no `method`",
                "step call without a kind: step 'only': call has none of http, bean, class");
    }

    @Test
    void load_whenNamesRepeat_shouldBreakTheDuplicates() {
        CaseFile result = underTest.load(resource("rules/duplicate-names.yaml"));

        assertOutcomes(result.cases(),
                "same: valid",
                "same: duplicate case name in this file",
                "duplicate steps: duplicate step name 'a'");
    }

    @Test
    void load_whenReferencesPointBackwards_shouldAcceptThem() {
        CaseFile result = underTest.load(resource("references/references.yaml"));

        assertThat(result.cases().getFirst()).isInstanceOf(YamlCase.class);
    }

    @Test
    void load_whenReferencesPointForwardOrNowhere_shouldNameTheReference() {
        CaseFile result = underTest.load(resource("references/references.yaml"));

        assertOutcomes(result.cases().subList(1, 4),
                "forward step reference: step 'first': forward reference 'second' (defined later, as step 'second')",
                "unknown reference: step 'main': unknown reference 'nowhere'",
                "capture used before it is made: step 'main': forward reference 'dsp'");
    }

    @Test
    void load_whenCaseUsesTheFullFormat_shouldBindStubsVerificationsAndPolling() {
        CaseFile result = underTest.load(resource("format/full-format.yaml"));

        assertThat(result.cases()).allMatch(YamlCase.class::isInstance);
        YamlCase httpCase = (YamlCase) result.cases().getFirst();
        assertThat(httpCase.context()).isEqualTo("ebay-off");
        assertThat(httpCase.freshContext()).isTrue();
        assertThat(httpCase.stubs().mocks()).extracting(BeanEntry::bean)
                .containsExactly("placementAccess", "providers", "enricher", "partnerApi");
        assertThat(httpCase.stubs().mocks().get(2).stubs().getFirst().args()).containsExactly(new ArgMatcher.Any());
        assertThat(httpCase.stubs().http().get(1).sequence()).isTrue();
        assertThat(httpCase.steps().get(1).expect().polling().within()).hasSeconds(30);
        YamlCase unitCase = (YamlCase) result.cases().get(2);
        assertThat(unitCase.context()).isNull();
        assertThat(((Call.Unit) unitCase.steps().getFirst().call()).deps())
                .containsOnlyKeys("CtaIdConverter", "limits", "clock");
    }

    @Test
    void load_whenHeaderNamesAreInMixedCase_shouldBindThemInLowercase() {
        CaseFile result = underTest.load(resource("format/header-names.yaml"));

        YamlCase mixedCase = (YamlCase) result.cases().getFirst();
        assertThat(mixedCase.stubs().http().getFirst().request().headers()).containsExactly(Map.entry("x-key", "abc"));
        VerifyCheck.OnService check = (VerifyCheck.OnService) singleStep(mixedCase).expect().verify().getFirst();
        assertThat(check.request().headers()).containsExactly(Map.entry("content-type", "application/json"));
    }

    @Test
    void load_whenAHeaderNameRepeatsInOtherLetterCase_shouldBreakThatCase() {
        CaseFile result = underTest.load(resource("format/header-names.yaml"));

        assertThat(brokenReason(result.cases().get(1))).isEqualTo(ROOT + "format/header-names.yaml, case 'one header"
                + " name in two spellings': header 'x-key' is given twice; header names are case-insensitive");
    }

    @Test
    void load_whenDefineHoldsNestedAnchors_shouldResolveTheAliasesAndMergeKeysOfTheCases() {
        CaseFile result = underTest.load(resource("define/define-in-list.yaml"));

        Step alias = singleStep(result.cases().get(0));
        Step merge = singleStep(result.cases().get(1));
        assertThat(((Call.Http) alias.call()).body()).isEqualTo(json("""
                {"intent": {"prompt": "running shoes", "ad_count": 2}, "meta": {"request_id": "req-1"}}"""));
        assertThat(((Call.Http) merge.call()).body()).isEqualTo(json("""
                {"intent": {"prompt": "running shoes", "ad_count": 1}, "meta": {"request_id": "req-1"}}"""));
        assertThat(alias.expect().status()).isEqualTo(200);
        assertThat(merge.expect().status()).isEqualTo(200);
        assertThat(merge.expect().headers()).containsExactly(Map.entry("Content-Type", "application/json"));
    }

    @Test
    void load_whenFileHasDefine_shouldLoadTheSameCasesAsTheFileWithTheFragmentsWrittenOut() {
        CaseFile withDefine = underTest.load(resource("define/define-in-list.yaml"));
        CaseFile writtenOut = underTest.load(resource("define/inline.yaml"));

        assertThat(withDefine.cases()).hasSize(2).allMatch(YamlCase.class::isInstance);
        assertThat(withoutSource(withDefine)).isEqualTo(withoutSource(writtenOut));
    }

    @Test
    void load_whenSingleCaseFileHasDefine_shouldBindTheCaseWithTheMergedFragment() {
        CaseFile result = underTest.load(resource("define/define-in-single-case.yaml"));

        YamlCase yamlCase = (YamlCase) result.cases().getFirst();
        assertThat(yamlCase.name()).isEqualTo("single case with define");
        assertThat(yamlCase.stubs().mocks()).singleElement().isEqualTo(new BeanEntry("placementAccess",
                List.of(new Stub(new MethodRef("getPlacement", null), null,
                        new StubAnswer.Returns(json("{\"id\": 1}"))))));
    }

    private static CaseResource resource(String relativePath) {
        CaseSource source = new CaseSource(URI.create("classpath:/" + ROOT + relativePath), ROOT, relativePath);
        return new CaseResource(new ClassPathResource(ROOT + relativePath), source);
    }

    /** Each case's content without its source, which differs between two files. */
    private static List<Tuple> withoutSource(CaseFile file) {
        return file.cases().stream()
                .map(YamlCase.class::cast)
                .map(yamlCase -> tuple(yamlCase.name(), yamlCase.steps(), yamlCase.fileDefaults(), yamlCase.stubs(),
                        yamlCase.context(), yamlCase.freshContext()))
                .toList();
    }

    private static Step singleStep(CaseEntry entry) {
        return ((YamlCase) entry).steps().getFirst();
    }

    private static String brokenReason(CaseEntry entry) {
        return ((BrokenCase) entry).reason();
    }

    private static String outcome(CaseEntry entry) {
        return switch (entry) {
            case YamlCase yamlCase -> yamlCase.name() + ": valid";
            case BrokenCase broken -> {
                String prefix = ", case '" + broken.name() + "': ";
                String reason = broken.reason().substring(broken.reason().indexOf(prefix) + prefix.length());
                yield broken.name() + ": " + reason;
            }
        };
    }

    /** Each case's outcome ("name: valid" or "name: reason") starts with the expected text, in order. */
    private static void assertOutcomes(List<CaseEntry> entries, String... expectedPrefixes) {
        assertThat(entries).extracting(CaseFileLoaderTest::outcome).hasSize(expectedPrefixes.length);
        for (int i = 0; i < expectedPrefixes.length; i++) {
            assertThat(outcome(entries.get(i))).startsWith(expectedPrefixes[i]);
        }
    }

    private static int returnedId(BeanEntry entry) {
        return ((StubAnswer.Returns) entry.stubs().getFirst().answer()).value().get("id").asInt();
    }
}
