package com.yamltestkit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The engine package is application-agnostic (spec: Engine package is application-agnostic): its main sources
 * reference no class of the self-test fixtures ({@code selftest}), and reference protobuf and vavr only in the
 * optional converters under {@code convert/protobuf} and {@code convert/vavr}. The domain stays pure: {@code domain/}
 * references no test or runtime framework (Spring, Mockito, WireMock, Logback, JUnit) and no engine package outside
 * {@code domain}, so dependencies point only inward. Comments and string literals don't count, so the core may name
 * an optional converter or library class by its name.
 */
class PackageIsolationTest {

    private static final Path ENGINE_SOURCES = Path.of("src/main/java/com/yamltestkit");
    /** Comments, string literals, text blocks and char literals, which may mention any package by name. */
    private static final Pattern NOT_CODE = Pattern.compile(
            "\"\"\"[\\s\\S]*?\"\"\"|\"(?:\\\\.|[^\"\\\\\\n])*\"|'(?:\\\\.|[^'\\\\\\n])+'|//[^\\n]*|/\\*[\\s\\S]*?\\*/");
    private static final Path DOMAIN = Path.of("domain");
    private static final List<Rule> RULES = List.of(
            Rule.everywhere("com.yamltestkit.selftest", "\\bcom\\.yamltestkit\\.selftest\\b"),
            Rule.outside(Path.of("convert", "protobuf"), "com.google.protobuf", "\\bcom\\.google\\.protobuf\\b"),
            Rule.outside(Path.of("convert", "vavr"), "io.vavr", "\\bio\\.vavr\\b"),
            Rule.inside(DOMAIN, "org.springframework", "\\borg\\.springframework\\b"),
            Rule.inside(DOMAIN, "org.mockito", "\\borg\\.mockito\\b"),
            Rule.inside(DOMAIN, "com.github.tomakehurst.wiremock", "\\bcom\\.github\\.tomakehurst\\.wiremock\\b"),
            Rule.inside(DOMAIN, "ch.qos.logback", "\\bch\\.qos\\.logback\\b"),
            Rule.inside(DOMAIN, "org.junit", "\\borg\\.junit\\b"),
            Rule.inside(DOMAIN, "an engine package outside domain", "\\bcom\\.yamltestkit\\.(?!domain\\b)\\w"));

    @Test
    void engineSources_whenScanned_shouldFollowEveryIsolationRule() {
        List<String> result = violations(ENGINE_SOURCES);

        assertThat(result).isEmpty();
        assertThat(javaFiles(ENGINE_SOURCES)).hasSizeGreaterThan(50);
    }

    @Test
    void violations_whenFilesBreakTheRules_shouldReportEachOne(@TempDir Path root) throws IOException {
        write(root, "call/Bad.java", "import com.yamltestkit.selftest.app.FeedCache;\n"
                + "class Bad { com.google.protobuf.Message m; }");
        write(root, "convert/Vavr.java", "import io.vavr.control.Validation;\nclass Vavr {}");
        write(root, "domain/model/Bad.java", "import org.springframework.core.io.Resource;\n"
                + "import org.mockito.Mockito;\nclass Bad {}");
        write(root, "domain/plan/Bad.java", "import com.github.tomakehurst.wiremock.WireMockServer;\n"
                + "import ch.qos.logback.classic.Logger;\nimport org.junit.jupiter.api.Test;\n"
                + "import com.yamltestkit.infrastructure.spring.RunningContext;\nclass Bad {}");
        write(root, "domain/rules/Root.java", "import com.yamltestkit.YamlCaseSuite;\nclass Root {}");

        List<String> result = violations(root);

        assertThat(result).containsExactlyInAnyOrder(
                "call/Bad.java references com.yamltestkit.selftest",
                "call/Bad.java references com.google.protobuf",
                "convert/Vavr.java references io.vavr",
                "domain/model/Bad.java references org.springframework",
                "domain/model/Bad.java references org.mockito",
                "domain/plan/Bad.java references com.github.tomakehurst.wiremock",
                "domain/plan/Bad.java references ch.qos.logback",
                "domain/plan/Bad.java references org.junit",
                "domain/plan/Bad.java references an engine package outside domain",
                "domain/rules/Root.java references an engine package outside domain");
    }

    @Test
    void violations_whenReferencesAreAllowed_shouldReportNothing(@TempDir Path root) throws IOException {
        write(root, "convert/protobuf/Proto.java", "import com.google.protobuf.Message;\nclass Proto {}");
        write(root, "convert/vavr/Vavr.java", "import io.vavr.control.Validation;\nclass Vavr {}");
        write(root, "convert/Chain.java", "class Chain {\n    // io.vavr is optional\n"
                + "    String library = \"com.google.protobuf.Message\";\n    /* com.yamltestkit.selftest.x */\n}");
        write(root, "infrastructure/spring/Spring.java", "import org.springframework.context.ApplicationContext;\n"
                + "import org.mockito.Mockito;\nimport com.yamltestkit.infrastructure.yaml.CaseFileLoader;\n"
                + "class Spring {}");
        write(root, "domain/plan/Pure.java", "import com.yamltestkit.domain.model.Step;\n"
                + "import com.yamltestkit.domain.rules.CaseRules;\n"
                + "/** Started by {@code org.springframework} through com.yamltestkit.infrastructure.spring. */\n"
                + "class Pure {}");

        List<String> result = violations(root);

        assertThat(result).isEmpty();
    }

    private static List<String> violations(Path root) {
        return javaFiles(root).stream()
                .flatMap(file -> violations(root, file))
                .toList();
    }

    private static Stream<String> violations(Path root, Path file) {
        Path relative = root.relativize(file);
        String code = NOT_CODE.matcher(read(file)).replaceAll(" ");
        return RULES.stream()
                .filter(rule -> rule.checkedIn().test(relative))
                .filter(rule -> rule.reference().matcher(code).find())
                .map(rule -> relative.toString().replace('\\', '/') + " references " + rule.name());
    }

    private static List<Path> javaFiles(Path root) {
        try (Stream<Path> files = Files.walk(root)) {
            return files.filter(file -> file.toString().endsWith(".java")).sorted().toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String read(Path file) {
        try {
            return Files.readString(file);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void write(Path root, String relative, String content) throws IOException {
        Path file = root.resolve(relative);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }

    /**
     * @param name      how a violation names the reference
     * @param reference the forbidden reference in code
     * @param checkedIn the files (relative to the engine root) the rule applies to
     */
    private record Rule(String name, Pattern reference, Predicate<Path> checkedIn) {

        static Rule everywhere(String name, String reference) {
            return new Rule(name, Pattern.compile(reference), file -> true);
        }

        static Rule outside(Path allowedUnder, String name, String reference) {
            return new Rule(name, Pattern.compile(reference), file -> !file.startsWith(allowedUnder));
        }

        static Rule inside(Path folder, String name, String reference) {
            return new Rule(name, Pattern.compile(reference), file -> file.startsWith(folder));
        }
    }
}
