package com.yamltestkit.infrastructure.spring;

import java.lang.reflect.Type;

/**
 * A Mockito mock or spy the engine put into a context in place of a bean.
 *
 * @param declaredType the type the bean stands for, generics included; stubs resolve methods against it
 */
public record OverriddenBean(Object instance, Type declaredType) {
}
