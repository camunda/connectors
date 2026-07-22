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
package io.camunda.connector.runtime.core.outbound.configuration;

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
import java.util.List;
import org.junit.jupiter.api.Test;

class ConfigurationValidationServiceTest {

  @Configuration(id = "ok", name = "Ok")
  record OkConfig(String value) {}

  @Configuration(id = "throws", name = "Throws")
  record ThrowingConfig(String value) {}

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

  private ConfigurationValidationService serviceWith(String resolvedJson) {
    var registry =
        new ConfigurationValidationRegistry(List.of(new OkValidator(), new ThrowingValidator()));
    SecretProvider noSecrets =
        new SecretProvider() {
          @Override
          public String getSecret(String name, SecretContext context) {
            return null;
          }
        };
    return new ConfigurationValidationService(
        registry, feelReturning(resolvedJson), noSecrets, objectMapper);
  }

  @Test
  void returnsSuccessWhenValidatorPasses() {
    var service = serviceWith("{\"value\":\"x\"}");

    var result = service.validate(new ConfigurationValidationRequest("ok", "=ref", "tenant"));

    assertThat(result.status()).isEqualTo(Status.SUCCESS);
  }

  @Test
  void mapsThrownConnectorExceptionToFailureWithErrorCode() {
    var service = serviceWith("{\"value\":\"x\"}");

    var result = service.validate(new ConfigurationValidationRequest("throws", "=ref", "tenant"));

    assertThat(result.status()).isEqualTo(Status.FAILURE);
    assertThat(result.code()).isEqualTo("UNAUTHORIZED");
    assertThat(result.message()).isEqualTo("invalid key");
  }

  @Test
  void returnsUnsupportedForUnknownConfigurationId() {
    var service = serviceWith("{\"value\":\"x\"}");

    var result = service.validate(new ConfigurationValidationRequest("unknown", "=ref", "tenant"));

    assertThat(result.status()).isEqualTo(Status.UNSUPPORTED);
  }

  @Test
  void returnsFailureWhenResolutionFails() {
    var service = serviceWith("not-json");

    var result = service.validate(new ConfigurationValidationRequest("ok", "=ref", "tenant"));

    assertThat(result.status()).isEqualTo(Status.FAILURE);
    assertThat(result.code()).isEqualTo("RESOLUTION_ERROR");
  }
}
