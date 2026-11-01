package com.yamltestkit.junit;

import org.junit.jupiter.api.DynamicContainer;
import org.junit.jupiter.api.DynamicNode;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Flattens a dynamic test tree for assertions, shared by the tests of {@link CaseTreeBuilder} and of the suite.
 */
public final class DynamicNodeTrees {

    private DynamicNodeTrees() {
    }

    /** Depth-first "path/" for containers and "path/name" for tests, each with its test source URI if any. */
    public static List<String> describe(Stream<? extends DynamicNode> nodes) {
        List<String> lines = new ArrayList<>();
        describe(nodes, "", lines);
        return lines;
    }

    private static void describe(Stream<? extends DynamicNode> nodes, String prefix, List<String> lines) {
        nodes.forEach(node -> {
            String uri = node.getTestSourceUri().map(source -> " <" + source + ">").orElse("");
            if (node instanceof DynamicContainer container) {
                String path = prefix + node.getDisplayName() + "/";
                lines.add(path + uri);
                describe(container.getChildren(), path, lines);
            } else {
                lines.add(prefix + node.getDisplayName() + uri);
            }
        });
    }
}
