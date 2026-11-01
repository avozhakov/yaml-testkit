package com.yamltestkit;

import com.yamltestkit.convert.ValueConverter;
import com.yamltestkit.domain.model.CallKind;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares which YAML case files a {@link YamlCaseSuite} runs.
 *
 * <pre>{@code
 * @YamlCases(value = "cases/**", kinds = {HTTP, BEAN})
 * class SomeServiceYamlIT extends YamlCaseSuite {}
 * }</pre>
 */
@Inherited
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface YamlCases {

    /**
     * Classpath globs, relative to the classpath root (Ant style: {@code *} within a folder, {@code **} across
     * folders). A glob ending in a folder pattern such as {@code cases/**} selects every {@code *.yaml} below it;
     * files whose name starts with {@code _} are configuration, not cases.
     */
    String[] value();

    /** Only cases whose calls are all of these kinds run; the default runs every kind. */
    CallKind[] kinds() default {CallKind.HTTP, CallKind.BEAN, CallKind.CLASS};

    /** Additional value converters, tried in order before the built-in ones; each needs a no-argument constructor. */
    Class<? extends ValueConverter>[] converters() default {};
}
