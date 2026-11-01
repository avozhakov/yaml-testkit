package com.yamltestkit.infrastructure.yaml;

import com.yamltestkit.domain.model.CaseSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.net.URI;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CaseResourceLocatorTest {

    private static final String ROOT = "com/yamltestkit/loader/discovery/";
    private static final String GLOB = ROOT + "**";

    private CaseResourceLocator underTest;

    @BeforeEach
    void setUp() {
        underTest = new CaseResourceLocator(getClass().getClassLoader());
    }

    @Test
    void locate_whenGlobMatchesFolders_shouldReturnCaseFilesSortedByRelativePath() {
        List<CaseResource> result = underTest.locate(List.of(GLOB));

        assertThat(result).extracting(resource -> resource.source().relativePath())
                .containsExactly("a-folder/nested/deep.yaml", "a-folder/second.yaml", "b-root.yaml");
    }

    @Test
    void locate_whenFolderHoldsReservedFiles_shouldSkipFilesStartingWithUnderscore() {
        List<CaseResource> result = underTest.locate(List.of(GLOB));

        assertThat(result).extracting(resource -> resource.resource().getFilename())
                .noneMatch(fileName -> fileName.startsWith("_"));
    }

    @Test
    void locate_whenFileIsFound_shouldGiveRootAndClasspathTestSourceUri() {
        List<CaseResource> result = underTest.locate(List.of(GLOB));

        assertThat(result.getLast().source()).isEqualTo(new CaseSource(
                URI.create("classpath:/" + ROOT + "b-root.yaml"), ROOT, "b-root.yaml"));
    }

    @Test
    void locate_whenGlobsOverlap_shouldReturnEachFileOnce() {
        List<CaseResource> result = underTest.locate(List.of(GLOB, ROOT + "b-root.yaml"));

        assertThat(result).extracting(resource -> resource.source().testSourceUri())
                .doesNotHaveDuplicates()
                .hasSize(3);
    }

    @Test
    void locate_whenGlobsMatchNothing_shouldFailNamingTheGlobs() {
        List<String> globs = List.of("com/yamltestkit/loader/no-such-folder/**");

        assertThatThrownBy(() -> underTest.locate(globs))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("[com/yamltestkit/loader/no-such-folder/**]")
                .hasMessageContaining("match no case file");
    }

    @ParameterizedTest
    @MethodSource("globs")
    void pattern_whenGlobIsGiven_shouldSelectYamlFiles(String glob, String expectedPattern, String expectedRoot) {
        String pattern = CaseResourceLocator.pattern(glob);

        assertThat(pattern).isEqualTo(expectedPattern);
        assertThat(CaseResourceLocator.root(pattern)).isEqualTo(expectedRoot);
    }

    private static Stream<Arguments> globs() {
        return Stream.of(
                Arguments.of("cases/**", "cases/**/*.yaml", "cases/"),
                Arguments.of("/cases/**", "cases/**/*.yaml", "cases/"),
                Arguments.of("cases", "cases/**/*.yaml", "cases/"),
                Arguments.of("cases/", "cases/**/*.yaml", "cases/"),
                Arguments.of("cases/discover/*.yaml", "cases/discover/*.yaml", "cases/discover/"),
                Arguments.of("cases/a.yaml", "cases/a.yaml", "cases/"),
                Arguments.of("**/*.yaml", "**/*.yaml", ""));
    }
}
