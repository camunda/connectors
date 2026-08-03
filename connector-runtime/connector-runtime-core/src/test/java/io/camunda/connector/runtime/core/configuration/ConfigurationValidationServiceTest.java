/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information regarding copyright
 * ownership. Camunda licenses this file to you under the Apache License,
 * Version 2.0; you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.camunda.connector.runtime.core.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.api.annotation.Configuration;
import io.camunda.connector.api.error.ConnectorException;
import io.camunda.connector.api.secret.SecretContext;
import io.camunda.connector.api.secret.SecretProvider;
import io.camunda.connector.api.validation.ConfigurationValidationResult;
import io.camunda.connector.api.validation.ConfigurationValidationResult.Status;
import io.camunda.connector.api.validation.ConfigurationValidator;
import io.camunda.connector.feel.FeelExpressionEvaluator;
import io.camunda.connector.runtime.core.validation.ValidationUtil;
import jakarta.validation.constraints.Size;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ConfigurationValidationServiceTest {

  @Configuration(id = "ok", name = "Ok")
  record OkConfig(String value) {}

  @Configuration(id = "throws", name = "Throws")
  record ThrowingConfig(String value) {}

  @Configuration(id = "constrained", name = "Constrained")
  record ConstrainedConfig(@Size(max = 3) String token) {}

  static class OkValidator implements ConfigurationValidator<OkConfig> {
    @Override
    public ConfigurationValidationResult validate(OkConfig configuration) {
      return ConfigurationValidationResult.success();
    }
  }

  static class ThrowingValidator implements ConfigurationValidator<ThrowingConfig> {
    @Override
    public ConfigurationValidationResult validate(ThrowingConfig configuration) {
      throw new ConnectorException("UNAUTHORIZED", "invalid key");
    }
  }

  /** Fails if ever invoked — proves bean validation short-circuits before the validator runs. */
  static class ConstrainedValidator implements ConfigurationValidator<ConstrainedConfig> {
    @Override
    public ConfigurationValidationResult validate(ConstrainedConfig configuration) {
      throw new AssertionError("validator must not run when bean validation already failed");
    }
  }

  @Configuration(id = "recording", name = "Recording")
  record RecordingConfig(String value) {}

  /** Captures the configuration that actually reached the validator. */
  static class RecordingValidator implements ConfigurationValidator<RecordingConfig> {
    private RecordingConfig seen;

    @Override
    public ConfigurationValidationResult validate(RecordingConfig configuration) {
      seen = configuration;
      return ConfigurationValidationResult.success();
    }
  }

  @Configuration(id = "null", name = "Null")
  record NullConfig(String value) {}

  /** The SDK contract does not forbid a null result; the service must not NPE on it. */
  static class NullReturningValidator implements ConfigurationValidator<NullConfig> {
    @Override
    public ConfigurationValidationResult validate(NullConfig configuration) {
      return null;
    }
  }

  private final ObjectMapper objectMapper = new ObjectMapper();

  private FeelExpressionEvaluator feelReturning(String json) {
    return new FeelExpressionEvaluator() {
      @Override
      public <T> T evaluate(String expression, Object... variables) {
        throw new UnsupportedOperationException();
      }

      @Override
      public <T> T evaluate(String expression, Class<T> targetType, Object... variables) {
        throw new UnsupportedOperationException();
      }

      @Override
      public <T> T evaluate(String expression, JavaType targetType, Object... variables) {
        throw new UnsupportedOperationException();
      }

      @Override
      public String evaluateToJson(String expression, Object... variables) {
        return json;
      }
    };
  }

  /** Records the {@link SecretContext} each lookup was made with, and resolves nothing. */
  private static final class RecordingSecretProvider implements SecretProvider {
    private final List<SecretContext> contexts = new ArrayList<>();

    @Override
    public String getSecret(String name, SecretContext context) {
      contexts.add(context);
      return null;
    }
  }

  private ConfigurationValidationService serviceWith(String resolvedJson) {
    return serviceWith(Map.of("engine-a", feelReturning(resolvedJson)));
  }

  private ConfigurationValidationService serviceWith(
      Map<String, FeelExpressionEvaluator> evaluatorsByPhysicalTenantId) {
    return serviceWith(evaluatorsByPhysicalTenantId, new RecordingSecretProvider());
  }

  private final RecordingValidator recordingValidator = new RecordingValidator();

  private ConfigurationValidationService serviceWith(
      Map<String, FeelExpressionEvaluator> evaluatorsByPhysicalTenantId,
      SecretProvider secretProvider) {
    var registry =
        new ConfigurationValidationRegistry(
            List.of(
                new OkValidator(),
                new ThrowingValidator(),
                new ConstrainedValidator(),
                new NullReturningValidator(),
                recordingValidator));
    return new ConfigurationValidationService(
        registry,
        evaluatorsByPhysicalTenantId,
        secretProvider,
        ValidationUtil.discoverDefaultValidationProviderImplementation(),
        objectMapper);
  }

  @Test
  void returnsSuccessWhenValidatorPasses() {
    var service = serviceWith("{\"value\":\"x\"}");

    var result =
        service.validate(new ConfigurationValidationRequest("ok", "=ref", "tenant", "engine-a"));

    assertThat(result.status()).isEqualTo(Status.SUCCESS);
  }

  @Test
  void keepsThrownErrorCodeButDoesNotLeakThrownMessage() {
    var service = serviceWith("{\"value\":\"x\"}");

    var result =
        service.validate(
            new ConfigurationValidationRequest("throws", "=ref", "tenant", "engine-a"));

    assertThat(result.status()).isEqualTo(Status.FAILURE);
    assertThat(result.code()).isEqualTo("UNAUTHORIZED");
    // The thrown exception's free-text message must never reach the client.
    assertThat(result.message()).doesNotContain("invalid key");
  }

  @Test
  void mapsNullValidatorResultToFailureInsteadOfNpe() {
    var service = serviceWith("{\"value\":\"x\"}");

    var result =
        service.validate(new ConfigurationValidationRequest("null", "=ref", "tenant", "engine-a"));

    assertThat(result).isNotNull();
    assertThat(result.status()).isEqualTo(Status.FAILURE);
    assertThat(result.code()).isEqualTo("ERROR");
  }

  @Test
  void returnsUnsupportedForUnknownConfigurationId() {
    var service = serviceWith("{\"value\":\"x\"}");

    var result =
        service.validate(
            new ConfigurationValidationRequest("unknown", "=ref", "tenant", "engine-a"));

    assertThat(result.status()).isEqualTo(Status.UNSUPPORTED);
  }

  @Test
  void returnsResolutionFailureWithoutLeakingResolvedContent() {
    // The resolved value is invalid JSON; Jackson's error would echo it, so it must be suppressed.
    var service = serviceWith("this-is-not-json-and-could-be-a-secret");

    var result =
        service.validate(new ConfigurationValidationRequest("ok", "=ref", "tenant", "engine-a"));

    assertThat(result.status()).isEqualTo(Status.FAILURE);
    assertThat(result.code()).isEqualTo("RESOLUTION_ERROR");
    assertThat(result.message()).doesNotContain("this-is-not-json-and-could-be-a-secret");
  }

  @Test
  void evaluatesTheReferenceAgainstTheRequestedPhysicalTenantsEngine() {
    // Each engine holds its own camunda.vars.env.*, so resolving against the wrong one would
    // validate an entirely different configuration.
    var service =
        serviceWith(
            Map.of(
                "engine-a", feelReturning("{\"value\":\"from-engine-a\"}"),
                "engine-b", feelReturning("{\"value\":\"from-engine-b\"}")));

    var result =
        service.validate(
            new ConfigurationValidationRequest("recording", "=ref", "tenant", "engine-b"));

    assertThat(result.status()).isEqualTo(Status.SUCCESS);
    assertThat(recordingValidator.seen.value()).isEqualTo("from-engine-b");
  }

  @Test
  void resolvesSecretsInTheRequestedPhysicalTenantsScope() {
    var secretProvider = new RecordingSecretProvider();
    var service =
        serviceWith(
            Map.of("engine-b", feelReturning("{\"value\":\"{{secrets.TOKEN}}\"}")), secretProvider);

    service.validate(new ConfigurationValidationRequest("ok", "=ref", "tenant", "engine-b"));

    assertThat(secretProvider.contexts)
        .isNotEmpty()
        .allSatisfy(
            context -> {
              assertThat(context.physicalTenantId()).isEqualTo("engine-b");
              assertThat(context.tenantId()).isEqualTo("tenant");
            });
  }

  @Test
  void usesTheOnlyConfiguredEngineWhenThePhysicalTenantIsOmitted() {
    // Single-engine deployments, and callers predating multi-engine support, omit the field.
    var service = serviceWith(Map.of("engine-a", feelReturning("{\"value\":\"x\"}")));

    var result = service.validate(new ConfigurationValidationRequest("ok", "=ref", "tenant", null));

    assertThat(result.status()).isEqualTo(Status.SUCCESS);
  }

  @Test
  void treatsABlankPhysicalTenantAsOmitted() {
    // Clients report an unset physical tenant as an empty string.
    var service = serviceWith(Map.of("engine-a", feelReturning("{\"value\":\"x\"}")));

    var result = service.validate(new ConfigurationValidationRequest("ok", "=ref", "tenant", ""));

    assertThat(result.status()).isEqualTo(Status.SUCCESS);
  }

  @Test
  void failsInsteadOfGuessingAnEngineWhenSeveralAreConfigured() {
    var service =
        serviceWith(
            Map.of(
                "engine-a", feelReturning("{\"value\":\"x\"}"),
                "engine-b", feelReturning("{\"value\":\"x\"}")));

    var result = service.validate(new ConfigurationValidationRequest("ok", "=ref", "tenant", null));

    assertThat(result.status()).isEqualTo(Status.FAILURE);
    assertThat(result.code()).isEqualTo("RESOLUTION_ERROR");
  }

  @Test
  void failsForAnUnknownPhysicalTenantWithoutLeakingConfiguredEngines() {
    var service = serviceWith(Map.of("engine-a", feelReturning("{\"value\":\"x\"}")));

    var result =
        service.validate(new ConfigurationValidationRequest("ok", "=ref", "tenant", "engine-zzz"));

    assertThat(result.status()).isEqualTo(Status.FAILURE);
    assertThat(result.code()).isEqualTo("RESOLUTION_ERROR");
    assertThat(result.message()).doesNotContain("engine-a");
  }

  @Test
  void runsBeanValidationAndReturnsInvalidInputWithoutLeakingTheValue() {
    // token exceeds @Size(max = 3); the offending value must never be echoed back.
    var service = serviceWith("{\"token\":\"supersecretvalue\"}");

    var result =
        service.validate(
            new ConfigurationValidationRequest("constrained", "=ref", "tenant", "engine-a"));

    assertThat(result.status()).isEqualTo(Status.FAILURE);
    assertThat(result.code()).isEqualTo("INVALID_INPUT");
    assertThat(result.message()).doesNotContain("supersecretvalue");
  }
}
