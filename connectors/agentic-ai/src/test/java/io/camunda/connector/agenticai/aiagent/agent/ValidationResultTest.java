/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.agent;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class ValidationResultTest {

  @Test
  void valid_hasNoViolations() {
    assertThat(ValidationResult.valid().hasViolations()).isFalse();
    assertThat(ValidationResult.valid().violations()).isEmpty();
  }

  @Test
  void withViolation_hasViolations() {
    var result =
        ValidationResult.of(
            List.of(
                new ValidationResult.Violation(
                    "MAX_MODEL_CALLS", "Reached limit of 10 (current: 10)")));
    assertThat(result.hasViolations()).isTrue();
    assertThat(result.violations()).hasSize(1);
    assertThat(result.violations().getFirst().errorCode()).isEqualTo("MAX_MODEL_CALLS");
  }
}
