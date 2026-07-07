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
import io.camunda.connector.api.error.ConnectorException;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.api.outbound.OutboundConnectorFunction;
import io.camunda.connector.api.secret.SecretContext;
import io.camunda.connector.api.secret.SecretProvider;
import io.camunda.connector.api.validation.ConfigurationValidationResult;
import io.camunda.connector.api.validation.ConfigurationValidationResult.Status;
import io.camunda.connector.api.validation.ConfigurationValidator;
import io.camunda.connector.feel.FeelExpressionEvaluator;
import io.camunda.connector.generator.java.annotation.ConfigurationTemplate;
import io.camunda.connector.generator.java.annotation.ElementTemplate;
import io.camunda.connector.runtime.core.config.OutboundConnectorConfiguration;
import io.camunda.connector.runtime.core.outbound.OutboundConnectorFactory;
import java.util.Collection;
import java.util.List;
import org.junit.jupiter.api.Test;

class ConfigurationValidationServiceTest {

  @ConfigurationTemplate(id = "ok", name = "Ok")
  record OkConfig(String value) implements ConfigurationValidator {
    @Override
    public ConfigurationValidationResult validate() {
      return ConfigurationValidationResult.success();
    }
  }

  @ConfigurationTemplate(id = "throws", name = "Throws")
  record ThrowingConfig(String value) implements ConfigurationValidator {
    @Override
    public ConfigurationValidationResult validate() {
      throw new ConnectorException("UNAUTHORIZED", "invalid key");
    }
  }

  @ElementTemplate(
      id = "conn",
      name = "Conn",
      configurationTemplates = {OkConfig.class, ThrowingConfig.class})
  static class TestConnector implements OutboundConnectorFunction {
    @Override
    public Object execute(OutboundConnectorContext context) {
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

  private ConfigurationValidationService serviceWith(String resolvedJson) {
    var connector = new TestConnector();
    OutboundConnectorFactory factory =
        new OutboundConnectorFactory() {
          @Override
          public Collection<OutboundConnectorConfiguration> getActiveConfigurations() {
            return List.of(
                new OutboundConnectorConfiguration(
                    "Test", new String[0], "test:1", () -> connector));
          }

          @Override
          public Collection<
                  io.camunda.connector.runtime.core.common.AbstractConnectorFactory
                          .ConnectorRuntimeConfiguration<
                      OutboundConnectorConfiguration>>
              getRuntimeConfigurations() {
            return List.of();
          }

          @Override
          public OutboundConnectorFunction getInstance(String type) {
            return connector;
          }
        };
    var registry = new ConfigurationValidationRegistry(factory);
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
