package com.yamltestkit.selftest;

import com.yamltestkit.YamlCaseSuite;
import com.yamltestkit.YamlCases;
import com.yamltestkit.domain.model.CallKind;

/**
 * Unit phase of the engine's self-tests: the {@code call.class} cases, run without any Spring context.
 */
@YamlCases(value = "com/yamltestkit/selftest/unit-cases/**", kinds = CallKind.CLASS)
class YamlTestkitSelfTest extends YamlCaseSuite {
}
