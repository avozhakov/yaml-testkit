package com.yamltestkit.infrastructure.yaml;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.IntNode;
import com.yamltestkit.domain.json.JsonMappers;
import com.yamltestkit.domain.model.BeanEntry;
import com.yamltestkit.domain.model.CaseDefinitionException;
import com.yamltestkit.domain.model.ContextFile;
import com.yamltestkit.domain.model.ContextVariant;
import com.yamltestkit.domain.model.MethodRef;
import com.yamltestkit.domain.model.MockBean;
import com.yamltestkit.domain.model.RootConfiguration;
import com.yamltestkit.domain.model.SpyBean;
import com.yamltestkit.domain.model.Stub;
import com.yamltestkit.domain.model.StubAnswer;
import com.yamltestkit.domain.model.StubSet;
import com.yamltestkit.domain.model.ValueBean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.core.io.DefaultResourceLoader;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class RootConfigurationLoaderTest {

    private static final String ROOT = "com/yamltestkit/loader/configuration/";
    private static final JsonNode ROWS = json("[ { \"id\": 1 } ]");
    private static final JsonNode SOURCES = json("{ \"sources\": [] }");

    private SchemaValidator mockSchemaValidator;

    private RootConfigurationLoader underTest;

    @BeforeEach
    void setUp() {
        mockSchemaValidator = mock(SchemaValidator.class);
        underTest = new RootConfigurationLoader(new DefaultResourceLoader(getClass().getClassLoader()),
                mockSchemaValidator);
    }

    @Test
    void load_whenBothFilesExist_shouldBindThemWithPayloadsRelativeToTheFile() {
        Map<String, RootConfiguration> result = underTest.load(List.of(ROOT + "full/**"));

        RootConfiguration root = result.get(ROOT + "full/");
        ContextFile context = root.context();
        assertThat(root.failure()).isNull();
        assertThat(context.label()).isEqualTo(ROOT + "full/_context.yaml");
        assertThat(context.application()).isEqualTo("com.example.App");
        assertThat(context.profiles()).containsExactly("selftest");
        assertThat(context.services()).containsExactly("partner");
        assertThat(context.converters()).containsExactly("com.yamltestkit.convert.OptionalConverter");
        assertThat(context.settleMs()).isEqualTo(50);
        assertThat(context.base()).isEqualTo(new ContextVariant(
                Map.of("spring.config.name", "selftest", "partner.base-url", "${wiremock.partner.url}",
                        "hosts[0]", "a", "hosts[1]", "b"),
                List.of(new MockBean("redisConnection", null, false, List.of()),
                        new MockBean("feedRepository", null, false, List.of(new Stub(new MethodRef("findAll", null),
                                null, new StubAnswer.Returns(ROWS)))),
                        new MockBean("jdbcTemplate", "com.example.Jdbc", true, List.of())),
                List.of(new SpyBean("auditService", List.of())),
                List.of(new ValueBean("exchangeSources", "com.example.Sources", SOURCES, true))));
        assertThat(context.variants()).containsExactly(Map.entry("eco", new ContextVariant(
                Map.of("fixture.mode", "eco"), List.of(),
                List.of(new SpyBean("metricService", List.of(new Stub(new MethodRef("count", null), null,
                        new StubAnswer.Returns(IntNode.valueOf(1)))))), List.of())));
        assertThat(root.defaults().mocks()).extracting(BeanEntry::bean).containsExactly("placementAccess");
        assertThat(root.defaults().http()).hasSize(1);
        assertThat(root.defaultsLabel()).isEqualTo(ROOT + "full/_defaults.yaml");
    }

    @Test
    void load_whenBothFilesExist_shouldValidateEachAgainstItsSchemaDefinition() {
        ArgumentCaptor<JsonNode> contextTree = ArgumentCaptor.forClass(JsonNode.class);
        ArgumentCaptor<JsonNode> defaultsTree = ArgumentCaptor.forClass(JsonNode.class);

        underTest.load(List.of(ROOT + "full/**"));

        verify(mockSchemaValidator).validate(contextTree.capture(), eq(SchemaValidator.FileKind.CONTEXT_FILE),
                eq(ROOT + "full/_context.yaml"));
        verify(mockSchemaValidator).validate(defaultsTree.capture(), eq(SchemaValidator.FileKind.DEFAULTS_FILE),
                eq(ROOT + "full/_defaults.yaml"));
        assertThat(contextTree.getValue().path("application").asText()).isEqualTo("com.example.App");
        assertThat(defaultsTree.getValue().path("mocks").path(0).path("bean").asText()).isEqualTo("placementAccess");
    }

    @Test
    void load_whenTheRootHasNoConfigurationFiles_shouldReturnAnEmptyConfiguration() {
        Map<String, RootConfiguration> result = underTest.load(List.of(ROOT + "missing/**"));

        assertThat(result).containsExactly(Map.entry(ROOT + "missing/", new RootConfiguration(ROOT + "missing/",
                null, StubSet.EMPTY, ROOT + "missing/_defaults.yaml", null)));
        verify(mockSchemaValidator, never()).validate(any(), any(), any());
    }

    @Test
    void load_whenTheContextFileFailsValidation_shouldKeepTheFailureForTheRoot() {
        ArgumentCaptor<JsonNode> contextTree = ArgumentCaptor.forClass(JsonNode.class);
        doThrow(new CaseDefinitionException(ROOT + "full/_context.yaml: does not match the YAML Testkit schema"))
                .when(mockSchemaValidator).validate(contextTree.capture(), eq(SchemaValidator.FileKind.CONTEXT_FILE),
                        eq(ROOT + "full/_context.yaml"));

        RootConfiguration result = underTest.load(List.of(ROOT + "full/**")).get(ROOT + "full/");

        assertThat(contextTree.getValue().path("application").asText()).isEqualTo("com.example.App");
        assertThat(result.context()).isNull();
        assertThat(result.failure()).isEqualTo(ROOT + "full/_context.yaml: does not match the YAML Testkit schema");
    }

    @Test
    void load_whenAConverterIsNotAValueConverter_shouldKeepTheFailureForTheRoot() {
        RootConfiguration result = underTest.load(List.of(ROOT + "bad-converter/**")).get(ROOT + "bad-converter/");

        assertThat(result.failure()).isEqualTo(ROOT + "bad-converter/_context.yaml: converters: java.lang.String"
                + " does not implement com.yamltestkit.convert.ValueConverter");
    }

    @Test
    void load_whenAPayloadFileIsMissing_shouldKeepTheFailureNamingTheFile() {
        RootConfiguration result = underTest.load(List.of(ROOT + "broken-payload/**")).get(ROOT + "broken-payload/");

        assertThat(result.failure()).isEqualTo(ROOT + "broken-payload/_context.yaml: payload file not found:"
                + " missing.json (relative to the case file)");
    }

    @Test
    void load_whenTwoGlobsShareARoot_shouldLoadItOnce() {
        ArgumentCaptor<JsonNode> contextTree = ArgumentCaptor.forClass(JsonNode.class);

        Map<String, RootConfiguration> result = underTest.load(List.of(ROOT + "full/**", ROOT + "full/*.yaml"));

        assertThat(result).containsOnlyKeys(ROOT + "full/");
        verify(mockSchemaValidator).validate(contextTree.capture(), eq(SchemaValidator.FileKind.CONTEXT_FILE),
                eq(ROOT + "full/_context.yaml"));
        assertThat(contextTree.getAllValues()).hasSize(1);
    }

    private static JsonNode json(String text) {
        try {
            return JsonMappers.plain().readTree(text);
        } catch (Exception e) {
            throw new IllegalArgumentException(e);
        }
    }
}
