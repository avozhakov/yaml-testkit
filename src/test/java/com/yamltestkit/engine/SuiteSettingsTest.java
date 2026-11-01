package com.yamltestkit.engine;

import com.yamltestkit.domain.model.CallKind;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class SuiteSettingsTest {

    @Test
    void new_whenFilterIsBlank_shouldMeanNoFilter() {
        SuiteSettings result = new SuiteSettings(List.of("cases/**"), Set.of(CallKind.CLASS), List.of(), "  ");

        assertThat(result.filter()).isNull();
    }
}
