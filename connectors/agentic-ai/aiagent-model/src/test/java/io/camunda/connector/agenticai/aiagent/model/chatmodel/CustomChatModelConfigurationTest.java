/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model.chatmodel;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CustomChatModelConfigurationTest {

  @Test
  void identifiesTheProviderByItsType() {
    final var configuration = new CustomChatModelConfiguration("acme", "m-1", Map.of());

    assertThat(configuration.provider()).isEqualTo("acme");
    assertThat(configuration.descriptiveProvider()).isEqualTo("acme");
    assertThat(configuration.model()).isEqualTo("m-1");
  }

  @Test
  void copiesTheParametersAndKeepsNullValues() {
    final var parameters = new HashMap<String, Object>();
    parameters.put("present", 1);
    parameters.put("absent", null);

    final var configuration = new CustomChatModelConfiguration("acme", "m-1", parameters);
    parameters.put("later", 2);

    assertThat(configuration.parameters())
        .containsOnlyKeys("present", "absent")
        .containsEntry("absent", null);
  }

  @Test
  void doesNotPrintTheParameterValues() {
    final var configuration =
        new CustomChatModelConfiguration("acme", "m-1", Map.of("apiKey", "secret-value"));

    assertThat(configuration.toString()).contains("apiKey").doesNotContain("secret-value");
  }
}
