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
import io.camunda.connector.agenticai.aiagent.model.request.SandboxConfiguration.DaytonaSandboxConfiguration.AutoArchiveMode;
import io.camunda.connector.agenticai.aiagent.model.request.SandboxConfiguration.DaytonaSandboxConfiguration.AutoDeleteMode;
import io.camunda.connector.agenticai.aiagent.model.request.SandboxConfiguration.DaytonaSandboxConfiguration.AutoStopMode;
import io.camunda.connector.agenticai.aiagent.model.request.SandboxConfiguration.DisabledSandboxConfiguration;
import io.camunda.connector.agenticai.util.TestObjectMapperSupplier;
import io.camunda.connector.api.error.ConnectorException;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link SandboxConfiguration} — Jackson round-trip, toString redaction, and
 * conversion helpers.
 */
class SandboxConfigurationTest {

  private final ObjectMapper objectMapper = TestObjectMapperSupplier.getInstance();

  // -------------------------------------------------------------------------
  // Deserialization
  // -------------------------------------------------------------------------

  @Test
  void daytonaSandboxConfiguration_deserializesFromJson() throws Exception {
    String json =
        """
        {
          "type": "daytona",
          "apiKey": "secret-key",
          "apiUrl": "https://my-daytona.example.com",
          "snapshot": "my-snapshot",
          "autoStop": "DURATION",
          "autoStopDuration": "PT30M",
          "autoArchive": "DURATION",
          "autoArchiveDuration": "PT60M",
          "autoDelete": "IMMEDIATELY"
        }
        """;

    SandboxConfiguration result = objectMapper.readValue(json, SandboxConfiguration.class);

    assertThat(result).isInstanceOf(DaytonaSandboxConfiguration.class);
    DaytonaSandboxConfiguration daytona = (DaytonaSandboxConfiguration) result;
    assertThat(daytona.apiKey()).isEqualTo("secret-key");
    assertThat(daytona.apiUrl()).isEqualTo("https://my-daytona.example.com");
    assertThat(daytona.snapshot()).isEqualTo("my-snapshot");
    assertThat(daytona.autoStop()).isEqualTo(AutoStopMode.DURATION);
    assertThat(daytona.autoStopDuration()).isEqualTo("PT30M");
    assertThat(daytona.autoArchive()).isEqualTo(AutoArchiveMode.DURATION);
    assertThat(daytona.autoArchiveDuration()).isEqualTo("PT60M");
    assertThat(daytona.autoDelete()).isEqualTo(AutoDeleteMode.IMMEDIATELY);
    assertThat(daytona.autoDeleteDuration()).isNull();
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
    assertThat(daytona.autoStop()).isNull();
    assertThat(daytona.autoStopDuration()).isNull();
    assertThat(daytona.autoArchive()).isNull();
    assertThat(daytona.autoArchiveDuration()).isNull();
    assertThat(daytona.autoDelete()).isNull();
    assertThat(daytona.autoDeleteDuration()).isNull();
  }

  // -------------------------------------------------------------------------
  // Round-trip
  // -------------------------------------------------------------------------

  @Test
  void daytonaSandboxConfiguration_roundTrip() throws Exception {
    DaytonaSandboxConfiguration original =
        new DaytonaSandboxConfiguration(
            "my-api-key", null, "snap-v1", AutoStopMode.DURATION, "PT15M", null, null, null, null);

    String serialized = objectMapper.writeValueAsString(original);
    SandboxConfiguration deserialized =
        objectMapper.readValue(serialized, SandboxConfiguration.class);

    assertThat(deserialized).isInstanceOf(DaytonaSandboxConfiguration.class);
    DaytonaSandboxConfiguration daytona = (DaytonaSandboxConfiguration) deserialized;
    assertThat(daytona.apiKey()).isEqualTo("my-api-key");
    assertThat(daytona.snapshot()).isEqualTo("snap-v1");
    assertThat(daytona.autoStop()).isEqualTo(AutoStopMode.DURATION);
    assertThat(daytona.autoStopDuration()).isEqualTo("PT15M");
  }

  @Test
  void daytonaSandboxConfiguration_providerTypeIsCorrect() {
    DaytonaSandboxConfiguration config =
        new DaytonaSandboxConfiguration("key", null, null, null, null, null, null, null, null);
    assertThat(config.providerType()).isEqualTo("daytona");
    assertThat(DaytonaSandboxConfiguration.TYPE).isEqualTo("daytona");
  }

  @Test
  void daytonaSandboxConfiguration_toStringRedactsApiKey() {
    DaytonaSandboxConfiguration config =
        new DaytonaSandboxConfiguration(
            "super-secret",
            "https://api.example.com",
            null,
            AutoStopMode.DURATION,
            "PT15M",
            null,
            null,
            null,
            null);

    String str = config.toString();

    assertThat(str).doesNotContain("super-secret");
    assertThat(str).contains("[REDACTED]");
    assertThat(str).contains("https://api.example.com");
    assertThat(str).contains("DURATION");
    assertThat(str).contains("PT15M");
  }

  // -------------------------------------------------------------------------
  // autoStopMinutes() conversion
  // -------------------------------------------------------------------------

  @Test
  void autoStopMinutes_disabled_returnsZero() {
    var cfg =
        new DaytonaSandboxConfiguration(
            "k", null, null, AutoStopMode.DISABLED, null, null, null, null, null);
    assertThat(cfg.autoStopMinutes()).isEqualTo(0);
  }

  @Test
  void autoStopMinutes_durationExplicit_returnsParsedMinutes() {
    var cfg =
        new DaytonaSandboxConfiguration(
            "k", null, null, AutoStopMode.DURATION, "PT15M", null, null, null, null);
    assertThat(cfg.autoStopMinutes()).isEqualTo(15);
  }

  @Test
  void autoStopMinutes_durationNullUsesDefault() {
    // null mode + null duration → default PT15M
    var cfg = new DaytonaSandboxConfiguration("k", null, null, null, null, null, null, null, null);
    assertThat(cfg.autoStopMinutes()).isEqualTo(15);
  }

  @Test
  void autoStopMinutes_durationBlankUsesDefault() {
    var cfg =
        new DaytonaSandboxConfiguration(
            "k", null, null, AutoStopMode.DURATION, "  ", null, null, null, null);
    assertThat(cfg.autoStopMinutes()).isEqualTo(15);
  }

  // -------------------------------------------------------------------------
  // autoArchiveMinutes() conversion
  // -------------------------------------------------------------------------

  @Test
  void autoArchiveMinutes_default_returnsNull() {
    var cfg =
        new DaytonaSandboxConfiguration(
            "k", null, null, null, null, AutoArchiveMode.DEFAULT, null, null, null);
    assertThat(cfg.autoArchiveMinutes()).isNull();
  }

  @Test
  void autoArchiveMinutes_nullMode_returnsNull() {
    var cfg = new DaytonaSandboxConfiguration("k", null, null, null, null, null, null, null, null);
    assertThat(cfg.autoArchiveMinutes()).isNull();
  }

  @Test
  void autoArchiveMinutes_duration7Days_returns10080() {
    var cfg =
        new DaytonaSandboxConfiguration(
            "k", null, null, null, null, AutoArchiveMode.DURATION, "P7D", null, null);
    assertThat(cfg.autoArchiveMinutes()).isEqualTo(7 * 24 * 60);
  }

  @Test
  void autoArchiveMinutes_durationNullUsesDefault7Days() {
    var cfg =
        new DaytonaSandboxConfiguration(
            "k", null, null, null, null, AutoArchiveMode.DURATION, null, null, null);
    assertThat(cfg.autoArchiveMinutes()).isEqualTo(7 * 24 * 60);
  }

  @Test
  void autoArchiveMinutes_durationExceeds30Days_throwsConnectorException() {
    var cfg =
        new DaytonaSandboxConfiguration(
            "k", null, null, null, null, AutoArchiveMode.DURATION, "P31D", null, null);
    assertThatThrownBy(cfg::autoArchiveMinutes)
        .isInstanceOf(ConnectorException.class)
        .hasMessageContaining("30 days");
  }

  // -------------------------------------------------------------------------
  // autoDeleteMinutes() conversion
  // -------------------------------------------------------------------------

  @Test
  void autoDeleteMinutes_disabled_returnsNull() {
    var cfg =
        new DaytonaSandboxConfiguration(
            "k", null, null, null, null, null, null, AutoDeleteMode.DISABLED, null);
    assertThat(cfg.autoDeleteMinutes()).isNull();
  }

  @Test
  void autoDeleteMinutes_nullMode_returnsNull() {
    var cfg = new DaytonaSandboxConfiguration("k", null, null, null, null, null, null, null, null);
    assertThat(cfg.autoDeleteMinutes()).isNull();
  }

  @Test
  void autoDeleteMinutes_immediately_returnsZero() {
    var cfg =
        new DaytonaSandboxConfiguration(
            "k", null, null, null, null, null, null, AutoDeleteMode.IMMEDIATELY, null);
    assertThat(cfg.autoDeleteMinutes()).isEqualTo(0);
  }

  @Test
  void autoDeleteMinutes_duration30Min_returns30() {
    var cfg =
        new DaytonaSandboxConfiguration(
            "k", null, null, null, null, null, null, AutoDeleteMode.DURATION, "PT30M");
    assertThat(cfg.autoDeleteMinutes()).isEqualTo(30);
  }

  @Test
  void autoDeleteMinutes_durationBlank_throwsConnectorException() {
    var cfg =
        new DaytonaSandboxConfiguration(
            "k", null, null, null, null, null, null, AutoDeleteMode.DURATION, "");
    assertThatThrownBy(cfg::autoDeleteMinutes)
        .isInstanceOf(ConnectorException.class)
        .hasMessageContaining("required");
  }

  @Test
  void autoDeleteMinutes_durationNull_throwsConnectorException() {
    var cfg =
        new DaytonaSandboxConfiguration(
            "k", null, null, null, null, null, null, AutoDeleteMode.DURATION, null);
    assertThatThrownBy(cfg::autoDeleteMinutes)
        .isInstanceOf(ConnectorException.class)
        .hasMessageContaining("required");
  }

  // -------------------------------------------------------------------------
  // Invalid ISO-8601 duration handling
  // -------------------------------------------------------------------------

  @Test
  void autoStopMinutes_invalidIso8601_throwsConnectorException() {
    var cfg =
        new DaytonaSandboxConfiguration(
            "k", null, null, AutoStopMode.DURATION, "not-a-duration", null, null, null, null);
    assertThatThrownBy(cfg::autoStopMinutes)
        .isInstanceOf(ConnectorException.class)
        .hasMessageContaining("autoStopDuration");
  }

  @Test
  void autoArchiveMinutes_invalidIso8601_throwsConnectorException() {
    var cfg =
        new DaytonaSandboxConfiguration(
            "k", null, null, null, null, AutoArchiveMode.DURATION, "bad-value", null, null);
    assertThatThrownBy(cfg::autoArchiveMinutes)
        .isInstanceOf(ConnectorException.class)
        .hasMessageContaining("autoArchiveDuration");
  }

  @Test
  void autoDeleteMinutes_invalidIso8601_throwsConnectorException() {
    var cfg =
        new DaytonaSandboxConfiguration(
            "k", null, null, null, null, null, null, AutoDeleteMode.DURATION, "bad-value");
    assertThatThrownBy(cfg::autoDeleteMinutes)
        .isInstanceOf(ConnectorException.class)
        .hasMessageContaining("autoDeleteDuration");
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
