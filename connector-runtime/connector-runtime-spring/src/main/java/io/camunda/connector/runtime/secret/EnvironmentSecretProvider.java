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

import io.camunda.connector.api.secret.SecretContext;
import io.camunda.connector.api.secret.SecretProvider;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.util.StringUtils;

public class EnvironmentSecretProvider implements SecretProvider {
  private static final Logger LOG = LoggerFactory.getLogger(EnvironmentSecretProvider.class);
  private final Environment environment;
  private final String prefix;
  private final boolean tenantAware;

  public EnvironmentSecretProvider(Environment environment, String prefix, boolean tenantAware) {
    this.environment = environment;
    this.prefix = prefix;
    this.tenantAware = tenantAware;
  }

  @PostConstruct
  public void init() {
    if (!StringUtils.hasText(prefix)) {
      LOG.info(
          "No prefix has been configured, all environment variables are available as connector secrets");
    } else {
      LOG.debug(
          "Prefix '{}' has been configured, only environment variables with this prefix are available as connector secrets",
          prefix);
    }
  }

  @Override
  public String getSecret(String name, SecretContext context) {
    String prefixedName =
        tenantAware ? composeSecretNameTenantAware(name, context) : composeSecretName(name);
    return environment.getProperty(prefixedName);
  }

  /**
   * returns the secret name in format ${prefix}${name}
   *
   * @param name the secrets' name to find the value for
   * @return the final secret name
   */
  private String composeSecretName(String name) {
    return String.format("%s%s", StringUtils.hasText(prefix) ? prefix : "", name);
  }

  /**
   * returns the secret name in format ${prefix}${tenantId}${name}
   *
   * @param name the secrets' name to find the value for
   * @param context the context of where the secret is originated
   * @return the final secret name
   */
  private String composeSecretNameTenantAware(String name, SecretContext context) {
    return String.format(
        "%s%s_%s", StringUtils.hasText(prefix) ? prefix + "_" : "", context.tenantId(), name);
  }
}
