package com.yamltestkit.junit;

import com.yamltestkit.domain.model.CaseEntry;
import com.yamltestkit.domain.model.CaseFile;
import com.yamltestkit.engine.CasePlan;
import com.yamltestkit.engine.CaseResult;
import com.yamltestkit.engine.YamlCaseEngine;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.DynamicContainer;
import org.junit.jupiter.api.DynamicNode;
import org.junit.jupiter.api.DynamicTest;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * Turns a {@link CasePlan} into the JUnit tree folder → file → case, keeping file order. With several glob roots
 * each root gets its own top-level container. Files and cases carry the file's {@code classpath:} test source URI.
 */
@RequiredArgsConstructor
public final class CaseTreeBuilder {

    private final YamlCaseEngine engine;

    public Stream<DynamicNode> build(CasePlan plan) {
        Map<String, List<CaseFile>> byRoot = groupBy(plan.files(), file -> file.source().root());
        if (byRoot.size() == 1) {
            return folder(plan.files(), 0);
        }
        return byRoot.entrySet().stream().map(root -> DynamicContainer.dynamicContainer(
                root.getKey().isEmpty() ? "/" : root.getKey(), folder(root.getValue(), 0)));
    }

    private Stream<DynamicNode> folder(List<CaseFile> files, int depth) {
        Map<String, List<CaseFile>> bySegment = groupBy(files, file -> segments(file)[depth]);
        return bySegment.entrySet().stream().map(entry -> {
            List<CaseFile> group = entry.getValue();
            boolean isFile = group.size() == 1 && segments(group.getFirst()).length == depth + 1;
            return isFile
                    ? file(entry.getKey(), group.getFirst())
                    : DynamicContainer.dynamicContainer(entry.getKey(), folder(group, depth + 1));
        });
    }

    private DynamicNode file(String fileName, CaseFile file) {
        return DynamicContainer.dynamicContainer(fileName, file.source().testSourceUri(),
                file.cases().stream().map(entry -> test(entry, file)));
    }

    private DynamicNode test(CaseEntry entry, CaseFile file) {
        return DynamicTest.dynamicTest(entry.name(), file.source().testSourceUri(), () -> {
            CaseResult result = engine.run(entry);
            if (!result.isPassed()) {
                throw result.failure();
            }
        });
    }

    private static String[] segments(CaseFile file) {
        return file.source().relativePath().split("/");
    }

    private static Map<String, List<CaseFile>> groupBy(List<CaseFile> files,
                                                       Function<CaseFile, String> key) {
        Map<String, List<CaseFile>> groups = new LinkedHashMap<>();
        files.forEach(file -> groups.computeIfAbsent(key.apply(file), ignored -> new ArrayList<>()).add(file));
        return groups;
    }
}
