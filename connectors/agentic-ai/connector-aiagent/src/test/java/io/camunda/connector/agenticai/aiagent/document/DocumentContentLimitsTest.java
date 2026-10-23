/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class DocumentContentLimitsTest {

  @Test
  void theDefaultsStayBelowTheLimitsOfTheMostRestrictiveProvider() {
    final var defaults = DocumentContentLimits.defaults();

    assertThat(defaults.textBytes()).isEqualTo(256L * 1024);
    assertThat(defaults.pdfBytes()).isEqualTo((long) (3.5 * 1024 * 1024));
    assertThat(defaults.imageBytes()).isEqualTo((long) (3.5 * 1024 * 1024));
  }

  @Test
  void rejectsALimitThatIsNotPositive() {
    assertThatThrownBy(() -> new DocumentContentLimits(0, 1, 1))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("text");
    assertThatThrownBy(() -> new DocumentContentLimits(1, -1, 1))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("pdf");
    assertThatThrownBy(() -> new DocumentContentLimits(1, 1, 0))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("image");
  }
}
