/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AgentMetadataTest {

  @Test
  void normalizesNullConfigurationFingerprintHistoryToEmptyMap() {
    // simulates Jackson binding a pre-existing persisted AgentContext whose metadata JSON has no
    // configurationFingerprintHistory field yet (the field was added after such state was written)
    final var metadata = new AgentMetadata(1L, 1L, null, 3, null);

    assertThat(metadata.configurationFingerprintHistory()).isEmpty();
    assertThat(metadata.configurationFingerprintAt(3)).isNull();
  }
}
