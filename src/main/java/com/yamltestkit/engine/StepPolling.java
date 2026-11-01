package com.yamltestkit.engine;

import com.yamltestkit.domain.model.Polling;
import com.yamltestkit.domain.model.Step;
import org.opentest4j.AssertionFailedError;

/**
 * Runs a step once, or with {@code expect.within} reruns its call and expectations every {@code every} until they
 * pass or time runs out, reporting the last failure (design D12). Only failed expectations are retried; an invalid
 * case or a call that can't be made fails at once. Before each retry the calls recorded on the context's mocks and
 * spies and the WireMock journals are cleared (stubs stay), so count checks apply to one attempt (design D20).
 */
public final class StepPolling {

    private StepPolling() {
    }

    public static void execute(StepExecutor executor, CaseRun run, Step step) throws Exception {
        Polling polling = step.expect().polling();
        if (polling == null) {
            executor.execute(run, step);
            return;
        }
        long deadline = System.nanoTime() + polling.within().toNanos();
        for (int attempt = 1; ; attempt++) {
            try {
                executor.execute(run, step);
                return;
            } catch (AssertionError failure) {
                if (System.nanoTime() + polling.every().toNanos() > deadline) {
                    throw new AssertionFailedError(run.where(step) + ": still failing after "
                            + polling.within().toMillis() + " ms (" + attempt + " attempts); last failure: "
                            + failure.getMessage(), failure);
                }
                Thread.sleep(polling.every());
                run.clearRecordings();
            }
        }
    }
}
