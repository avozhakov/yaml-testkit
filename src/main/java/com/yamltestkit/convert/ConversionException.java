package com.yamltestkit.convert;

import lombok.experimental.StandardException;

/**
 * A value that the converter chain can't convert; the message names the type, the YAML location and the converters
 * tried.
 */
@StandardException
public class ConversionException extends RuntimeException {
}
