package com.yamltestkit.selftest;

import com.yamltestkit.domain.model.CallKind;
import com.yamltestkit.domain.model.CaseEntry;
import com.yamltestkit.engine.CaseResult;
import com.yamltestkit.engine.SuiteSettings;
import com.yamltestkit.engine.YamlCaseEngine;
import com.yamltestkit.infrastructure.yaml.CaseFileLoader;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Negative self-tests (spec: Engine self-test suite; design D15): every case under {@code selftest/negative/} is broken
 * on purpose, and running it through {@link YamlCaseEngine} must fail with a message that names its file, its case and
 * the specific cause — the operator with the expected and actual values, the verification count, or the validation
 * error. A check whose implementation always passed would make its case pass here, and this test fail. Failsafe runs
 * it because most cases start the fixture application.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class NegativeCasesIT {

    private static final String ROOT = "com/yamltestkit/selftest/negative/";
    private static final String CONTEXT_FILE = ROOT + "_context.yaml";

    private YamlCaseEngine engine;
    private Map<String, CaseEntry> cases;

    @BeforeAll
    void startEngine() {
        engine = YamlCaseEngine.create(new SuiteSettings(List.of(ROOT + "**"), EnumSet.allOf(CallKind.class),
                List.of(), null));
        cases = engine.plan(engine.load()).files().stream()
                .flatMap(file -> file.cases().stream())
                .collect(Collectors.toMap(CaseEntry::casePath, Function.identity()));
    }

    @AfterAll
    void closeEngine() {
        engine.close();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("negativeCases")
    void run_whenTheCaseIsBroken_shouldFailNamingItsFileCaseAndCause(String casePath, List<String> cause) {
        CaseEntry entry = cases.get(casePath);
        assertThat(entry).as("negative case %s", casePath).isNotNull();

        CaseResult result = engine.run(entry);

        assertThat(result.failure()).as("failure of %s", casePath).isNotNull();
        assertThat(result.failure().getMessage()).contains(location(entry)).contains(cause);
    }

    @Test
    void load_whenTheNegativeFolderIsRead_shouldHaveAnExpectedFailureForEveryCase() {
        List<String> expected = negativeCases().map(arguments -> (String) arguments.get()[0]).toList();

        assertThat(cases.keySet()).containsExactlyInAnyOrderElementsOf(expected);
    }

    /** A case's file and name, as failure messages give them; a file that failed to load is named by its path. */
    private static String location(CaseEntry entry) {
        String label = entry.source().label();
        return CaseFileLoader.INVALID_FILE.equals(entry.name()) ? label : label + ", case '" + entry.name() + "'";
    }

    private static Stream<Arguments> negativeCases() {
        return Stream.of(assertOperators(), bodyAndText(), verification(), logsAndContext(), throwsAndPolling(),
                loading(), contextSetup(), unitCalls()).flatMap(Function.identity());
    }

    private static Stream<Arguments> assertOperators() {
        return Stream.of(
                negative("assert/operators.yaml/equals",
                        "assert `$.ads[0].title` equals: expected \"Green shoes\" but was \"Red shoes\""),
                negative("assert/operators.yaml/notEquals",
                        "assert `$.ads[0].title` notEquals: expected a value other than \"Red shoes\""),
                negative("assert/operators.yaml/isNull",
                        "assert `$.ads[1].asin` isNull: expected null or a missing key, but was \"B00SHOES\""),
                negative("assert/operators.yaml/notNull",
                        "assert `$.ads[0].gtin` notNull: expected a present non-null value, but it is null"),
                negative("assert/operators.yaml/exists",
                        "assert `$.ads[0].asin` exists: expected the key to exist, but it is missing"),
                negative("assert/operators.yaml/absent on a key present with null",
                        "assert `$.ads[0].gtin` absent: expected the key to be absent, but it is present with value"
                                + " null"),
                negative("assert/operators.yaml/size", "assert `$.ads` size: expected size 3 but was 2"),
                negative("assert/operators.yaml/gte",
                        "assert `$.ads[1].price` gte: expected a number >= 100 but was 80"),
                negative("assert/operators.yaml/lte",
                        "assert `$.ads[0].price` lte: expected a number <= 100 but was 120"),
                negative("assert/operators.yaml/contains",
                        "assert `$.ads[*].title` contains: expected an array containing [\"Green shoes\"] but was"
                                + " [\"Red shoes\",\"Blue shoes\"]"),
                negative("assert/operators.yaml/containsExactly",
                        "assert `$.ads[*].id` containsExactly: expected exactly [\"p2\",\"p1\"] in this order but was"
                                + " [\"p1\",\"p2\"]"),
                negative("assert/operators.yaml/containsInAnyOrder",
                        "assert `$.ads[*].id` containsInAnyOrder: expected [\"p1\",\"p3\"] in any order but was"
                                + " [\"p1\",\"p2\"] (missing [\"p3\"], unexpected [\"p2\"])"),
                negative("assert/operators.yaml/unique",
                        "assert `$.ads[*].section` unique: expected unique values but found duplicates [\"top\"]"),
                negative("assert/operators.yaml/startsWith",
                        "assert `$.requestId` startsWith: expected a string starting with \"request-\" but was \"req-"),
                negative("assert/operators.yaml/matches",
                        "assert `$.requestId` matches: expected a string matching /req-[a-z]+/ but was \"req-"),
                negative("assert/operators.yaml/all", "assert `$.ads` all: element [0]",
                        "fails `$.asin` exists: expected the key to exist, but it is missing"),
                negative("assert/operators.yaml/none", "assert `$.ads` none: element [1]",
                        "satisfies `$.asin` [exists]"),
                negative("assert/operators.yaml/parseJson",
                        "assert `$.diag[0].payload` equals: $.dropped: expected 1 elements but was 0",
                        "actual: {\"dropped\":[]}"),
                negative("assert/operators.yaml/path prefix missing",
                        "assert `$.adz[*].qt` size: path prefix `$['adz']` missing"));
    }

    private static Stream<Arguments> bodyAndText() {
        return Stream.of(
                negative("assert/body.yaml/status", "status expected 201 but was 200"),
                negative("assert/body.yaml/response header",
                        "header Content-Type expected \"text/plain\" but was \"application/json\""),
                negative("assert/body.yaml/body default mode",
                        "body (lenient) differs: $.items[0].id: expected \"fallback-2\" but was \"fallback-1\""),
                negative("assert/body.yaml/body strict mode",
                        "body (strict) differs: $.items[0].title: unexpected field with value \"Running shoes\""),
                negative("assert/body.yaml/body exact mode", "body (exact) differs"),
                negative("assert/body.yaml/body ignore keeps other differences",
                        "body (lenient) differs: $.version: expected \"9.9\" but was \"3.0\""),
                negative("assert/body.yaml/bodyText equals", "bodyText equals \"{\"version\":\"3.0\"}\" does not hold"),
                negative("assert/body.yaml/bodyText contains", "bodyText contains \"asin\" does not hold"),
                negative("assert/body.yaml/bodyText notContains",
                        "bodyText notContains \"Running shoes\" does not hold"),
                negative("assert/body.yaml/bodyText startsWith", "bodyText startsWith \"[\" does not hold"),
                negative("assert/body.yaml/bodyText matches", "bodyText matches \"^\\[.*\\]$\" does not hold"));
    }

    private static Stream<Arguments> verification() {
        return Stream.of(
                negative("verify/checks.yaml/times",
                        "verify[0] bean 'dspClient' requestAds: expected times: 2, actual: 1"),
                negative("verify/checks.yaml/atLeast",
                        "verify[0] bean 'dspClient' requestAds: expected atLeast: 3, actual: 1"),
                negative("verify/checks.yaml/never",
                        "verify[0] bean 'placementAccess' getPlacement: expected none (never), actual: 1",
                        "calls of the mock: [getPlacement[14019]]"),
                negative("verify/checks.yaml/noInteractions",
                        "verify[0] bean 'providerRegistry': expected none (noInteractions), actual: 1",
                        "calls of the mock: [find[dsp]]"),
                negative("verify/checks.yaml/args",
                        "verify[0] bean 'placementAccess' getPlacement: expected times: 1, actual: 0",
                        "calls of the mock: [getPlacement[14019]]"),
                negative("verify/checks.yaml/argType",
                        "verify[0] bean 'auditService' recordResponse: expected times: 1, actual: 0",
                        "calls of the mock: [recordResponse[/ads, 2]]"),
                negative("verify/checks.yaml/within timeout",
                        "verify[0] bean 'eventLogger' send: expected times: 2, actual: 1",
                        "(still failing after waiting 300 ms, within)"),
                negative("verify/checks.yaml/assert on captured calls",
                        "verify[0] bean 'dspClient' requestAds: assert `$[0].args[0].maxAds` equals: expected 9 but"
                                + " was 4"),
                negative("verify/checks.yaml/WireMock count",
                        "verify[0] service 'tracking': expected times: 2, actual: 1 matching requests; requests"
                                + " received: [POST /urls]"));
    }

    private static Stream<Arguments> logsAndContext() {
        String feedCache = "com.yamltestkit.selftest.app.FeedCache";
        String captured = "captured events of " + feedCache + ": [WARN " + feedCache + ": unmappable feed row"
                + " {name=feed-42}]";
        return Stream.of(
                negative("expect/logs-and-context.yaml/expected log event missing",
                        "logs: expected WARN event of " + feedCache + " containing \"feed-99\" but none was logged",
                        captured),
                negative("expect/logs-and-context.yaml/log event count differs",
                        "logs: expected times: 2 of WARN event of " + feedCache + " containing \"feed-42\", actual: 1",
                        captured),
                negative("expect/logs-and-context.yaml/log event expected absent",
                        "logs: expected no WARN event of " + feedCache + " (absent) but 1 were logged", captured),
                negative("expect/logs-and-context.yaml/bean expected present",
                        "context: expected bean 'ebayConnector' but the context has no bean of that name"),
                negative("expect/logs-and-context.yaml/bean type expected absent",
                        "context: expected no bean of type " + feedCache + " but the context has [feedCache]"));
    }

    private static Stream<Arguments> throwsAndPolling() {
        return Stream.of(
                negative("expect/throws-and-polling.yaml/throws with a wrong type",
                        "throws: expected IllegalArgumentException but java.lang.IllegalStateException was thrown:"
                                + " db down"),
                negative("expect/throws-and-polling.yaml/throws with a wrong message",
                        "throws: expected message \"db up\" but was \"db down\""),
                negative("expect/throws-and-polling.yaml/step-level within times out",
                        "still failing after 300 ms (", "last failure: ", "returns differs: expected 5 but was 4"));
    }

    private static Stream<Arguments> loading() {
        return Stream.of(
                negative("validation/two-call-kinds.yaml/http and bean in one call",
                        "step 'main': call has conflicting keys [http, bean]; exactly one of http, bean, class is"
                                + " allowed"),
                negative("validation/malformed-http.yaml/" + CaseFileLoader.INVALID_FILE,
                        "does not match the YAML Testkit schema", "/call/http",
                        "(schema yaml-testkit.schema.json#/$defs/call/properties/http/pattern)"),
                negative("validation/call-shape.yaml/deps on a bean call",
                        "step 'main': call has `deps`, which does not apply to bean calls; bean calls take method,"
                                + " paramTypes, args"),
                negative("validation/call-shape.yaml/body on a class call",
                        "step 'main': call has `body`, which does not apply to class calls"),
                negative("validation/call-shape.yaml/call without a kind",
                        "step 'main': call has none of http, bean, class; exactly one is required"),
                negative("validation/schema-typo.yaml/" + CaseFileLoader.INVALID_FILE,
                        "does not match the YAML Testkit schema",
                        "property 'expct' is not defined in the schema",
                        "(schema yaml-testkit.schema.json#/$defs/caseObject/additionalProperties)"),
                negative("validation/case-rules.yaml/mocks in a class case",
                        "`mocks` / `spies` / `http` is not allowed in a case whose calls are all `class`"),
                negative("validation/case-rules.yaml/returns on an http call",
                        "step 'main' (http call): `returns` / `throws` apply only to bean and class calls"),
                negative("validation/case-rules.yaml/forward reference",
                        "step 'first': forward reference 'second' (defined later, as step 'second')"),
                negative("validation/case-rules.yaml/unknown reference",
                        "step 'main': unknown reference 'nowhere'; references available here: [main]"),
                negative("validation/case-rules.yaml/missing payload file",
                        "payload file not found: data/no-such-file.json (relative to the case file)"));
    }

    private static Stream<Arguments> contextSetup() {
        String conflict = "bean 'modeService' is both mocked (in " + ROOT + "validation/mock-spy-conflict-mock.yaml)"
                + " and spied (in " + ROOT + "validation/mock-spy-conflict-spy.yaml) in " + ROOT
                + " variant 'mock-spy-conflict'";
        return Stream.of(
                negative("validation/context-rules.yaml/unknown variant",
                        "unknown context variant 'ebay-of'; " + CONTEXT_FILE
                                + " declares [mock-spy-conflict, unknown-bean]"),
                negative("validation/context-rules.yaml/unknown service",
                        "unknown service 'geo'; " + CONTEXT_FILE + " declares [partner, tracking]"),
                negative("validation/context-rules.yaml/verify on a bean neither mocked nor spied",
                        "verify on bean 'modeService', which " + ROOT + " variant 'default' neither mocks nor spies;"
                                + " add it to `spies` (or `spyBeans` in " + CONTEXT_FILE + ")"),
                negative("validation/context-rules.yaml/log level filtered by configuration",
                        "logs: DEBUG events of com.yamltestkit.selftest.app.FeedCache are never logged: the"
                                + " logger's effective level is INFO",
                        "set `logging.level.com.yamltestkit.selftest.app.FeedCache: DEBUG`"),
                negative("validation/unknown-bean.yaml/unknown bean",
                        "the application context failed to start",
                        "can't mock bean 'campainClient': the context has no bean of that name (referenced by " + ROOT
                                + "validation/unknown-bean.yaml)"),
                negative("validation/mock-spy-conflict-mock.yaml/mocks the bean", conflict),
                negative("validation/mock-spy-conflict-spy.yaml/spies the bean", conflict));
    }

    private static Stream<Arguments> unitCalls() {
        return Stream.of(
                negative("validation/unit-calls.yaml/ambiguous method",
                        "method 'greet' of com.yamltestkit.selftest.unit.Greeter is ambiguous; add `paramTypes`"
                                + " to choose one of: greet(String), greet(String, int)"),
                negative("validation/unit-calls.yaml/several constructors",
                        "com.yamltestkit.selftest.unit.Greeter has 2 constructors; choose one with `constructor`:"
                                + " Greeter(), Greeter(String)"),
                negative("validation/unit-calls.yaml/unknown dep",
                        "deps 'clock' matches no constructor parameter of"
                                + " com.yamltestkit.selftest.unit.AdTitleFormatter by name or type; parameters:"
                                + " ctaIds (CtaIdConverter), limits (Limits)"),
                negative("validation/unit-calls.yaml/ambiguous dep type",
                        "deps 'Limits' is ambiguous: parameters lower and upper both have type Limits"),
                negative("validation/unit-calls.yaml/no matching exception constructor",
                        "no public constructor of com.yamltestkit.selftest.unit.ApiException fits"
                                + " [\"x\", \"y\", \"z\"]; constructors: ApiException(Throwable), ApiException(int,"
                                + " String)"),
                negative("validation/unit-calls.yaml/unconvertible value",
                        "can't convert YAML {\"max\":\"lots\"} to com.yamltestkit.selftest.unit.Limits",
                        "deps.limits.value", "converters tried: "),
                negative("validation/unit-calls.yaml/returns", "returns differs: expected \"blue-tent\" but was"
                        + " \"red-tent\""),
                negative("validation/unit-calls.yaml/throws expected but returned",
                        "throws: expected IllegalArgumentException to be thrown, but no exception was thrown (returned"
                                + " \"red-tent\")"));
    }

    private static Arguments negative(String casePath, String... cause) {
        return Arguments.of(casePath, List.of(cause));
    }
}
