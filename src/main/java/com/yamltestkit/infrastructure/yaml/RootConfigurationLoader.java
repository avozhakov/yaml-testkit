package com.yamltestkit.infrastructure.yaml;

import com.fasterxml.jackson.databind.JsonNode;
import com.yamltestkit.convert.ValueConverter;
import com.yamltestkit.domain.model.CaseDefinitionException;
import com.yamltestkit.domain.model.ContextFile;
import com.yamltestkit.domain.model.ContextVariant;
import com.yamltestkit.domain.model.MockBean;
import com.yamltestkit.domain.model.RootConfiguration;
import com.yamltestkit.domain.model.SpyBean;
import com.yamltestkit.domain.model.StubSet;
import com.yamltestkit.domain.model.ValueBean;
import com.yamltestkit.infrastructure.reflect.TypeNames;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Loads {@code _context.yaml} and {@code _caseDefaults.yaml} from the root folder of each {@code @YamlCases} glob (the
 * path before its first wildcard segment), schema-validated; a top-level {@code define} block is dropped after
 * validation and {@code { file }} payloads resolve relative to the file. A missing file is allowed; a broken one
 * (including a {@code converters} entry that is not a loadable {@code ValueConverter}) is kept as the root's failure
 * instead of failing the whole suite.
 */
@RequiredArgsConstructor
public final class RootConfigurationLoader {

    private final ResourceLoader resources;
    private final SchemaValidator schemaValidator;

    /** The configuration of every glob root, by root, in glob order. */
    public Map<String, RootConfiguration> load(List<String> globs) {
        Map<String, RootConfiguration> configurations = new LinkedHashMap<>();
        for (String glob : globs) {
            String root = CaseResourceLocator.root(CaseResourceLocator.pattern(glob));
            configurations.computeIfAbsent(root, this::loadRoot);
        }
        return Collections.unmodifiableMap(configurations);
    }

    private RootConfiguration loadRoot(String root) {
        String defaultsLabel = root + RootConfiguration.DEFAULTS_FILE;
        try {
            return new RootConfiguration(root, contextFile(root), defaults(defaultsLabel), defaultsLabel, null);
        } catch (CaseDefinitionException e) {
            return new RootConfiguration(root, null, StubSet.EMPTY, defaultsLabel, e.getMessage());
        }
    }

    private @Nullable ContextFile contextFile(String root) {
        String label = root + RootConfiguration.CONTEXT_FILE;
        Resource resource = resources.getResource("classpath:" + label);
        if (!resource.exists()) {
            return null;
        }
        JsonNode node = YamlFiles.read(resource, label);
        schemaValidator.validate(node, SchemaValidator.FileKind.CONTEXT_FILE, label);
        Nodes.dropDefine(node);
        StubBinder stubs = new StubBinder(new PayloadResolver(resource));
        Map<String, ContextVariant> variants = new LinkedHashMap<>();
        node.path("variants").properties().forEach(entry -> variants.put(entry.getKey(),
                variant(entry.getValue(), stubs, label + ", variant '" + entry.getKey() + "'")));
        Integer settleMs = Nodes.integer(node, "settleMs");
        List<String> converters = Nodes.stringList(node, "converters");
        converters.forEach(name -> checkConverter(name, label));
        return new ContextFile(label, Nodes.text(node, "application"), Nodes.stringList(node, "profiles"),
                variant(node, stubs, label), variants, Nodes.stringList(node, "services"), converters,
                settleMs == null ? 0 : settleMs);
    }

    private static void checkConverter(String name, String label) {
        Class<?> type = TypeNames.load(name, label + ": converters");
        if (!ValueConverter.class.isAssignableFrom(type)) {
            throw new CaseDefinitionException(label + ": converters: " + name + " does not implement "
                    + ValueConverter.class.getName());
        }
    }

    private StubSet defaults(String label) {
        Resource resource = resources.getResource("classpath:" + label);
        if (!resource.exists()) {
            return StubSet.EMPTY;
        }
        JsonNode node = YamlFiles.read(resource, label);
        schemaValidator.validate(node, SchemaValidator.FileKind.DEFAULTS_FILE, label);
        Nodes.dropDefine(node);
        try {
            return new StubBinder(new PayloadResolver(resource)).stubSet(node);
        } catch (CaseDefinitionException e) {
            throw new CaseDefinitionException(label + ": " + e.getMessage(), e);
        }
    }

    private static ContextVariant variant(JsonNode node, StubBinder stubs, String label) {
        try {
            return new ContextVariant(properties(node.path("properties")),
                    Nodes.listField(node, "mockBeans", entry -> mockBean(entry, stubs)),
                    Nodes.listField(node, "spyBeans", entry -> spyBean(entry, stubs)),
                    Nodes.listField(node, "valueBeans", entry -> valueBean(entry, stubs)));
        } catch (CaseDefinitionException e) {
            throw new CaseDefinitionException(label + ": " + e.getMessage(), e);
        }
    }

    /** Scalars as their text; a list as indexed keys, which Spring binds to a list property. */
    private static Map<String, String> properties(JsonNode node) {
        Map<String, String> properties = new LinkedHashMap<>();
        node.properties().forEach(entry -> {
            if (entry.getValue().isArray()) {
                for (int i = 0; i < entry.getValue().size(); i++) {
                    properties.put(entry.getKey() + "[" + i + "]", entry.getValue().get(i).asText());
                }
            } else {
                properties.put(entry.getKey(), entry.getValue().asText());
            }
        });
        return properties;
    }

    private static MockBean mockBean(JsonNode node, StubBinder stubs) {
        if (node.isTextual()) {
            return new MockBean(node.asText(), null, false, List.of());
        }
        return new MockBean(node.get("name").asText(), Nodes.text(node, "type"), Nodes.flag(node, "primary"),
                Nodes.listField(node, "stubs", stubs::stub));
    }

    private static SpyBean spyBean(JsonNode node, StubBinder stubs) {
        if (node.isTextual()) {
            return new SpyBean(node.asText(), List.of());
        }
        return new SpyBean(node.get("name").asText(), Nodes.listField(node, "stubs", stubs::stub));
    }

    private static ValueBean valueBean(JsonNode node, StubBinder stubs) {
        return new ValueBean(node.get("name").asText(), node.get("type").asText(), stubs.payload(node, "value"),
                Nodes.flag(node, "primary"));
    }
}
