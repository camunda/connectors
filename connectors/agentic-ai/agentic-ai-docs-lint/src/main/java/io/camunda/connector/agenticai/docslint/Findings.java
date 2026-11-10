/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.docslint;

import java.util.List;
import java.util.Optional;

/**
 * The findings of a check, in the order they were found. A violation fails the check, a warning is
 * only reported.
 */
public record Findings(List<Finding> all) {

  public Findings {
    all = List.copyOf(all);
  }

  /** The warnings, to report. */
  public List<Warning> warnings() {
    return all.stream().filter(Warning.class::isInstance).map(Warning.class::cast).toList();
  }

  /** The violations, each after a line break, to fail the check. Empty without a violation. */
  public Optional<String> failureMessage() {
    final List<String> violations =
        all.stream().filter(Violation.class::isInstance).map(Finding::toString).toList();
    return violations.isEmpty()
        ? Optional.empty()
        : Optional.of("\n" + String.join("\n", violations));
  }
}
