package com.yamltestkit.infrastructure.yaml;

import com.fasterxml.jackson.core.io.IOContext;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import java.io.IOException;
import java.io.InputStream;

/**
 * Creates an {@link AnchorAwareYamlParser} for stream input, the only input {@link YamlFiles} reads; everything else,
 * scalar typing included, is Jackson's {@link YAMLFactory}.
 */
final class AnchorAwareYamlFactory extends YAMLFactory {

    @Override
    protected AnchorAwareYamlParser _createParser(InputStream in, IOContext context) throws IOException {
        return new AnchorAwareYamlParser(context, _parserFeatures, _yamlParserFeatures, _loaderOptions, _objectCodec,
                _createReader(in, null, context));
    }
}
