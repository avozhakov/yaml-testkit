package com.yamltestkit.domain.model;

import java.util.List;

/**
 * A loaded case file: its cases in file order. A file that can't be read or fails schema validation holds a single
 * {@link BrokenCase}.
 */
public record CaseFile(CaseSource source, List<CaseEntry> cases) {

    public CaseFile {
        cases = List.copyOf(cases);
    }

    public CaseFile withCases(List<CaseEntry> selected) {
        return new CaseFile(source, selected);
    }
}
