package com.yamltestkit.engine;

import com.yamltestkit.convert.ValueConverters;
import com.yamltestkit.domain.assertion.AssertionEvaluator;
import com.yamltestkit.domain.assertion.Failures;
import com.yamltestkit.domain.model.Call;
import com.yamltestkit.domain.model.CaseDefinitionException;
import com.yamltestkit.domain.model.Step;
import com.yamltestkit.infrastructure.invocation.CallOutcome;
import com.yamltestkit.infrastructure.invocation.Invocations;
import com.yamltestkit.infrastructure.mockito.StubApplier;
import com.yamltestkit.infrastructure.mockito.UnitInstance;
import com.yamltestkit.infrastructure.mockito.UnitInstanceFactory;
import com.yamltestkit.infrastructure.reflect.MethodResolver;
import com.yamltestkit.infrastructure.reflect.TypeNames;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.opentest4j.AssertionFailedError;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

/**
 * Runs a {@code call.class} step without Spring: creates the instance ({@link UnitInstanceFactory}; none for a
 * static method) and keeps its dependency mocks in the {@link CaseRun}, converts {@code args} to the method's
 * parameter types, invokes the method, keeps the outcome as the step's result for references, runs the
 * {@link EffectChecks} ({@code verify} on {@code dep} sees this step's dependency mocks) and checks {@code returns} /
 * {@code throws} / {@code assert}, together with any error the dependency stubs raised during the call.
 */
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
public final class UnitStepExecutor implements StepExecutor {

    private final ValueConverters converters;
    private final UnitInstanceFactory instances;
    private final ReturnAssertion returns;
    private final EffectChecks effects;

    public static UnitStepExecutor create(ValueConverters converters, EffectChecks effects) {
        return new UnitStepExecutor(converters, new UnitInstanceFactory(converters, StubApplier.create(converters)),
                new ReturnAssertion(converters, new AssertionEvaluator()), effects);
    }

    @Override
    public void execute(CaseRun run, Step step) throws Exception {
        Call.Unit call = (Call.Unit) step.call();
        String where = run.where(step);
        Class<?> type = TypeNames.load(call.className(), where + ": call.class");
        Method method = MethodResolver.resolve(type, call.method(), where + ": call.class");
        UnitInstance instance = instance(call, type, method, run, where);
        run.rememberUnitMocks(step, instance.mocks());
        Object[] args = Invocations.arguments(converters, call.args(), method, type, where, "call.class");
        CallOutcome outcome = Invocations.invoke(method, instance.instance(), args);
        run.recordResult(step, Invocations.result(converters, outcome));
        List<AssertionFailedError> failures = new ArrayList<>(run.stubErrors().drain());
        failures.addAll(effects.check(run, step));
        failures.addAll(returns.check(step.expect(), outcome, run, where));
        Failures.throwIfAny(failures, where);
    }

    private UnitInstance instance(Call.Unit call, Class<?> type, Method method, CaseRun run, String where) {
        if (!Modifier.isStatic(method.getModifiers())) {
            return instances.create(type, call.constructor(), call.deps(), run.stubErrors(), where + ": call.class");
        }
        if (call.constructor() != null || !call.deps().isEmpty()) {
            throw new CaseDefinitionException(where + ": " + TypeNames.signature(method)
                    + " is static, so no instance is created; remove `constructor` / `deps`");
        }
        return UnitInstance.STATIC;
    }
}
