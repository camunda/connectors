/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
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

  @Test
  void persistsTheAgentInstanceKeyAsANumber() throws Exception {
    final var objectMapper = new ObjectMapper();
    final var metadata = new AgentMetadata(1L, 2L, AgentInstanceKey.of(2251799813691441L), 3);

    final var json = objectMapper.writeValueAsString(metadata);
    final var restored = objectMapper.readValue(json, AgentMetadata.class);

    assertThat(objectMapper.readTree(json).path("agentInstanceKey").isNumber()).isTrue();
    assertThat(json).contains("\"agentInstanceKey\":2251799813691441");
    assertThat(restored).isEqualTo(metadata);
  }

  @Test
  void readsMetadataWrittenBeforeTheAgentInstanceKeyWasATypeAndWithoutOne() throws Exception {
    final var objectMapper = new ObjectMapper();

    final var withKey =
        objectMapper.readValue(
            "{\"processDefinitionKey\":1,\"processInstanceKey\":2,\"agentInstanceKey\":42}",
            AgentMetadata.class);
    final var withoutKey =
        objectMapper.readValue(
            "{\"processDefinitionKey\":1,\"processInstanceKey\":2}", AgentMetadata.class);

    assertThat(withKey.agentInstanceKey()).isEqualTo(AgentInstanceKey.of(42L));
    assertThat(withoutKey.agentInstanceKey()).isNull();
  }
}
