/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model.request;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.agenticai.aiagent.model.request.SandboxConfiguration.DaytonaSandboxConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.SandboxConfiguration.DisabledSandboxConfiguration;
import io.camunda.connector.agenticai.util.TestObjectMapperSupplier;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link SandboxConfiguration} — Jackson round-trip and toString redaction. */
class SandboxConfigurationTest {

  private final ObjectMapper objectMapper = TestObjectMapperSupplier.getInstance();

  @Test
  void daytonaSandboxConfiguration_deserializesFromJson() throws Exception {
    String json =
        """
        {
          "type": "daytona",
          "apiKey": "secret-key",
          "apiUrl": "https://my-daytona.example.com",
          "snapshot": "my-snapshot",
          "autoStopMinutes": 30,
          "autoArchiveMinutes": 60
        }
        """;

    SandboxConfiguration result = objectMapper.readValue(json, SandboxConfiguration.class);

    assertThat(result).isInstanceOf(DaytonaSandboxConfiguration.class);
    DaytonaSandboxConfiguration daytona = (DaytonaSandboxConfiguration) result;
    assertThat(daytona.apiKey()).isEqualTo("secret-key");
    assertThat(daytona.apiUrl()).isEqualTo("https://my-daytona.example.com");
    assertThat(daytona.snapshot()).isEqualTo("my-snapshot");
    assertThat(daytona.autoStopMinutes()).isEqualTo(30);
    assertThat(daytona.autoArchiveMinutes()).isEqualTo(60);
  }

  @Test
  void daytonaSandboxConfiguration_deserializesWithNullOptionals() throws Exception {
    String json =
        """
        {
          "type": "daytona",
          "apiKey": "secret-key"
        }
        """;

    SandboxConfiguration result = objectMapper.readValue(json, SandboxConfiguration.class);

    assertThat(result).isInstanceOf(DaytonaSandboxConfiguration.class);
    DaytonaSandboxConfiguration daytona = (DaytonaSandboxConfiguration) result;
    assertThat(daytona.apiKey()).isEqualTo("secret-key");
    assertThat(daytona.apiUrl()).isNull();
    assertThat(daytona.snapshot()).isNull();
    assertThat(daytona.autoStopMinutes()).isNull();
    assertThat(daytona.autoArchiveMinutes()).isNull();
  }

  @Test
  void daytonaSandboxConfiguration_roundTrip() throws Exception {
    DaytonaSandboxConfiguration original =
        new DaytonaSandboxConfiguration("my-api-key", null, "snap-v1", 15, null);

    String serialized = objectMapper.writeValueAsString(original);
    SandboxConfiguration deserialized =
        objectMapper.readValue(serialized, SandboxConfiguration.class);

    assertThat(deserialized).isInstanceOf(DaytonaSandboxConfiguration.class);
    DaytonaSandboxConfiguration daytona = (DaytonaSandboxConfiguration) deserialized;
    assertThat(daytona.apiKey()).isEqualTo("my-api-key");
    assertThat(daytona.snapshot()).isEqualTo("snap-v1");
    assertThat(daytona.autoStopMinutes()).isEqualTo(15);
  }

  @Test
  void daytonaSandboxConfiguration_providerTypeIsCorrect() {
    DaytonaSandboxConfiguration config =
        new DaytonaSandboxConfiguration("key", null, null, null, null);
    assertThat(config.providerType()).isEqualTo("daytona");
    assertThat(DaytonaSandboxConfiguration.TYPE).isEqualTo("daytona");
  }

  @Test
  void daytonaSandboxConfiguration_toStringRedactsApiKey() {
    DaytonaSandboxConfiguration config =
        new DaytonaSandboxConfiguration("super-secret", "https://api.example.com", null, 15, null);

    String str = config.toString();

    assertThat(str).doesNotContain("super-secret");
    assertThat(str).contains("[REDACTED]");
    assertThat(str).contains("https://api.example.com");
    assertThat(str).contains("15");
  }

  @Test
  void disabledSandboxConfiguration_deserializesFromJson() throws Exception {
    String json =
        """
        {"type": "disabled"}
        """;

    SandboxConfiguration result = objectMapper.readValue(json, SandboxConfiguration.class);

    assertThat(result).isInstanceOf(DisabledSandboxConfiguration.class);
    assertThat(result.providerType()).isEqualTo("disabled");
  }

  @Test
  void disabledSandboxConfiguration_roundTrip() throws Exception {
    DisabledSandboxConfiguration original = new DisabledSandboxConfiguration();

    String serialized = objectMapper.writeValueAsString(original);
    SandboxConfiguration deserialized =
        objectMapper.readValue(serialized, SandboxConfiguration.class);

    assertThat(deserialized).isInstanceOf(DisabledSandboxConfiguration.class);
  }
}
