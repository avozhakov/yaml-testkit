package com.yamltestkit.selftest;

import com.yamltestkit.YamlCaseSuite;
import com.yamltestkit.YamlCases;
import com.yamltestkit.domain.model.CallKind;

/**
 * Integration phase of the engine's self-tests: the {@code http} / {@code bean} cases, run against the fixture
 * application in {@code selftest.app}.
 */
@YamlCases(value = "com/yamltestkit/selftest/cases/**", kinds = {CallKind.HTTP, CallKind.BEAN})
class YamlTestkitSelfIT extends YamlCaseSuite {
}
