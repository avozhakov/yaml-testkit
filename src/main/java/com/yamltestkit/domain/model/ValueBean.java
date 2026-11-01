package com.yamltestkit.domain.model;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * A {@code valueBeans} entry of {@code _context.yaml}: a real object converted from {@code value}, registered under
 * {@code name} in place of any bean of that name.
 */
public record ValueBean(String name, String type, JsonNode value, boolean primary) {
}
