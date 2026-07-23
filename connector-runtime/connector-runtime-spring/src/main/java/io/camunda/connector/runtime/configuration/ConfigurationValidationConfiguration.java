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

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.api.validation.ConfigurationValidator;
import io.camunda.connector.api.validation.ValidationProvider;
import io.camunda.connector.feel.FeelExpressionEvaluator;
import io.camunda.connector.runtime.annotation.OutboundConnectorObjectMapper;
import io.camunda.connector.runtime.core.configuration.ConfigurationValidationRegistry;
import io.camunda.connector.runtime.core.configuration.ConfigurationValidationService;
import io.camunda.connector.runtime.core.secret.SecretProviderAggregator;
import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * Wires configuration (credential) validation. This is <b>direction-agnostic</b>: the same
 * configuration types are consumed by both inbound and outbound connectors, so it is imported by
 * the neutral top-level runtime auto-configuration rather than the outbound-specific one — an
 * inbound-only runtime exposes it too.
 */
@Configuration
@Import(ConfigurationValidationRestController.class)
public class ConfigurationValidationConfiguration {

  @Bean
  public ConfigurationValidationRegistry configurationValidationRegistry() {
    return new ConfigurationValidationRegistry(discoverConfigurationValidators());
  }

  @Bean
  public ConfigurationValidationService configurationValidationService(
      ConfigurationValidationRegistry configurationValidationRegistry,
      FeelExpressionEvaluator feelExpressionEvaluator,
      SecretProviderAggregator secretProviderAggregator,
      ValidationProvider validationProvider,
      @OutboundConnectorObjectMapper ObjectMapper objectMapper) {
    return new ConfigurationValidationService(
        configurationValidationRegistry,
        feelExpressionEvaluator,
        secretProviderAggregator,
        validationProvider,
        objectMapper);
  }

  /**
   * Discovers {@code ConfigurationValidator} implementations via the SPI {@link ServiceLoader},
   * mirroring how connectors themselves are discovered ({@code SPIConnectorDiscovery}). This is
   * package-independent: a third-party connector's validator (e.g. under {@code com.acme}) is found
   * as long as it is declared in {@code META-INF/services}, whereas a fixed base-package scan would
   * silently miss it and always answer {@code UNSUPPORTED}.
   */
  @SuppressWarnings("rawtypes")
  private static List<ConfigurationValidator<?>> discoverConfigurationValidators() {
    List<ConfigurationValidator<?>> validators = new ArrayList<>();
    for (ConfigurationValidator validator : ServiceLoader.load(ConfigurationValidator.class)) {
      validators.add(validator);
    }
    return validators;
  }
}
