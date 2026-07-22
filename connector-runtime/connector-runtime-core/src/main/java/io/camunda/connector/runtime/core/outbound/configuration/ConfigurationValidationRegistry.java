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
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Builds and holds the {@code configurationId -> validator} map used to validate stored
 * configurations out-of-band.
 *
 * <p>Validators are discovered elsewhere (a classpath scan for {@link ConfigurationValidator}
 * implementations) and handed to this registry. For each validator, the configuration type {@code
 * T} is resolved from its {@code ConfigurationValidator<T>} interface, and the id is read from that
 * type's {@link Configuration#id()}. Two validators claiming the same id is a conflict and fails
 * fast at construction time.
 */
public class ConfigurationValidationRegistry {

  private static final Logger LOG = LoggerFactory.getLogger(ConfigurationValidationRegistry.class);

  /** A validator together with the configuration type its argument must be deserialized into. */
  public record RegisteredValidator(
      Class<?> configurationClass, ConfigurationValidator<?> validator) {}

  private final Map<String, RegisteredValidator> byId = new HashMap<>();

  public ConfigurationValidationRegistry(Collection<ConfigurationValidator<?>> validators) {
    for (ConfigurationValidator<?> validator : validators) {
      register(validator);
    }
    LOG.info("Registered {} configuration validator(s): {}", byId.size(), byId.keySet());
  }

  private void register(ConfigurationValidator<?> validator) {
    Class<?> configurationClass = resolveConfigurationType(validator.getClass());
    if (configurationClass == null) {
      throw new IllegalStateException(
          String.format(
              "Cannot determine the configuration type of validator %s; it must directly implement"
                  + " ConfigurationValidator<T>",
              validator.getClass().getName()));
    }
    Configuration configuration = configurationClass.getAnnotation(Configuration.class);
    if (configuration == null) {
      throw new IllegalStateException(
          String.format(
              "Configuration type %s validated by %s is not annotated with @Configuration",
              configurationClass.getName(), validator.getClass().getName()));
    }

    RegisteredValidator existing = byId.get(configuration.id());
    if (existing != null && !existing.validator().getClass().equals(validator.getClass())) {
      throw new IllegalStateException(
          String.format(
              "Duplicate configuration validator for id '%s': %s and %s",
              configuration.id(),
              existing.validator().getClass().getName(),
              validator.getClass().getName()));
    }
    byId.put(configuration.id(), new RegisteredValidator(configurationClass, validator));
  }

  /**
   * Resolves {@code T} from a {@code ConfigurationValidator<T>} implemented directly by the class.
   */
  private static Class<?> resolveConfigurationType(Class<?> validatorClass) {
    for (Type genericInterface : validatorClass.getGenericInterfaces()) {
      if (genericInterface instanceof ParameterizedType parameterized
          && parameterized.getRawType() == ConfigurationValidator.class
          && parameterized.getActualTypeArguments()[0] instanceof Class<?> configurationClass) {
        return configurationClass;
      }
    }
    return null;
  }

  /** Returns the validator registered for the given configuration id, if any. */
  public Optional<RegisteredValidator> findById(String configurationId) {
    return Optional.ofNullable(byId.get(configurationId));
  }
}
