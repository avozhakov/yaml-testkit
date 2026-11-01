package com.yamltestkit.selftest.app.ads;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** @param retries how many times a DSP call that threw {@code ApiException} is retried */
@ConfigurationProperties("fixture.dsp")
public record DspProperties(int retries) {
}
