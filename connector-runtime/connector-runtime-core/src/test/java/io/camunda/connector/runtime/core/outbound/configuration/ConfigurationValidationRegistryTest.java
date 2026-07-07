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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.api.outbound.OutboundConnectorFunction;
import io.camunda.connector.api.validation.ConfigurationValidationResult;
import io.camunda.connector.api.validation.ConfigurationValidator;
import io.camunda.connector.generator.java.annotation.ConfigurationTemplate;
import io.camunda.connector.generator.java.annotation.ElementTemplate;
import io.camunda.connector.runtime.core.config.OutboundConnectorConfiguration;
import io.camunda.connector.runtime.core.outbound.OutboundConnectorFactory;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import org.junit.jupiter.api.Test;

class ConfigurationValidationRegistryTest {

  @ConfigurationTemplate(id = "cfg:1", name = "Config One")
  record ValidatableConfig(String value) implements ConfigurationValidator {
    @Override
    public ConfigurationValidationResult validate() {
      return ConfigurationValidationResult.success();
    }
  }

  /** Same id as {@link ValidatableConfig}, different class — a conflict. */
  @ConfigurationTemplate(id = "cfg:1", name = "Conflicting")
  record ConflictingConfig(String value) implements ConfigurationValidator {
    @Override
    public ConfigurationValidationResult validate() {
      return ConfigurationValidationResult.success();
    }
  }

  /** A configuration template that declares no validation logic. */
  @ConfigurationTemplate(id = "cfg:plain", name = "Plain")
  record PlainConfig(String value) {}

  @ElementTemplate(
      id = "conn:1",
      name = "Conn 1",
      configurationTemplates = {ValidatableConfig.class})
  static class ValidatableConnector implements OutboundConnectorFunction {
    @Override
    public Object execute(OutboundConnectorContext context) {
      return null;
    }
  }

  @ElementTemplate(
      id = "conn:2",
      name = "Conn 2",
      configurationTemplates = {ValidatableConfig.class})
  static class OtherConnectorSharingConfig implements OutboundConnectorFunction {
    @Override
    public Object execute(OutboundConnectorContext context) {
      return null;
    }
  }

  @ElementTemplate(
      id = "conn:c",
      name = "Conn C",
      configurationTemplates = {ConflictingConfig.class})
  static class ConflictingConnector implements OutboundConnectorFunction {
    @Override
    public Object execute(OutboundConnectorContext context) {
      return null;
    }
  }

  @ElementTemplate(
      id = "conn:p",
      name = "Conn P",
      configurationTemplates = {PlainConfig.class})
  static class PlainConfigConnector implements OutboundConnectorFunction {
    @Override
    public Object execute(OutboundConnectorContext context) {
      return null;
    }
  }

  static class NoTemplateConnector implements OutboundConnectorFunction {
    @Override
    public Object execute(OutboundConnectorContext context) {
      return null;
    }
  }

  private OutboundConnectorFactory factoryFor(OutboundConnectorFunction... connectors) {
    return new OutboundConnectorFactory() {
      @Override
      public Collection<OutboundConnectorConfiguration> getActiveConfigurations() {
        int[] idx = {0};
        return Arrays.stream(connectors)
            .map(
                c ->
                    new OutboundConnectorConfiguration(
                        c.getClass().getSimpleName(), new String[0], "type:" + (idx[0]++), () -> c))
            .toList();
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
        return connectors[Integer.parseInt(type.substring("type:".length()))];
      }
    };
  }

  @Test
  void registersValidatableConfiguration() {
    var registry = new ConfigurationValidationRegistry(factoryFor(new ValidatableConnector()));

    assertThat(registry.findById("cfg:1")).get().isEqualTo(ValidatableConfig.class);
  }

  @Test
  void ignoresConfigurationWithoutValidator() {
    var registry = new ConfigurationValidationRegistry(factoryFor(new PlainConfigConnector()));

    assertThat(registry.findById("cfg:plain")).isEmpty();
  }

  @Test
  void returnsEmptyForUnknownId() {
    var registry = new ConfigurationValidationRegistry(factoryFor(new ValidatableConnector()));

    assertThat(registry.findById("does-not-exist")).isEmpty();
  }

  @Test
  void sameConfigurationClassAcrossConnectorsIsRegisteredOnce() {
    var registry =
        new ConfigurationValidationRegistry(
            factoryFor(new ValidatableConnector(), new OtherConnectorSharingConfig()));

    assertThat(registry.findById("cfg:1")).get().isEqualTo(ValidatableConfig.class);
  }

  @Test
  void failsFastWhenTwoClassesClaimTheSameId() {
    assertThatThrownBy(
            () ->
                new ConfigurationValidationRegistry(
                    factoryFor(new ValidatableConnector(), new ConflictingConnector())))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Duplicate configuration validator for id 'cfg:1'");
  }

  @Test
  void skipsConnectorsWithoutElementTemplate() {
    var registry = new ConfigurationValidationRegistry(factoryFor(new NoTemplateConnector()));

    assertThat(registry.findById("cfg:1")).isEmpty();
  }
}
