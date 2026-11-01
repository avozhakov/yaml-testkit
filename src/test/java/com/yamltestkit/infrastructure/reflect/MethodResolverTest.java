package com.yamltestkit.infrastructure.reflect;

import com.yamltestkit.domain.model.CaseDefinitionException;
import com.yamltestkit.domain.model.MethodRef;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MethodResolverTest {

    private static final String WHERE = "cases/a.yaml, case 'c', step 'main'";

    @Test
    void resolve_whenNameIsUnique_shouldReturnTheMethodWithoutParamTypes() {
        Method result = MethodResolver.resolve(Shop.class, new MethodRef("open", null), WHERE);

        assertThat(result.getName()).isEqualTo("open");
    }

    @Test
    void resolve_whenParamTypesAreSimpleQualifiedOrArrayNames_shouldPickTheOverload() {
        Method bySimpleName = MethodResolver.resolve(Shop.class, new MethodRef("price", List.of("String", "int")),
                WHERE);
        Method byQualifiedName = MethodResolver.resolve(Shop.class, new MethodRef("price", List.of("java.lang.String")),
                WHERE);
        Method byArray = MethodResolver.resolve(Shop.class, new MethodRef("price", List.of("String[]")), WHERE);

        assertThat(bySimpleName.getParameterTypes()).containsExactly(String.class, int.class);
        assertThat(byQualifiedName.getParameterTypes()).containsExactly(String.class);
        assertThat(byArray.getParameterTypes()).containsExactly(String[].class);
    }

    @Test
    void resolve_whenOverloadedWithoutParamTypes_shouldListTheCandidates() {
        assertThatThrownBy(() -> MethodResolver.resolve(Shop.class, new MethodRef("price", null), WHERE))
                .isInstanceOf(CaseDefinitionException.class)
                .hasMessageStartingWith(WHERE + ": method 'price' of " + Shop.class.getName() + " is ambiguous;")
                .hasMessageContaining("price(String)")
                .hasMessageContaining("price(String, int)")
                .hasMessageContaining("static price(String[])");
    }

    @Test
    void resolve_whenNoMethodHasTheName_shouldSaySo() {
        assertThatThrownBy(() -> MethodResolver.resolve(Shop.class, new MethodRef("close", null), WHERE))
                .isInstanceOf(CaseDefinitionException.class)
                .hasMessage(WHERE + ": " + Shop.class.getName() + " has no method 'close'");
    }

    @Test
    void resolve_whenParamTypesMatchNoOverload_shouldListTheCandidates() {
        assertThatThrownBy(() -> MethodResolver.resolve(Shop.class, new MethodRef("open", List.of("int")), WHERE))
                .isInstanceOf(CaseDefinitionException.class)
                .hasMessage(WHERE + ": no method open[int] on " + Shop.class.getName() + "; candidates: open()");
    }

    public static class Shop {

        public void open() {
        }

        public int price(String sku) {
            return 1;
        }

        public int price(String sku, int quantity) {
            return quantity;
        }

        public static int price(String[] skus) {
            return skus.length;
        }
    }
}
