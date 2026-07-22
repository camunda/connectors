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

import io.camunda.connector.api.annotation.Configuration;
import io.camunda.connector.api.validation.ConfigurationValidationResult;
import io.camunda.connector.api.validation.ConfigurationValidator;
import java.util.List;
import org.junit.jupiter.api.Test;

class ConfigurationValidationRegistryTest {

  @Configuration(id = "cfg:1", name = "Config One")
  record ValidatableConfig(String value) implements ConfigurationValidator {
    @Override
    public ConfigurationValidationResult validate() {
      return ConfigurationValidationResult.success();
    }
  }

  /** Same id as {@link ValidatableConfig}, different class — a conflict. */
  @Configuration(id = "cfg:1", name = "Conflicting")
  record ConflictingConfig(String value) implements ConfigurationValidator {
    @Override
    public ConfigurationValidationResult validate() {
      return ConfigurationValidationResult.success();
    }
  }

  /** A configuration that declares no validation logic. */
  @Configuration(id = "cfg:plain", name = "Plain")
  record PlainConfig(String value) {}

  /** Validatable but not a configuration (no {@code @Configuration}). */
  record UnannotatedConfig(String value) implements ConfigurationValidator {
    @Override
    public ConfigurationValidationResult validate() {
      return ConfigurationValidationResult.success();
    }
  }

  @Test
  void registersValidatableConfiguration() {
    var registry = new ConfigurationValidationRegistry(List.of(ValidatableConfig.class));

    assertThat(registry.findById("cfg:1")).get().isEqualTo(ValidatableConfig.class);
  }

  @Test
  void ignoresConfigurationWithoutValidator() {
    var registry = new ConfigurationValidationRegistry(List.of(PlainConfig.class));

    assertThat(registry.findById("cfg:plain")).isEmpty();
  }

  @Test
  void ignoresValidatorWithoutConfigurationAnnotation() {
    var registry = new ConfigurationValidationRegistry(List.of(UnannotatedConfig.class));

    assertThat(registry.findById("cfg:1")).isEmpty();
  }

  @Test
  void returnsEmptyForUnknownId() {
    var registry = new ConfigurationValidationRegistry(List.of(ValidatableConfig.class));

    assertThat(registry.findById("does-not-exist")).isEmpty();
  }

  @Test
  void sameConfigurationClassListedTwiceIsRegisteredOnce() {
    var registry =
        new ConfigurationValidationRegistry(
            List.of(ValidatableConfig.class, ValidatableConfig.class));

    assertThat(registry.findById("cfg:1")).get().isEqualTo(ValidatableConfig.class);
  }

  @Test
  void failsFastWhenTwoClassesClaimTheSameId() {
    assertThatThrownBy(
            () ->
                new ConfigurationValidationRegistry(
                    List.of(ValidatableConfig.class, ConflictingConfig.class)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Duplicate configuration validator for id 'cfg:1'");
  }
}
