package com.yamltestkit.engine;

import com.yamltestkit.domain.model.Step;

/**
 * Runs one step of a case for one call kind: executes the call and evaluates the step's expectations, throwing
 * when they fail. The engine picks the executor by {@link Step#call()} kind and closes it when the engine closes.
 */
@FunctionalInterface
public interface StepExecutor extends AutoCloseable {

    void execute(CaseRun run, Step step) throws Exception;

    /** Releases what the executor holds open; nothing by default. */
    @Override
    default void close() {
    }
}
