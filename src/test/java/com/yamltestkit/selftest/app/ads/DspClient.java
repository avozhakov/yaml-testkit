package com.yamltestkit.selftest.app.ads;

import com.yamltestkit.selftest.app.ApiException;
import org.springframework.stereotype.Component;

/** Imitates {@code DspEngineRestClient}, the outermost DSP boundary; there is no DSP in tests, so the cases mock it. */
@Component
public class DspClient {

    public DspResponse requestAds(AdRequest request) throws ApiException {
        throw new ApiException(503, "no DSP engine in tests");
    }
}
