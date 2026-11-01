package com.yamltestkit.engine;

import com.yamltestkit.convert.ValueConverters;
import com.yamltestkit.domain.assertion.AssertionEvaluator;
import com.yamltestkit.domain.assertion.Failures;
import com.yamltestkit.domain.model.Call;
import com.yamltestkit.domain.model.CaseDefinitionException;
import com.yamltestkit.domain.model.Step;
import com.yamltestkit.infrastructure.invocation.CallOutcome;
import com.yamltestkit.infrastructure.invocation.Invocations;
import com.yamltestkit.infrastructure.reflect.MethodResolver;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.opentest4j.AssertionFailedError;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.context.ApplicationContext;
import org.springframework.util.ClassUtils;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * Runs a {@code call.bean} step: invokes the method on the named bean of the case's application context, keeps the
 * outcome as the step's result for references, runs the {@link EffectChecks} and checks {@code returns} /
 * {@code throws} / {@code assert}, together with any error a stub raised meanwhile. The method is
 * resolved on the bean's own class, so a bean behind an AOP proxy is called through the proxy.
 */
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
public final class BeanStepExecutor implements StepExecutor {

    private final ValueConverters converters;
    private final ReturnAssertion returns;
    private final EffectChecks effects;

    public static BeanStepExecutor create(ValueConverters converters, EffectChecks effects) {
        return new BeanStepExecutor(converters, new ReturnAssertion(converters, new AssertionEvaluator()), effects);
    }

    @Override
    public void execute(CaseRun run, Step step) throws IllegalAccessException {
        Call.Bean call = (Call.Bean) step.call();
        String where = run.where(step);
        Object bean = bean(run.caseContext().running().context(), call.bean(), where);
        Class<?> type = ClassUtils.getUserClass(AopUtils.getTargetClass(bean));
        Method method = MethodResolver.resolve(type, call.method(), where + ": call.bean");
        Object[] args = Invocations.arguments(converters, call.args(), method, type, where, "call.bean");
        CallOutcome outcome = Invocations.invoke(AopUtils.selectInvocableMethod(method, bean.getClass()), bean, args);
        run.recordResult(step, Invocations.result(converters, outcome));
        List<AssertionFailedError> failures = new ArrayList<>(run.stubErrors().drain());
        failures.addAll(effects.check(run, step));
        failures.addAll(returns.check(step.expect(), outcome, run, where));
        Failures.throwIfAny(failures, where);
    }

    private static Object bean(ApplicationContext context, String name, String where) {
        try {
            return context.getBean(name);
        } catch (NoSuchBeanDefinitionException e) {
            throw new CaseDefinitionException(where + ": call.bean: the context has no bean named '" + name + "'", e);
        }
    }
}
