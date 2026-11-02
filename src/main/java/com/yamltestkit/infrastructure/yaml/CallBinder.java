package com.yamltestkit.infrastructure.yaml;

import com.fasterxml.jackson.databind.JsonNode;
import com.yamltestkit.domain.model.Call;
import com.yamltestkit.domain.model.CallKind;
import com.yamltestkit.domain.model.CaseDefinitionException;
import com.yamltestkit.domain.model.MethodRef;
import com.yamltestkit.domain.model.Query;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Binds a {@code call} whose file {@code caseDefaults} are already merged. The kind is the one of {@code http},
 * {@code bean}, {@code class} present in the call, and its value names the target ({@code "GET /ads"}, a bean name, a
 * class name); every other key must be a field of that kind. These checks run after the merge because a file's
 * {@code caseDefaults} may give the kind and a case only the fields, so the schema cannot make them.
 */
@RequiredArgsConstructor
final class CallBinder {

    private static final List<String> KINDS = Arrays.stream(CallKind.values()).map(CallBinder::key).toList();

    private static final Map<CallKind, List<String>> FIELDS = Collections.unmodifiableMap(new EnumMap<>(Map.of(
            CallKind.HTTP, List.of("query", "rawQuery", "headers", "body"),
            CallKind.BEAN, List.of("method", "paramTypes", "args"),
            CallKind.CLASS, List.of("method", "paramTypes", "args", "constructor", "deps"))));

    /** {@code "<METHOD> <path>"}, the same pattern as the schema's {@code call.http}; no query in the path. */
    private static final Pattern HTTP_TARGET =
            Pattern.compile("^(GET|POST|PUT|PATCH|DELETE|HEAD|OPTIONS) (/[^\\s?#]*)$");

    private final StubBinder stubs;
    private final PayloadResolver payloads;

    /**
     * @param merged whether the file's {@code caseDefaults.call} was merged into this call, so error messages say
     *               that a key may come from there
     */
    Call bind(JsonNode node, String stepName, boolean merged) {
        String where = "step '" + stepName + "': call";
        CallKind kind = kind(node, where, merged);
        checkFields(node, kind, where, merged);
        return switch (kind) {
            case HTTP -> http(node, where, merged);
            case BEAN -> bean(node, where);
            case CLASS -> unit(node, where);
        };
    }

    private static String key(CallKind kind) {
        return kind.name().toLowerCase(Locale.ROOT);
    }

    private static CallKind kind(JsonNode node, String where, boolean merged) {
        List<String> kinds = node.properties().stream().map(Map.Entry::getKey).filter(KINDS::contains).toList();
        if (kinds.isEmpty()) {
            throw new CaseDefinitionException(where + " has none of http, bean, class; exactly one is required"
                    + (merged ? " (it may come from the file's `caseDefaults`)" : ""));
        }
        if (kinds.size() > 1) {
            throw new CaseDefinitionException(where + " has conflicting keys " + kinds + "; exactly one of http, bean,"
                    + " class is allowed" + (merged ? " (one of them may come from the file's `caseDefaults`)" : ""));
        }
        return CallKind.valueOf(kinds.getFirst().toUpperCase(Locale.ROOT));
    }

    private static void checkFields(JsonNode node, CallKind kind, String where, boolean merged) {
        String key = key(kind);
        List<String> allowed = FIELDS.get(kind);
        node.properties().stream()
                .map(Map.Entry::getKey)
                .filter(field -> !field.equals(key) && !allowed.contains(field))
                .findFirst()
                .ifPresent(field -> {
                    throw new CaseDefinitionException(where + " has `" + field + "`, which does not apply to " + key
                            + " calls" + (merged ? " (it may come from the file's `caseDefaults`)" : "") + "; " + key
                            + " calls take " + String.join(", ", allowed));
                });
    }

    private Call.Http http(JsonNode node, String where, boolean merged) {
        String target = node.get("http").asText();
        Matcher matcher = HTTP_TARGET.matcher(target);
        if (!matcher.matches()) {
            throw new CaseDefinitionException(where + ".http `" + target + "` is not \"<METHOD> <path>\": one of GET,"
                    + " POST, PUT, PATCH, DELETE, HEAD, OPTIONS, one space, then a path starting with / without"
                    + " spaces, `?` or `#` (the query goes in `query` or `rawQuery`)");
        }
        String rawQuery = Nodes.text(node, "rawQuery");
        if (rawQuery != null && node.has("query")) {
            throw new CaseDefinitionException(where + " has both `query` and `rawQuery`"
                    + (merged ? " (one of them may come from the file's `caseDefaults`)" : "") + "; use one of them");
        }
        return new Call.Http(matcher.group(1), matcher.group(2),
                rawQuery == null ? queryParams(node.get("query")) : new Query.Raw(rawQuery),
                Nodes.scalarMap(node, "headers"), stubs.payload(node, "body"));
    }

    private static Query queryParams(@Nullable JsonNode node) {
        if (node == null) {
            return Query.NONE;
        }
        Map<String, List<String>> params = new LinkedHashMap<>();
        node.properties().forEach(entry -> params.put(entry.getKey(), entry.getValue().isArray()
                ? Nodes.list(entry.getValue(), JsonNode::asText)
                : List.of(entry.getValue().asText())));
        return new Query.Params(Collections.unmodifiableMap(params));
    }

    private Call.Bean bean(JsonNode node, String where) {
        return new Call.Bean(Nodes.requiredText(node, "bean", where), methodRef(node, where + " (bean)"),
                Nodes.listField(node, "args", payloads::json));
    }

    private Call.Unit unit(JsonNode node, String where) {
        return new Call.Unit(Nodes.requiredText(node, "class", where), methodRef(node, where + " (class)"),
                Nodes.listField(node, "args", payloads::json), Nodes.stringListOrNull(node, "constructor"),
                stubs.deps(node));
    }

    private static MethodRef methodRef(JsonNode node, String where) {
        return new MethodRef(Nodes.requiredText(node, "method", where), Nodes.stringListOrNull(node, "paramTypes"));
    }
}
