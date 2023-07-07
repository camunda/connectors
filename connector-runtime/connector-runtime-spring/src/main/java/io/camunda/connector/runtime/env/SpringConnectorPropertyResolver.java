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
package io.camunda.connector.runtime.env;

import io.camunda.connector.impl.config.ConnectorPropertyResolver;
import org.springframework.core.env.Environment;

public class SpringConnectorPropertyResolver implements ConnectorPropertyResolver {

  private final Environment environment;

  public SpringConnectorPropertyResolver(Environment environment) {
    this.environment = environment;
  }

  @Override
  public boolean containsProperty(String key) {
    if (environment.containsProperty(key)) {
      return true;
    } else return environment.containsProperty(createSpringFormattedKey(key));
  }

  @Override
  public String getProperty(String key) {
    if (environment.containsProperty(key)) {
      return environment.getProperty(key);
    }
    // Check if maybe a ENV_VARIABLE_FORMAT was provided - lowercase it:
    String alternativeKey = createSpringFormattedKey(key);
    if (environment.containsProperty(alternativeKey)) {
      return environment.getProperty(alternativeKey);
    }
    return null;
  }

  private String createSpringFormattedKey(String key) {
    return key.toLowerCase().replaceAll("_", ".");
  }
}
