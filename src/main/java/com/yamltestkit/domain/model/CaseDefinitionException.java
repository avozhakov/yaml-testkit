package com.yamltestkit.domain.model;

import lombok.experimental.StandardException;

/**
 * A case file, case or configuration file that does not follow the YAML Testkit format. The message names the file
 * (and the case, when the problem is case-specific).
 */
@StandardException
public class CaseDefinitionException extends RuntimeException {
}
