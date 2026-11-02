package com.yamltestkit.infrastructure.yaml;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.MissingNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.yamltestkit.domain.model.BrokenCase;
import com.yamltestkit.domain.model.CaseDefinitionException;
import com.yamltestkit.domain.model.CaseEntry;
import com.yamltestkit.domain.model.CaseFile;
import com.yamltestkit.domain.model.CaseSource;
import com.yamltestkit.domain.model.YamlCase;
import com.yamltestkit.domain.rules.CaseRules;
import com.yamltestkit.domain.rules.ReferenceOrderCheck;
import lombok.RequiredArgsConstructor;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Loads a case file: YAML → schema validation → {@code define} dropped → {@code caseDefaults} merge → model (call
 * kind and fields) → kind rules and reference order. A file that fails before the cases are split (invalid YAML,
 * schema) yields one {@link BrokenCase} named {@value #INVALID_FILE}; a case that fails afterwards (missing payload
 * file, rule violation) yields a {@link BrokenCase} with its own name, so the other cases of the file still run.
 */
@RequiredArgsConstructor
public final class CaseFileLoader {

    public static final String INVALID_FILE = "invalid case file";

    private final SchemaValidator schemaValidator;

    public CaseFile load(CaseResource caseResource) {
        CaseSource source = caseResource.source();
        String label = source.label();
        JsonNode root;
        try {
            root = YamlFiles.read(caseResource.resource(), label);
            schemaValidator.validate(root, SchemaValidator.FileKind.CASE_FILE, label);
        } catch (CaseDefinitionException e) {
            return new CaseFile(source, List.of(new BrokenCase(INVALID_FILE, source, e.getMessage())));
        }
        Nodes.dropDefine(root);
        CaseBinder binder = new CaseBinder(source, new PayloadResolver(caseResource.resource()));
        JsonNode defaults = root.path("caseDefaults");
        List<JsonNode> rawCases = root.has("cases") ? Nodes.list(root.get("cases"), node -> node) : List.of(root);
        List<CaseEntry> cases = new ArrayList<>(rawCases.size());
        Set<String> names = new HashSet<>();
        for (JsonNode rawCase : rawCases) {
            String name = rawCase.get("name").asText();
            cases.add(names.add(name)
                    ? bind(binder, rawCase, defaults, source)
                    : new BrokenCase(name, source, label + ", case '" + name + "': duplicate case name in this file"));
        }
        return new CaseFile(source, cases);
    }

    private static CaseEntry bind(CaseBinder binder, JsonNode rawCase, JsonNode defaults, CaseSource source) {
        String name = rawCase.get("name").asText();
        try {
            ObjectNode merged = defaults.isMissingNode()
                    ? (ObjectNode) rawCase
                    : DefaultsMerger.merge(defaults, (ObjectNode) rawCase);
            YamlCase yamlCase = binder.bind(merged, defaults.isMissingNode() ? MissingNode.getInstance() : defaults,
                    rawCase);
            CaseRules.check(yamlCase);
            ReferenceOrderCheck.check(yamlCase);
            return yamlCase;
        } catch (CaseDefinitionException e) {
            return new BrokenCase(name, source, source.label() + ", case '" + name + "': " + e.getMessage());
        }
    }
}
