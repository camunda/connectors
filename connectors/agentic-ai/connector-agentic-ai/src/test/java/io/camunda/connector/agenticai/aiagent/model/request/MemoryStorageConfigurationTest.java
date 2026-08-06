/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model.request;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.agenticai.aiagent.model.request.MemoryStorageConfiguration.CustomMemoryStorageConfiguration;
import org.junit.jupiter.api.Test;

class MemoryStorageConfigurationTest {

  private final ObjectMapper mapper = new ObjectMapper();

  @Test
  void deserialisesCustomStorageWithoutParametersToAnEmptyMap() throws Exception {
    final String json =
        """
        {
          "type": "custom",
          "storeType": "acme-store"
        }
        """;

    final MemoryStorageConfiguration parsed =
        mapper.readValue(json, MemoryStorageConfiguration.class);

    assertThat(parsed).isInstanceOf(CustomMemoryStorageConfiguration.class);
    final CustomMemoryStorageConfiguration custom = (CustomMemoryStorageConfiguration) parsed;
    assertThat(custom.storeType()).isEqualTo("acme-store");
    assertThat(custom.parameters()).isNotNull().isEmpty();
  }

  @Test
  void deserialisesCustomStorageWithParameters() throws Exception {
    final String json =
        """
        {
          "type": "custom",
          "storeType": "acme-store",
          "parameters": { "endpoint": "https://acme.example.com" }
        }
        """;

    final MemoryStorageConfiguration parsed =
        mapper.readValue(json, MemoryStorageConfiguration.class);

    final CustomMemoryStorageConfiguration custom = (CustomMemoryStorageConfiguration) parsed;
    assertThat(custom.parameters()).containsEntry("endpoint", "https://acme.example.com");
  }
}
