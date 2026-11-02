package com.yamltestkit.infrastructure.yaml;

import com.fasterxml.jackson.core.ObjectCodec;
import com.fasterxml.jackson.core.io.IOContext;
import com.fasterxml.jackson.dataformat.yaml.YAMLParser;
import org.jspecify.annotations.Nullable;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.events.NodeEvent;

import java.io.Reader;

/**
 * A {@link YAMLParser} that tells the anchor ({@code &name}) of the current value. Jackson's parser reports the anchor
 * of a mapping or a sequence only, and never the anchor of a scalar.
 */
final class AnchorAwareYamlParser extends YAMLParser {

    AnchorAwareYamlParser(IOContext context, int parserFeatures, int formatFeatures, LoaderOptions loaderOptions,
                          ObjectCodec codec, Reader reader) {
        super(context, parserFeatures, formatFeatures, loaderOptions, codec, reader);
    }

    /** The anchor of the current value token (a mapping, sequence or scalar start), or {@code null} if it has none. */
    @Nullable String valueAnchor() {
        return _lastEvent instanceof NodeEvent node && !isCurrentAlias() ? node.getAnchor() : null;
    }
}
