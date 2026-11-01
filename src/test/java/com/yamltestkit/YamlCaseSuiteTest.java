package com.yamltestkit;

import com.yamltestkit.domain.model.CallKind;
import com.yamltestkit.domain.model.CaseDefinitionException;
import com.yamltestkit.engine.YamlCaseEngine;
import com.yamltestkit.junit.DynamicNodeTrees;
import org.junit.jupiter.api.DynamicContainer;
import org.junit.jupiter.api.DynamicNode;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.mockito.ArgumentCaptor;

import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The whole discovery chain over real fixture files: globs → files → plan → JUnit tree.
 */
class YamlCaseSuiteTest {

    private static final ExtensionContext.Namespace ENGINE_NAMESPACE = ExtensionContext.Namespace.create(
            YamlCaseSuite.EngineResolver.class);

    @Test
    void yamlCases_whenSuiteSelectsEveryKind_shouldBuildTheTreeFromTheFixtureFolder() {
        YamlCaseSuite underTest = new DiscoverySuite() {
        };

        Stream<DynamicNode> result = underTest.yamlCases(engine(underTest));

        assertThat(DynamicNodeTrees.describe(result)).map(YamlCaseSuiteTest::withoutUri).containsExactly(
                "a-folder/",
                "a-folder/nested/",
                "a-folder/nested/deep.yaml/",
                "a-folder/nested/deep.yaml/deep case",
                "a-folder/second.yaml/",
                "a-folder/second.yaml/third",
                "a-folder/second.yaml/first",
                "a-folder/second.yaml/second",
                "b-root.yaml/",
                "b-root.yaml/unit case",
                "b-root.yaml/http case");
    }

    @Test
    void yamlCases_whenSuiteSelectsClassCalls_shouldLeaveOutOtherCasesAndEmptyFiles() {
        YamlCaseSuite underTest = new UnitDiscoverySuite() {
        };

        Stream<DynamicNode> result = underTest.yamlCases(engine(underTest));

        assertThat(DynamicNodeTrees.describe(result)).map(YamlCaseSuiteTest::withoutUri).containsExactly(
                "a-folder/",
                "a-folder/second.yaml/",
                "a-folder/second.yaml/second",
                "b-root.yaml/",
                "b-root.yaml/unit case");
    }

    @Test
    void yamlCases_whenACaseFails_shouldRethrowItsFailureFromTheDynamicTest() {
        YamlCaseSuite underTest = new UnitDiscoverySuite() {
        };
        DynamicTest test = firstTest(underTest.yamlCases(engine(underTest)));

        assertThatThrownBy(() -> test.getExecutable().execute())
                .isInstanceOf(CaseDefinitionException.class)
                .hasMessageContaining("a-folder/second.yaml, case 'second', step 'main'")
                .hasMessageContaining("class com.example.Unit not found");
    }

    @Test
    void yamlCases_whenGlobMatchesNothing_shouldFailInsteadOfRunningZeroTests() {
        YamlCaseSuite underTest = new EmptySuite() {
        };

        assertThatThrownBy(() -> underTest.yamlCases(engine(underTest)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("com/yamltestkit/loader/no-such-folder/**");
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void resolveParameter_whenTheTestFactoryAsksForTheEngine_shouldKeepItInTheSuiteClassStore() throws Exception {
        ExtensionContext mockMethodContext = mock(ExtensionContext.class);
        ExtensionContext mockClassContext = mock(ExtensionContext.class);
        ExtensionContext.Store mockStore = mock(ExtensionContext.Store.class);
        YamlCaseEngine mockEngine = mock(YamlCaseEngine.class);
        ArgumentCaptor<Function> creator = ArgumentCaptor.forClass(Function.class);
        when(mockMethodContext.getTestMethod()).thenReturn(Optional.of(YamlCaseSuite.class.getDeclaredMethod(
                "yamlCases", YamlCaseEngine.class)));
        when(mockMethodContext.getParent()).thenReturn(Optional.of(mockClassContext));
        when(mockClassContext.getTestMethod()).thenReturn(Optional.empty());
        doReturn(DiscoverySuite.class).when(mockClassContext).getRequiredTestClass();
        when(mockClassContext.getStore(ENGINE_NAMESPACE)).thenReturn(mockStore);
        when(mockStore.computeIfAbsent(eq(DiscoverySuite.class), creator.capture(), eq(YamlCaseEngine.class)))
                .thenReturn(mockEngine);

        Object result = new YamlCaseSuite.EngineResolver().resolveParameter(mock(ParameterContext.class),
                mockMethodContext);

        assertThat(result).isSameAs(mockEngine);
        assertThat(creator.getValue()).isNotNull();
        verify(mockMethodContext, never()).getStore(any());
        verify(mockMethodContext, never()).getRoot();
    }

    private static YamlCaseEngine engine(YamlCaseSuite suite) {
        return YamlCaseEngine.create(YamlCasesReader.read(suite.getClass()));
    }

    private static String withoutUri(String line) {
        int uri = line.indexOf(" <");
        return uri < 0 ? line : line.substring(0, uri);
    }

    private static DynamicTest firstTest(Stream<? extends DynamicNode> nodes) {
        DynamicNode node = nodes.findFirst().orElseThrow();
        return node instanceof DynamicContainer container ? firstTest(container.getChildren()) : (DynamicTest) node;
    }

    /** Abstract so that JUnit never discovers it as a suite of its own. */
    @YamlCases("com/yamltestkit/loader/discovery/**")
    private abstract static class DiscoverySuite extends YamlCaseSuite {
    }

    @YamlCases(value = "com/yamltestkit/loader/discovery/**", kinds = CallKind.CLASS)
    private abstract static class UnitDiscoverySuite extends YamlCaseSuite {
    }

    @YamlCases("com/yamltestkit/loader/no-such-folder/**")
    private abstract static class EmptySuite extends YamlCaseSuite {
    }
}
