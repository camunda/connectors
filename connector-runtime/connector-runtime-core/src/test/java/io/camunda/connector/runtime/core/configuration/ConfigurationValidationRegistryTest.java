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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.connector.api.annotation.Configuration;
import io.camunda.connector.api.validation.ConfigurationValidationResult;
import io.camunda.connector.api.validation.ConfigurationValidator;
import io.camunda.connector.runtime.core.configuration.ConfigurationValidationRegistry.RegisteredValidator;
import java.util.List;
import org.junit.jupiter.api.Test;

class ConfigurationValidationRegistryTest {

  @Configuration(id = "cfg:1", name = "Config One")
  record ConfigOne(String value) {}

  @Configuration(id = "cfg:2", name = "Config Two")
  record ConfigTwo(String value) {}

  /** Configuration type not annotated with {@code @Configuration}. */
  record UnannotatedConfig(String value) {}

  static class ConfigOneValidator implements ConfigurationValidator<ConfigOne> {
    @Override
    public ConfigurationValidationResult validate(ConfigOne configuration) {
      return ConfigurationValidationResult.success();
    }
  }

  /** A second validator for the same configuration id — a conflict. */
  static class OtherConfigOneValidator implements ConfigurationValidator<ConfigOne> {
    @Override
    public ConfigurationValidationResult validate(ConfigOne configuration) {
      return ConfigurationValidationResult.success();
    }
  }

  static class ConfigTwoValidator implements ConfigurationValidator<ConfigTwo> {
    @Override
    public ConfigurationValidationResult validate(ConfigTwo configuration) {
      return ConfigurationValidationResult.success();
    }
  }

  static class UnannotatedConfigValidator implements ConfigurationValidator<UnannotatedConfig> {
    @Override
    public ConfigurationValidationResult validate(UnannotatedConfig configuration) {
      return ConfigurationValidationResult.success();
    }
  }

  @Test
  void registersValidatorUnderConfigurationId() {
    var registry = new ConfigurationValidationRegistry(List.of(new ConfigOneValidator()));

    assertThat(registry.findById("cfg:1"))
        .get()
        .extracting(RegisteredValidator::configurationClass)
        .isEqualTo(ConfigOne.class);
  }

  @Test
  void registersMultipleDistinctValidators() {
    var registry =
        new ConfigurationValidationRegistry(
            List.of(new ConfigOneValidator(), new ConfigTwoValidator()));

    assertThat(registry.findById("cfg:1")).isPresent();
    assertThat(registry.findById("cfg:2")).isPresent();
  }

  @Test
  void returnsEmptyForUnknownId() {
    var registry = new ConfigurationValidationRegistry(List.of(new ConfigOneValidator()));

    assertThat(registry.findById("does-not-exist")).isEmpty();
  }

  @Test
  void failsFastWhenTwoValidatorsClaimTheSameId() {
    assertThatThrownBy(
            () ->
                new ConfigurationValidationRegistry(
                    List.of(new ConfigOneValidator(), new OtherConfigOneValidator())))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Duplicate configuration validator for id 'cfg:1'");
  }

  @Test
  void failsFastWhenConfigurationTypeLacksAnnotation() {
    assertThatThrownBy(
            () -> new ConfigurationValidationRegistry(List.of(new UnannotatedConfigValidator())))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("not annotated with @Configuration");
  }
}
