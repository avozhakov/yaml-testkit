package com.yamltestkit.selftest.app.ads;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

/** Imitates {@code IntentFilter}: real logic around the {@link InnerClient} it delegates to. */
@Component
@RequiredArgsConstructor
public class OuterService {

    private final InnerClient innerClient;

    public List<String> filterTerms(List<String> terms) {
        Set<String> blocked = Set.copyOf(innerClient.blockedTerms(terms));
        return terms.stream().filter(term -> !blocked.contains(term)).toList();
    }
}
