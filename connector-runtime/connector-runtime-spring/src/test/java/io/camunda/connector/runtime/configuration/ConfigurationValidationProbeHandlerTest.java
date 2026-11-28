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
package io.camunda.connector.runtime.configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.client.api.response.ProbeOutcome;
import io.camunda.client.api.worker.probe.ActivatedProbe;
import io.camunda.client.api.worker.probe.ProbeAnswer;
import io.camunda.connector.api.annotation.Configuration;
import io.camunda.connector.api.secret.SecretContext;
import io.camunda.connector.api.secret.SecretProvider;
import io.camunda.connector.api.validation.ConfigurationValidationResult;
import io.camunda.connector.api.validation.ConfigurationValidationResult.Status;
import io.camunda.connector.api.validation.ConfigurationValidator;
import io.camunda.connector.runtime.core.FeelEvaluationResultMapper;
import io.camunda.connector.runtime.core.configuration.ConfigurationValidationRegistry;
import io.camunda.connector.runtime.core.configuration.ConfigurationValidationService;
import io.camunda.connector.runtime.core.secret.LegacySecretsDisabledProvider;
import io.camunda.connector.runtime.core.validation.ValidationUtil;
import jakarta.validation.constraints.NotBlank;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ConfigurationValidationProbeHandlerTest {

  @Configuration(id = "test:credential:1", name = "Test credential")
  record Credential(@NotBlank String user, String password) {}

  /** Answers from the password, so each test picks the validator's behavior through the input. */
  static class CredentialValidator implements ConfigurationValidator<Credential> {
    private Credential seen;

    @Override
    public ConfigurationValidationResult validate(Credential configuration) {
      seen = configuration;
      return switch (configuration.password()) {
        case "valid" -> ConfigurationValidationResult.success();
        case "wrong" ->
            ConfigurationValidationResult.failure(
                ConfigurationValidationResult.ErrorCode.UNAUTHORIZED, "The password is wrong.");
        case "unsupported" -> ConfigurationValidationResult.unsupported();
        default -> throw new IllegalStateException("boom " + configuration.password());
      };
    }
  }

  private final CredentialValidator validator = new CredentialValidator();
  private final List<String> requestedSecrets = new ArrayList<>();
  private final List<SecretContext> secretContexts = new ArrayList<>();
  private final SecretProvider secretProvider =
      new SecretProvider() {
        @Override
        public String getSecret(String name, SecretContext context) {
          requestedSecrets.add(name);
          secretContexts.add(context);
          return "LOCAL_PASSWORD".equals(name) ? "valid" : null;
        }
      };

  private ProbeAnswer answer(String input) {
    return answer(input, secretProvider);
  }

  private ProbeAnswer answer(String input, SecretProvider provider) {
    var registry = new ConfigurationValidationRegistry(List.of(validator));
    var service =
        new ConfigurationValidationService(
            registry,
            ValidationUtil.discoverDefaultValidationProviderImplementation(),
            FeelEvaluationResultMapper.create());
    var probe = mock(ActivatedProbe.class);
    when(probe.getInput()).thenReturn(input);
    when(probe.getTenantId()).thenReturn("tenant-a");
    when(probe.getPhysicalTenantId()).thenReturn("engine-a");
    return new ConfigurationValidationProbeHandler("test:credential:1", service, provider)
        .handle(probe);
  }

  private static ConfigurationValidationResponse data(ProbeAnswer answer) {
    assertThat(answer.getOutcome()).isEqualTo(ProbeOutcome.OK);
    assertThat(answer.getData()).isInstanceOf(ConfigurationValidationResponse.class);
    return (ConfigurationValidationResponse) answer.getData();
  }

  @Test
  void answersSuccessAsReturned() {
    var data = data(answer("{\"user\":\"u\",\"password\":\"valid\"}"));

    assertThat(data).isEqualTo(new ConfigurationValidationResponse(Status.SUCCESS, null, null));
  }

  @Test
  void answersFailureAsReturned() {
    var data = data(answer("{\"user\":\"u\",\"password\":\"wrong\"}"));

    assertThat(data)
        .isEqualTo(
            new ConfigurationValidationResponse(
                Status.FAILURE, "UNAUTHORIZED", "The password is wrong."));
  }

  @Test
  void answersUnsupportedAsReturned() {
    var data = data(answer("{\"user\":\"u\",\"password\":\"unsupported\"}"));

    assertThat(data).isEqualTo(new ConfigurationValidationResponse(Status.UNSUPPORTED, null, null));
  }

  @Test
  void answersInvalidInputWhenTheInputCannotBeBound() {
    var data = data(answer("{\"user\":{\"not\":\"a string\"},\"password\":\"valid\"}"));

    assertThat(data)
        .isEqualTo(
            new ConfigurationValidationResponse(
                Status.FAILURE, "INVALID_INPUT", "The resolved configuration is not valid."));
  }

  @Test
  void answersInvalidInputWhenTheInputIsNotValid() {
    var data = data(answer("{\"user\":\" \",\"password\":\"valid\"}"));

    assertThat(data)
        .isEqualTo(
            new ConfigurationValidationResponse(
                Status.FAILURE, "INVALID_INPUT", "The resolved configuration is not valid."));
  }

  @Test
  void answersErrorWithoutTheExceptionMessageWhenTheValidatorThrows() {
    var data = data(answer("{\"user\":\"u\",\"password\":\"injected-secret-value\"}"));

    assertThat(data)
        .isEqualTo(
            new ConfigurationValidationResponse(
                Status.FAILURE, "ERROR", "Validation could not be completed."));
  }

  @Test
  void replacesLegacySecretsFromTheRuntimesOwnProviders() {
    var data = data(answer("{\"user\":\"u\",\"password\":\"{{secrets.LOCAL_PASSWORD}}\"}"));

    assertThat(data.status()).isEqualTo(Status.SUCCESS);
    assertThat(validator.seen.password()).isEqualTo("valid");
    assertThat(requestedSecrets).containsExactly("LOCAL_PASSWORD");
    assertThat(secretContexts).containsExactly(new SecretContext("tenant-a", null, "engine-a"));
  }

  @Test
  void leavesInjectedValuesAlone() {
    var data = data(answer("{\"user\":\"u\",\"password\":\"valid\"}"));

    assertThat(data.status()).isEqualTo(Status.SUCCESS);
    assertThat(requestedSecrets).isEmpty();
  }

  @Test
  void answersResolutionErrorWhenALegacySecretIsMissing() {
    var data = data(answer("{\"user\":\"u\",\"password\":\"{{secrets.MISSING}}\"}"));

    assertThat(data)
        .isEqualTo(
            new ConfigurationValidationResponse(
                Status.FAILURE,
                "RESOLUTION_ERROR",
                "A secret of the configuration could not be resolved."));
    assertThat(validator.seen).isNull();
  }

  @Test
  void answersResolutionErrorWhenLegacySecretsAreSwitchedOff() {
    var data =
        data(
            answer(
                "{\"user\":\"u\",\"password\":\"{{secrets.LOCAL_PASSWORD}}\"}",
                new LegacySecretsDisabledProvider()));

    assertThat(data.code()).isEqualTo("RESOLUTION_ERROR");
    assertThat(validator.seen).isNull();
  }

  @Test
  void serializesTheResultWithoutAbsentFields() {
    var data = data(answer("{\"user\":\"u\",\"password\":\"valid\"}"));

    assertThat(FeelEvaluationResultMapper.create().convertValue(data, Map.class))
        .isEqualTo(Map.of("status", "SUCCESS"));
  }
}
