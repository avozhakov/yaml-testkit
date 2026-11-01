package com.yamltestkit.junit;

import com.yamltestkit.domain.model.BrokenCase;
import com.yamltestkit.domain.model.CaseFile;
import com.yamltestkit.domain.model.CaseSource;
import com.yamltestkit.engine.CasePlan;
import com.yamltestkit.engine.CaseResult;
import com.yamltestkit.engine.YamlCaseEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DynamicContainer;
import org.junit.jupiter.api.DynamicNode;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.List;
import java.util.stream.Stream;

import static com.yamltestkit.junit.DynamicNodeTrees.describe;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CaseTreeBuilderTest {

    private static final CaseSource DEEP = source("cases/", "a-folder/nested/deep.yaml");
    private static final CaseSource SECOND = source("cases/", "a-folder/second.yaml");
    private static final CaseSource ROOT_FILE = source("cases/", "b-root.yaml");
    private static final CaseSource OTHER_ROOT_FILE = source("more/", "x.yaml");
    private static final BrokenCase DEEP_CASE = new BrokenCase("deep case", DEEP, "r");
    private static final BrokenCase THIRD = new BrokenCase("third", SECOND, "r");
    private static final BrokenCase FIRST = new BrokenCase("first", SECOND, "r");
    private static final BrokenCase ROOT_CASE = new BrokenCase("root case", ROOT_FILE, "r");
    private static final BrokenCase OTHER_CASE = new BrokenCase("other case", OTHER_ROOT_FILE, "r");
    private static final CasePlan PLAN = new CasePlan(List.of(
            new CaseFile(DEEP, List.of(DEEP_CASE)),
            new CaseFile(SECOND, List.of(THIRD, FIRST)),
            new CaseFile(ROOT_FILE, List.of(ROOT_CASE))));

    private YamlCaseEngine mockEngine;

    private CaseTreeBuilder underTest;

    @BeforeEach
    void setUp() {
        mockEngine = mock(YamlCaseEngine.class);
        underTest = new CaseTreeBuilder(mockEngine);
    }

    @Test
    void build_whenFilesSitInFolders_shouldNestFolderFileAndCaseInFileOrder() {
        Stream<DynamicNode> result = underTest.build(PLAN);

        assertThat(describe(result)).containsExactly(
                "a-folder/",
                "a-folder/nested/",
                "a-folder/nested/deep.yaml/ <classpath:/cases/a-folder/nested/deep.yaml>",
                "a-folder/nested/deep.yaml/deep case <classpath:/cases/a-folder/nested/deep.yaml>",
                "a-folder/second.yaml/ <classpath:/cases/a-folder/second.yaml>",
                "a-folder/second.yaml/third <classpath:/cases/a-folder/second.yaml>",
                "a-folder/second.yaml/first <classpath:/cases/a-folder/second.yaml>",
                "b-root.yaml/ <classpath:/cases/b-root.yaml>",
                "b-root.yaml/root case <classpath:/cases/b-root.yaml>");
    }

    @Test
    void build_whenPlanHasSeveralGlobRoots_shouldGroupFilesUnderTheirRoot() {
        CasePlan plan = new CasePlan(List.of(new CaseFile(ROOT_FILE, List.of(ROOT_CASE)),
                new CaseFile(OTHER_ROOT_FILE, List.of(OTHER_CASE))));

        Stream<DynamicNode> result = underTest.build(plan);

        assertThat(describe(result)).containsExactly(
                "cases//",
                "cases//b-root.yaml/ <classpath:/cases/b-root.yaml>",
                "cases//b-root.yaml/root case <classpath:/cases/b-root.yaml>",
                "more//",
                "more//x.yaml/ <classpath:/more/x.yaml>",
                "more//x.yaml/other case <classpath:/more/x.yaml>");
    }

    @Test
    void build_whenCaseFails_shouldRethrowTheFailureFromTheDynamicTest() {
        AssertionError failure = new AssertionError("expected 200");
        when(mockEngine.run(ROOT_CASE)).thenReturn(CaseResult.failed(ROOT_CASE.casePath(), failure));
        DynamicTest test = firstTest(underTest.build(new CasePlan(List.of(new CaseFile(ROOT_FILE,
                List.of(ROOT_CASE))))));

        assertThatThrownBy(() -> test.getExecutable().execute()).isSameAs(failure);
    }

    @Test
    void build_whenCasePasses_shouldCompleteTheDynamicTest() {
        when(mockEngine.run(ROOT_CASE)).thenReturn(CaseResult.passed(ROOT_CASE.casePath()));
        DynamicTest test = firstTest(underTest.build(new CasePlan(List.of(new CaseFile(ROOT_FILE,
                List.of(ROOT_CASE))))));

        assertThatCode(() -> test.getExecutable().execute()).doesNotThrowAnyException();
    }

    private static DynamicTest firstTest(Stream<? extends DynamicNode> nodes) {
        DynamicNode node = nodes.findFirst().orElseThrow();
        return node instanceof DynamicContainer container ? firstTest(container.getChildren()) : (DynamicTest) node;
    }

    private static CaseSource source(String root, String relativePath) {
        return new CaseSource(URI.create("classpath:/" + root + relativePath), root, relativePath);
    }
}
