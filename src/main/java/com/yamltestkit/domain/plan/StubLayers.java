package com.yamltestkit.domain.plan;

import com.yamltestkit.domain.model.ArgMatcher;
import com.yamltestkit.domain.model.BeanEntry;
import com.yamltestkit.domain.model.HttpStub;
import com.yamltestkit.domain.model.MethodRef;
import com.yamltestkit.domain.model.RequestMatcher;
import com.yamltestkit.domain.model.Stub;
import com.yamltestkit.domain.model.StubSet;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Layers stubs: a later layer's stub with the same key replaces every earlier-layer stub with that key, which is then
 * never registered, so its answer can't run. Stubs of one layer never replace each other: all of them are registered,
 * in order. A bean stub's key is bean + method + {@code paramTypes} + {@code args}; a WireMock stub's is service +
 * method + path / path pattern + query + headers + body matcher. A layer's stubs are appended after the stubs kept
 * from earlier layers, so they are registered last and win where their matching overlaps.
 */
public final class StubLayers {

    private StubLayers() {
    }

    /** @param layers lowest layer first */
    public static <T> List<T> merge(List<List<T>> layers, Function<T, Object> key) {
        List<T> merged = new ArrayList<>();
        for (List<T> layer : layers) {
            Set<Object> replaced = layer.stream().map(key).collect(Collectors.toSet());
            merged.removeIf(stub -> replaced.contains(key.apply(stub)));
            merged.addAll(layer);
        }
        return merged;
    }

    /** The bean stubs of a stub set's {@code mocks} and {@code spies}, in order. */
    public static List<BeanStub> beanStubs(StubSet stubs, String where) {
        List<BeanStub> planned = new ArrayList<>();
        addBeanStubs(planned, stubs.mocks(), where + ", mocks ");
        addBeanStubs(planned, stubs.spies(), where + ", spies ");
        return planned;
    }

    public static List<HttpStubAt> httpStubs(StubSet stubs, String where) {
        return stubs.http().stream().map(stub -> new HttpStubAt(stub, where + ", http " + stub.service())).toList();
    }

    private static void addBeanStubs(List<BeanStub> planned, List<BeanEntry> entries, String wherePrefix) {
        entries.forEach(entry -> entry.stubs().forEach(stub -> planned.add(
                new BeanStub(entry.bean(), stub, wherePrefix + entry.bean()))));
    }

    /** A stub on a bean, with its YAML location. */
    public record BeanStub(String bean, Stub stub, String where) {

        public Object key() {
            return new BeanStubKey(bean, stub.method(), stub.args());
        }
    }

    /** A WireMock stub, with its YAML location. */
    public record HttpStubAt(HttpStub stub, String where) {

        public Object key() {
            return new HttpStubKey(stub.service(), stub.request());
        }
    }

    private record BeanStubKey(String bean, MethodRef method, @Nullable List<ArgMatcher> args) {
    }

    private record HttpStubKey(String service, RequestMatcher request) {
    }
}
