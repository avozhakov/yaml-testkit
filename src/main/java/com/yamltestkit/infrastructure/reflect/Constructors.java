package com.yamltestkit.infrastructure.reflect;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

/**
 * Reflective construction for the objects a case builds: units under test, their {@code real: true} dependencies,
 * stubbed exceptions and converters.
 */
public final class Constructors {

    private Constructors() {
    }

    /**
     * @param where how the YAML location (or the object being built) is named in errors
     * @throws IllegalStateException naming {@code where} and the signature when the constructor throws (with the
     *                               thrown exception as cause) or can't be called
     */
    public static <T> T instantiate(Constructor<T> constructor, Object[] args, String where) {
        try {
            return constructor.newInstance(args);
        } catch (InvocationTargetException e) {
            throw new IllegalStateException(where + ": constructor " + TypeNames.signature(constructor) + " threw "
                    + e.getCause(), e.getCause());
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(where + ": can't call " + TypeNames.signature(constructor) + ": " + e, e);
        }
    }
}
