/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model.request.v2;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.agenticai.aiagent.model.request.v2.MistralChatModelConfiguration.MistralBackend.MistralApiBackend;
import io.camunda.connector.agenticai.aiagent.model.request.v2.MistralChatModelConfiguration.MistralBackend.MistralApiBackend.MistralApiConnection;
import io.camunda.connector.agenticai.aiagent.model.request.v2.MistralChatModelConfiguration.MistralConnection;
import io.camunda.connector.agenticai.aiagent.model.request.v2.MistralChatModelConfiguration.MistralEffort;
import io.camunda.connector.agenticai.aiagent.model.request.v2.MistralChatModelConfiguration.MistralModel;
import io.camunda.connector.agenticai.aiagent.model.request.v2.MistralChatModelConfiguration.MistralParameters;
import io.camunda.connector.agenticai.aiagent.util.ConnectorUtils;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.validation.autoconfigure.ValidationAutoConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import uk.org.webcompere.systemstubs.environment.EnvironmentVariables;
import uk.org.webcompere.systemstubs.jupiter.SystemStub;
import uk.org.webcompere.systemstubs.jupiter.SystemStubsExtension;

@ExtendWith({SpringExtension.class, SystemStubsExtension.class})
@Import(ValidationAutoConfiguration.class)
class MistralChatModelConfigurationTest {

  private final ObjectMapper mapper = new ObjectMapper();

  @Autowired private Validator validator;
  @SystemStub private EnvironmentVariables environment;

  @BeforeEach
  void setUp() {
    environment.set(ConnectorUtils.CONNECTOR_RUNTIME_SAAS_ENV_VARIABLE, null);
  }

  private static MistralChatModelConfiguration configuration(String apiKey, String model) {
    return new MistralChatModelConfiguration(
        new MistralConnection(
            new MistralApiBackend(new MistralApiConnection(apiKey, null, null, null, null)),
            new MistralModel(model),
            null,
            null));
  }

  @Test
  void rejectsBlankModel() {
    final var config = configuration("sk-test", "");

    assertThat(validator.validate(config))
        .anySatisfy(
            v -> {
              assertThat(v.getPropertyPath().toString()).isEqualTo("mistral.model.model");
              assertThat(v.getMessage()).isEqualTo("must not be blank");
            });
  }

  @Test
  void rejectsBlankApiKey() {
    final var config = configuration("", "mistral-medium-latest");

    final Set<ConstraintViolation<MistralChatModelConfiguration>> violations =
        validator.validate(config);

    assertThat(violations)
        .anySatisfy(
            v -> {
              assertThat(v.getPropertyPath().toString())
                  .isEqualTo("mistral.backend.mistral.apiKey");
              assertThat(v.getMessage()).isEqualTo("must not be blank");
            });
  }

  @Test
  void acceptsMinimalValidConfiguration() {
    final var config = configuration("sk-test", "mistral-medium-latest");

    assertThat(validator.validate(config)).isEmpty();
  }

  @Test
  void redactsApiKeyAndBodyPropertiesInToString() {
    final var connection =
        new MistralApiConnection(
            "sk-secret",
            null,
            Map.of("Authorization", "sensitive"),
            Map.of("api-version", "sensitive"),
            Map.of("large_field", "sensitive"));

    assertThat(connection.toString())
        .contains("[REDACTED]")
        .doesNotContain("sk-secret")
        .doesNotContain("sensitive");
  }

  @Test
  void reportsProviderAndDescriptiveProvider() {
    final var config = configuration("sk-test", "mistral-medium-latest");

    assertThat(config.provider()).isEqualTo("mistral");
    assertThat(config.descriptiveProvider()).isEqualTo("mistral/mistral-api");
    assertThat(config.model()).isEqualTo("mistral-medium-latest");
  }

  @Test
  void deserialisesMistralApiBackendAndRoundTrips() throws Exception {
    final String json =
        """
        {
          "type": "mistral",
          "mistral": {
            "backend": { "type": "mistral-api", "mistral": { "apiKey": "sk-test-123" } },
            "model": { "model": "mistral-medium-latest" },
            "parameters": { "effort": "high", "maxTokens": 512, "temperature": 0.5, "topP": 0.9 }
          }
        }
        """;

    final ProviderConfiguration parsed = mapper.readValue(json, ProviderConfiguration.class);

    assertThat(parsed).isInstanceOf(MistralChatModelConfiguration.class);
    assertThat(parsed.provider()).isEqualTo("mistral");
    assertThat(parsed.descriptiveProvider()).isEqualTo("mistral/mistral-api");
    assertThat(parsed.model()).isEqualTo("mistral-medium-latest");

    final MistralChatModelConfiguration mistral = (MistralChatModelConfiguration) parsed;
    assertThat(mistral.mistral().backend()).isInstanceOf(MistralApiBackend.class);
    assertThat(((MistralApiBackend) mistral.mistral().backend()).mistral().apiKey())
        .isEqualTo("sk-test-123");
    final MistralParameters parameters = mistral.mistral().parameters();
    assertThat(parameters.effort()).isEqualTo(MistralEffort.HIGH);
    assertThat(parameters.maxTokens()).isEqualTo(512);
    assertThat(parameters.temperature()).isEqualTo(0.5);
    assertThat(parameters.topP()).isEqualTo(0.9);

    final String reserialised = mapper.writeValueAsString(parsed);
    assertThat(mapper.readValue(reserialised, ProviderConfiguration.class)).isEqualTo(parsed);
  }

  @Test
  void handlesNullParametersWithoutError() throws Exception {
    final String json =
        """
        {
          "type": "mistral",
          "mistral": {
            "backend": { "type": "mistral-api", "mistral": { "apiKey": "sk-test-123" } },
            "model": { "model": "mistral-medium-latest" }
          }
        }
        """;

    final MistralChatModelConfiguration parsed =
        (MistralChatModelConfiguration) mapper.readValue(json, ProviderConfiguration.class);

    assertThat(parsed.mistral().parameters()).isNull();
  }
}
