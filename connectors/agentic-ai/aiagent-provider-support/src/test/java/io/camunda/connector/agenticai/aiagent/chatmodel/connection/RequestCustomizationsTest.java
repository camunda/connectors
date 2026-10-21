/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.chatmodel.connection;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

class RequestCustomizationsTest {

  @Test
  void absentValuesAreEmpty() {
    assertThat(RequestCustomizations.of(null, null, null)).isEqualTo(RequestCustomizations.NONE);
  }

  @Test
  void toStringListsTheNamesButNeverTheValues() {
    final var customizations =
        RequestCustomizations.of(
            Map.of("Authorization", "Bearer secret-header"),
            Map.of("key", "secret-query"),
            Map.of("token", "secret-body"));

    assertThat(customizations.toString())
        .contains("Authorization", "key", "token")
        .doesNotContain("secret-header", "secret-query", "secret-body");
  }
}
