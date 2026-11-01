package com.yamltestkit.infrastructure.yaml;

import com.yamltestkit.domain.model.CaseSource;
import org.springframework.core.io.Resource;

/**
 * A located case file: the resource to read and where it sits relative to its glob root.
 */
public record CaseResource(Resource resource, CaseSource source) {
}
