package com.yamltestkit.engine;

import com.yamltestkit.domain.model.CaseFile;

import java.util.List;

/**
 * The cases a suite runs: case files in path order, each with only its selected cases (by call kind and
 * {@code yamlit.filter}); files with no selected case are left out.
 */
public record CasePlan(List<CaseFile> files) {

    public CasePlan {
        files = List.copyOf(files);
    }
}
