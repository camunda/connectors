/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model.request;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.agenticai.aiagent.model.request.SandboxConfiguration.DaytonaSandboxConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.SandboxConfiguration.DaytonaSandboxConfiguration.AutoArchiveConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.SandboxConfiguration.DaytonaSandboxConfiguration.AutoArchiveMode;
import io.camunda.connector.agenticai.aiagent.model.request.SandboxConfiguration.DaytonaSandboxConfiguration.AutoDeleteConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.SandboxConfiguration.DaytonaSandboxConfiguration.AutoDeleteMode;
import io.camunda.connector.agenticai.aiagent.model.request.SandboxConfiguration.DaytonaSandboxConfiguration.AutoStopConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.SandboxConfiguration.DaytonaSandboxConfiguration.AutoStopMode;
import io.camunda.connector.agenticai.aiagent.model.request.SandboxConfiguration.DaytonaSandboxConfiguration.DaytonaConnection;
import io.camunda.connector.agenticai.aiagent.model.request.SandboxConfiguration.DisabledSandboxConfiguration;
import io.camunda.connector.agenticai.util.TestObjectMapperSupplier;
import io.camunda.connector.api.error.ConnectorException;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link SandboxConfiguration} — Jackson round-trip, toString redaction, and
 * conversion helpers. Daytona settings live one level deeper under {@code daytona} (binding prefix
 * {@code data.sandbox.daytona.}), and lifecycle settings are grouped into mode + duration objects.
 */
class SandboxConfigurationTest {

  private final ObjectMapper objectMapper = TestObjectMapperSupplier.getInstance();

  private static DaytonaConnection connection(
      AutoStopConfiguration autoStop,
      AutoArchiveConfiguration autoArchive,
      AutoDeleteConfiguration autoDelete) {
    return new DaytonaConnection("k", null, null, autoStop, autoArchive, autoDelete);
  }

  // -------------------------------------------------------------------------
  // Deserialization
  // -------------------------------------------------------------------------

  @Test
  void daytonaSandboxConfiguration_deserializesFromJson() throws Exception {
    String json =
        """
        {
          "type": "daytona",
          "daytona": {
            "apiKey": "secret-key",
            "apiUrl": "https://my-daytona.example.com",
            "snapshot": "my-snapshot",
            "autoStop": { "mode": "DURATION", "duration": "PT30M" },
            "autoArchive": { "mode": "DURATION", "duration": "PT60M" },
            "autoDelete": { "mode": "IMMEDIATELY" }
          }
        }
        """;

    SandboxConfiguration result = objectMapper.readValue(json, SandboxConfiguration.class);

    assertThat(result).isInstanceOf(DaytonaSandboxConfiguration.class);
    DaytonaConnection daytona = ((DaytonaSandboxConfiguration) result).daytona();
    assertThat(daytona.apiKey()).isEqualTo("secret-key");
    assertThat(daytona.apiUrl()).isEqualTo("https://my-daytona.example.com");
    assertThat(daytona.snapshot()).isEqualTo("my-snapshot");
    assertThat(daytona.autoStop())
        .isEqualTo(new AutoStopConfiguration(AutoStopMode.DURATION, "PT30M"));
    assertThat(daytona.autoArchive())
        .isEqualTo(new AutoArchiveConfiguration(AutoArchiveMode.DURATION, "PT60M"));
    assertThat(daytona.autoDelete())
        .isEqualTo(new AutoDeleteConfiguration(AutoDeleteMode.IMMEDIATELY, null));
  }

  @Test
  void daytonaSandboxConfiguration_deserializesWithNullOptionals() throws Exception {
    String json =
        """
        {
          "type": "daytona",
          "daytona": { "apiKey": "secret-key" }
        }
        """;

    SandboxConfiguration result = objectMapper.readValue(json, SandboxConfiguration.class);

    assertThat(result).isInstanceOf(DaytonaSandboxConfiguration.class);
    DaytonaConnection daytona = ((DaytonaSandboxConfiguration) result).daytona();
    assertThat(daytona.apiKey()).isEqualTo("secret-key");
    assertThat(daytona.apiUrl()).isNull();
    assertThat(daytona.snapshot()).isNull();
    assertThat(daytona.autoStop()).isNull();
    assertThat(daytona.autoArchive()).isNull();
    assertThat(daytona.autoDelete()).isNull();
  }

  // -------------------------------------------------------------------------
  // Round-trip
  // -------------------------------------------------------------------------

  @Test
  void daytonaSandboxConfiguration_roundTrip() throws Exception {
    DaytonaSandboxConfiguration original =
        new DaytonaSandboxConfiguration(
            new DaytonaConnection(
                "my-api-key",
                null,
                "snap-v1",
                new AutoStopConfiguration(AutoStopMode.DURATION, "PT15M"),
                null,
                null));

    String serialized = objectMapper.writeValueAsString(original);
    SandboxConfiguration deserialized =
        objectMapper.readValue(serialized, SandboxConfiguration.class);

    assertThat(deserialized).isInstanceOf(DaytonaSandboxConfiguration.class);
    DaytonaConnection daytona = ((DaytonaSandboxConfiguration) deserialized).daytona();
    assertThat(daytona.apiKey()).isEqualTo("my-api-key");
    assertThat(daytona.snapshot()).isEqualTo("snap-v1");
    assertThat(daytona.autoStop())
        .isEqualTo(new AutoStopConfiguration(AutoStopMode.DURATION, "PT15M"));
  }

  @Test
  void daytonaSandboxConfiguration_providerTypeIsCorrect() {
    DaytonaSandboxConfiguration config =
        new DaytonaSandboxConfiguration(connection(null, null, null));
    assertThat(config.providerType()).isEqualTo("daytona");
    assertThat(DaytonaSandboxConfiguration.TYPE).isEqualTo("daytona");
  }

  @Test
  void daytonaConnection_toStringRedactsApiKey() {
    DaytonaConnection connection =
        new DaytonaConnection(
            "super-secret",
            "https://api.example.com",
            null,
            new AutoStopConfiguration(AutoStopMode.DURATION, "PT15M"),
            null,
            null);

    String str = connection.toString();

    assertThat(str).doesNotContain("super-secret");
    assertThat(str).contains("[REDACTED]");
    assertThat(str).contains("https://api.example.com");
    assertThat(str).contains("PT15M");
  }

  // -------------------------------------------------------------------------
  // autoStopMinutes() conversion
  // -------------------------------------------------------------------------

  @Test
  void autoStopMinutes_disabled_returnsZero() {
    var conn = connection(new AutoStopConfiguration(AutoStopMode.DISABLED, null), null, null);
    assertThat(conn.autoStopMinutes()).isEqualTo(0);
  }

  @Test
  void autoStopMinutes_durationExplicit_returnsParsedMinutes() {
    var conn = connection(new AutoStopConfiguration(AutoStopMode.DURATION, "PT15M"), null, null);
    assertThat(conn.autoStopMinutes()).isEqualTo(15);
  }

  @Test
  void autoStopMinutes_subObjectNullUsesDefault() {
    // autoStop sub-object absent → default DURATION PT15M
    var conn = connection(null, null, null);
    assertThat(conn.autoStopMinutes()).isEqualTo(15);
  }

  @Test
  void autoStopMinutes_nullModeUsesDefault() {
    var conn = connection(new AutoStopConfiguration(null, null), null, null);
    assertThat(conn.autoStopMinutes()).isEqualTo(15);
  }

  @Test
  void autoStopMinutes_durationBlankUsesDefault() {
    var conn = connection(new AutoStopConfiguration(AutoStopMode.DURATION, "  "), null, null);
    assertThat(conn.autoStopMinutes()).isEqualTo(15);
  }

  // -------------------------------------------------------------------------
  // autoArchiveMinutes() conversion
  // -------------------------------------------------------------------------

  @Test
  void autoArchiveMinutes_default_returnsNull() {
    var conn = connection(null, new AutoArchiveConfiguration(AutoArchiveMode.DEFAULT, null), null);
    assertThat(conn.autoArchiveMinutes()).isNull();
  }

  @Test
  void autoArchiveMinutes_subObjectNull_returnsNull() {
    var conn = connection(null, null, null);
    assertThat(conn.autoArchiveMinutes()).isNull();
  }

  @Test
  void autoArchiveMinutes_duration7Days_returns10080() {
    var conn =
        connection(null, new AutoArchiveConfiguration(AutoArchiveMode.DURATION, "P7D"), null);
    assertThat(conn.autoArchiveMinutes()).isEqualTo(7 * 24 * 60);
  }

  @Test
  void autoArchiveMinutes_durationNullUsesDefault7Days() {
    var conn = connection(null, new AutoArchiveConfiguration(AutoArchiveMode.DURATION, null), null);
    assertThat(conn.autoArchiveMinutes()).isEqualTo(7 * 24 * 60);
  }

  @Test
  void autoArchiveMinutes_durationExceeds30Days_throwsConnectorException() {
    var conn =
        connection(null, new AutoArchiveConfiguration(AutoArchiveMode.DURATION, "P31D"), null);
    assertThatThrownBy(conn::autoArchiveMinutes)
        .isInstanceOf(ConnectorException.class)
        .hasMessageContaining("30 days");
  }

  // -------------------------------------------------------------------------
  // autoDeleteMinutes() conversion
  // -------------------------------------------------------------------------

  @Test
  void autoDeleteMinutes_disabled_returnsNull() {
    var conn = connection(null, null, new AutoDeleteConfiguration(AutoDeleteMode.DISABLED, null));
    assertThat(conn.autoDeleteMinutes()).isNull();
  }

  @Test
  void autoDeleteMinutes_subObjectNull_returnsNull() {
    var conn = connection(null, null, null);
    assertThat(conn.autoDeleteMinutes()).isNull();
  }

  @Test
  void autoDeleteMinutes_immediately_returnsZero() {
    var conn =
        connection(null, null, new AutoDeleteConfiguration(AutoDeleteMode.IMMEDIATELY, null));
    assertThat(conn.autoDeleteMinutes()).isEqualTo(0);
  }

  @Test
  void autoDeleteMinutes_duration30Min_returns30() {
    var conn =
        connection(null, null, new AutoDeleteConfiguration(AutoDeleteMode.DURATION, "PT30M"));
    assertThat(conn.autoDeleteMinutes()).isEqualTo(30);
  }

  @Test
  void autoDeleteMinutes_durationBlankUsesDefault5Min() {
    var conn = connection(null, null, new AutoDeleteConfiguration(AutoDeleteMode.DURATION, ""));
    assertThat(conn.autoDeleteMinutes()).isEqualTo(5);
  }

  @Test
  void autoDeleteMinutes_durationNullUsesDefault5Min() {
    var conn = connection(null, null, new AutoDeleteConfiguration(AutoDeleteMode.DURATION, null));
    assertThat(conn.autoDeleteMinutes()).isEqualTo(5);
  }

  // -------------------------------------------------------------------------
  // Invalid ISO-8601 duration handling
  // -------------------------------------------------------------------------

  @Test
  void autoStopMinutes_invalidIso8601_throwsConnectorException() {
    var conn =
        connection(new AutoStopConfiguration(AutoStopMode.DURATION, "not-a-duration"), null, null);
    assertThatThrownBy(conn::autoStopMinutes)
        .isInstanceOf(ConnectorException.class)
        .hasMessageContaining("autoStop.duration");
  }

  @Test
  void autoArchiveMinutes_invalidIso8601_throwsConnectorException() {
    var conn =
        connection(null, new AutoArchiveConfiguration(AutoArchiveMode.DURATION, "bad-value"), null);
    assertThatThrownBy(conn::autoArchiveMinutes)
        .isInstanceOf(ConnectorException.class)
        .hasMessageContaining("autoArchive.duration");
  }

  @Test
  void autoDeleteMinutes_invalidIso8601_throwsConnectorException() {
    var conn =
        connection(null, null, new AutoDeleteConfiguration(AutoDeleteMode.DURATION, "bad-value"));
    assertThatThrownBy(conn::autoDeleteMinutes)
        .isInstanceOf(ConnectorException.class)
        .hasMessageContaining("autoDelete.duration");
  }

  // -------------------------------------------------------------------------
  // Disabled sandbox
  // -------------------------------------------------------------------------

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
