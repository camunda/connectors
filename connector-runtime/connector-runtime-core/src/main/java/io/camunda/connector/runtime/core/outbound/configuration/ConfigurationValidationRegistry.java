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

import io.camunda.connector.api.annotation.Configuration;
import io.camunda.connector.api.validation.ConfigurationValidator;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Builds and holds the {@code configurationId -> configuration class} map used to validate stored
 * configurations out-of-band.
 *
 * <p>The candidate classes are discovered elsewhere (a classpath scan for {@link Configuration})
 * and handed to this registry, which keeps those that also implement {@link ConfigurationValidator}
 * and registers each under the id from its {@link Configuration#id()}. Two <em>different</em>
 * classes claiming the same id is a conflict and fails fast at construction time.
 */
public class ConfigurationValidationRegistry {

  private static final Logger LOG = LoggerFactory.getLogger(ConfigurationValidationRegistry.class);

  private final Map<String, Class<? extends ConfigurationValidator>> classById = new HashMap<>();

  public ConfigurationValidationRegistry(Collection<Class<?>> configurationClasses) {
    for (Class<?> configurationClass : configurationClasses) {
      register(configurationClass);
    }
    LOG.info("Registered {} configuration validator(s): {}", classById.size(), classById.keySet());
  }

  private void register(Class<?> configurationClass) {
    Configuration configuration = configurationClass.getAnnotation(Configuration.class);
    if (configuration == null
        || !ConfigurationValidator.class.isAssignableFrom(configurationClass)) {
      // Not validatable: either not a configuration, or it declares no validation logic.
      return;
    }
    @SuppressWarnings("unchecked")
    Class<? extends ConfigurationValidator> validatorClass =
        (Class<? extends ConfigurationValidator>) configurationClass;

    Class<? extends ConfigurationValidator> existing = classById.get(configuration.id());
    if (existing != null && !existing.equals(validatorClass)) {
      throw new IllegalStateException(
          String.format(
              "Duplicate configuration validator for id '%s': %s and %s",
              configuration.id(), existing.getName(), validatorClass.getName()));
    }
    classById.put(configuration.id(), validatorClass);
  }

  /** Returns the configuration class registered for the given id, if any. */
  public Optional<Class<? extends ConfigurationValidator>> findById(String configurationId) {
    return Optional.ofNullable(classById.get(configurationId));
  }
}
