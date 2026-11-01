package com.yamltestkit.convert;

import com.fasterxml.jackson.databind.JsonNode;
import org.jspecify.annotations.Nullable;

import java.lang.reflect.Type;

/**
 * The converter chain as seen by a {@link ValueConverter}, for converting nested values; errors keep the YAML
 * location of the outermost value.
 */
public interface ConversionContext {

    @Nullable Object fromYaml(JsonNode value, Type targetType);

    JsonNode toJson(@Nullable Object value);
}
