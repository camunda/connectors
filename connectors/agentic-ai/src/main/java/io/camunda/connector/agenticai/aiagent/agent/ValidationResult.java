/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.agent;

import java.util.List;
import java.util.Objects;

/** Result of a limits validation check. Carries zero or more violations. */
public record ValidationResult(List<Violation> violations) {

  public ValidationResult {
    Objects.requireNonNull(violations);
    violations = List.copyOf(violations);
  }

  public boolean hasViolations() {
    return !violations.isEmpty();
  }

  public static ValidationResult valid() {
    return new ValidationResult(List.of());
  }

  public static ValidationResult of(List<Violation> violations) {
    return new ValidationResult(violations);
  }

  public record Violation(String errorCode, String message) {}
}
