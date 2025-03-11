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
package io.camunda.connector.runtime.secret;

import io.camunda.connector.api.secret.SecretProvider;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.AbstractEnvironment;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.Environment;
import org.springframework.util.StringUtils;

public class EnvironmentSecretProvider implements SecretProvider {
  private static final Logger LOG = LoggerFactory.getLogger(EnvironmentSecretProvider.class);
  private final Environment environment;
  private final String prefix;

  public EnvironmentSecretProvider(Environment environment, String prefix) {
    if (!StringUtils.hasText(prefix)) {
      LOG.warn(
          "No prefix has been configured, only environment variables prefixed by `SECRET_` are available as connector secrets");
      this.prefix = "SECRET_";
    } else {
      LOG.debug(
          "Prefix '{}' has been configured, only environment variables with this prefix are available as connector secrets",
          prefix);
      this.prefix = prefix;
    }
    this.environment = environment;
  }

  @Override
  public String getSecret(String name) {
    String prefixedName = !StringUtils.hasText(prefix) ? name : prefix + name;
    return environment.getProperty(prefixedName);
  }

  @Override
  public List<String> getSecretValues() {
    if (environment instanceof AbstractEnvironment abstractEnvironment) {
      return abstractEnvironment.getPropertySources().stream()
          .filter(propertySource -> propertySource instanceof EnumerablePropertySource<?>)
          .map(propertySource -> (EnumerablePropertySource<?>) propertySource)
          .flatMap(
              enumerablePropertySource ->
                  Arrays.stream(enumerablePropertySource.getPropertyNames())
                      .filter(name -> name.startsWith(prefix))
                      .map(enumerablePropertySource::getProperty))
          .filter(Objects::nonNull)
          .map(Object::toString)
          .toList();
    }
    return List.of();
  }
}
