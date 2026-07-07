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

import io.camunda.connector.api.outbound.OutboundConnectorFunction;
import io.camunda.connector.api.validation.ConfigurationValidator;
import io.camunda.connector.generator.java.annotation.ConfigurationTemplate;
import io.camunda.connector.generator.java.annotation.ElementTemplate;
import io.camunda.connector.runtime.core.config.OutboundConnectorConfiguration;
import io.camunda.connector.runtime.core.outbound.OutboundConnectorFactory;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Builds and holds the {@code configurationId -> configuration class} map used to validate stored
 * configurations out-of-band.
 *
 * <p>Discovery is connector-rooted: for each discovered outbound connector, its declared {@link
 * ElementTemplate#configurationTemplates()} are inspected, and every class that both carries a
 * {@link ConfigurationTemplate} (which supplies the id) and implements {@link
 * ConfigurationValidator} (which supplies the validation logic) is registered under its id. Because
 * the same configuration class is typically shared by several connectors, it is registered once;
 * two <em>different</em> classes claiming the same id is a conflict and fails fast at construction
 * time.
 */
public class ConfigurationValidationRegistry {

  private static final Logger LOG = LoggerFactory.getLogger(ConfigurationValidationRegistry.class);

  private final Map<String, Class<? extends ConfigurationValidator>> classById = new HashMap<>();

  public ConfigurationValidationRegistry(OutboundConnectorFactory connectorFactory) {
    for (OutboundConnectorConfiguration configuration :
        connectorFactory.getActiveConfigurations()) {
      final OutboundConnectorFunction instance;
      try {
        instance = connectorFactory.getInstance(configuration.type());
      } catch (Exception e) {
        LOG.warn(
            "Could not instantiate connector '{}' while scanning for configuration validators; "
                + "skipping",
            configuration.type(),
            e);
        continue;
      }
      // Provider-based connectors are wrapped (e.g. OutboundConnectorOperationFunction); the
      // wrapper
      // carries no @ElementTemplate, so their configuration templates are not discovered here.
      ElementTemplate elementTemplate = instance.getClass().getAnnotation(ElementTemplate.class);
      if (elementTemplate == null) {
        continue;
      }
      for (Class<?> configurationClass : elementTemplate.configurationTemplates()) {
        register(configurationClass);
      }
    }
    LOG.info("Registered {} configuration validator(s): {}", classById.size(), classById.keySet());
  }

  private void register(Class<?> configurationClass) {
    ConfigurationTemplate template = configurationClass.getAnnotation(ConfigurationTemplate.class);
    if (template == null || !ConfigurationValidator.class.isAssignableFrom(configurationClass)) {
      // Not validatable: either not a configuration template, or it declares no validation logic.
      return;
    }
    @SuppressWarnings("unchecked")
    Class<? extends ConfigurationValidator> validatorClass =
        (Class<? extends ConfigurationValidator>) configurationClass;

    Class<? extends ConfigurationValidator> existing = classById.get(template.id());
    if (existing != null && !existing.equals(validatorClass)) {
      throw new IllegalStateException(
          String.format(
              "Duplicate configuration validator for id '%s': %s and %s",
              template.id(), existing.getName(), validatorClass.getName()));
    }
    classById.put(template.id(), validatorClass);
  }

  /** Returns the configuration class registered for the given id, if any. */
  public Optional<Class<? extends ConfigurationValidator>> findById(String configurationId) {
    return Optional.ofNullable(classById.get(configurationId));
  }
}
