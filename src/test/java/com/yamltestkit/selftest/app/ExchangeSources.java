package com.yamltestkit.selftest.app;

import java.util.List;

/** A data bean replaced by a value bean (imitates {@code DiscoverExchangeSources}). */
public record ExchangeSources(List<String> sources) {
}
