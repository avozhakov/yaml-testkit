package com.yamltestkit.domain.model;

/**
 * A case (or a whole file) that could not be loaded; running it fails with {@code reason}, which names the case file.
 */
public record BrokenCase(String name, CaseSource source, String reason) implements CaseEntry {
}
