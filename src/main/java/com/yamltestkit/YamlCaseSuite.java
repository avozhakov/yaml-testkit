package com.yamltestkit;

import com.yamltestkit.engine.YamlCaseEngine;
import com.yamltestkit.junit.CaseTreeBuilder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicNode;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolver;

import java.util.stream.Stream;

/**
 * Base class of a YAML case entry class; the subclass only carries {@link YamlCases}:
 *
 * <pre>{@code
 * @YamlCases(value = "cases/**", kinds = {HTTP, BEAN})
 * class SomeServiceYamlIT extends YamlCaseSuite {}
 * }</pre>
 *
 * Each case becomes a dynamic test under its folder and file, in file order. Nothing relies on JUnit lifecycle
 * callbacks: dynamic tests don't run {@code @BeforeEach}, and the engine resets its own state per case. The engine
 * is kept in the JUnit store of the suite class, which closes it, with the contexts its cases started, when the
 * class finishes, so other test classes of the run don't share the JVM with its contexts still open.
 */
@ExtendWith(YamlCaseSuite.EngineResolver.class)
public abstract class YamlCaseSuite {

    @TestFactory
    @DisplayName("YAML cases")
    protected final Stream<DynamicNode> yamlCases(YamlCaseEngine engine) {
        return new CaseTreeBuilder(engine).build(engine.plan(engine.load()));
    }

    /**
     * Supplies the suite's engine, stored in the {@link ExtensionContext.Store} of the suite class's context (which
     * closes {@link AutoCloseable} values when the class finishes); a test factory can't receive the extension context
     * itself.
     */
    static final class EngineResolver implements ParameterResolver {

        private static final ExtensionContext.Namespace NAMESPACE = ExtensionContext.Namespace.create(
                EngineResolver.class);

        @Override
        public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext) {
            return parameterContext.getParameter().getType() == YamlCaseEngine.class;
        }

        @Override
        public Object resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext) {
            ExtensionContext classContext = classContext(extensionContext);
            return classContext.getStore(NAMESPACE).computeIfAbsent(classContext.getRequiredTestClass(),
                    type -> YamlCaseEngine.create(YamlCasesReader.read(type)), YamlCaseEngine.class);
        }

        /** The context of the class that declares the test factory: the nearest one without a test method. */
        private static ExtensionContext classContext(ExtensionContext context) {
            ExtensionContext current = context;
            while (current.getTestMethod().isPresent()) {
                current = current.getParent().orElseThrow(() -> new IllegalStateException(
                        "a test method's extension context has no parent"));
            }
            return current;
        }
    }
}
