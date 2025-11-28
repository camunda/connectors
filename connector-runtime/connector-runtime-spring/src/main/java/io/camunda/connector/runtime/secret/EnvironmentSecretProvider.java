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
  private final boolean processDefinitionAware;

  public EnvironmentSecretProvider(
      Environment environment, String prefix, boolean tenantAware, boolean processDefinitionAware) {
    this.environment = environment;
    this.prefix = prefix;
    this.tenantAware = tenantAware;
    this.processDefinitionAware = processDefinitionAware;
  }

  @PostConstruct
  public void init() {
    if (!StringUtils.hasText(prefix)) {
      LOG.warn(
          """
                Connector secret environment variable prefix is not configured.
                Currently, all environment variables will be exposed as connector secrets.
                This is unsafe and will not be supported anymore in future releases.

                Please configure a valid prefix using
                `camunda.connectors.secretprovider.environment.prefix`
                or
                `CAMUNDA_CONNECTORS_SECRETPROVIDER_ENVIRONMENT_PREFIX`.

                If `camunda.connector.secretprovider.environment.enabled` is set to true,
                a prefix must be configured to avoid breaking changes in the future.
                """);
    } else {
      LOG.debug(
          "Prefix '{}' has been configured, only environment variables with this prefix are available as connector secrets",
          prefix);
    }
  }

  @Override
  public String getSecret(String name, SecretContext context) {
    if (!StringUtils.hasText(prefix)) {
      LOG.warn(
          "Accessing connector environment secrets without a configured prefix. This behavior is deprecated and will not be supported in a future release. "
              + "Please set `camunda.connector.secretprovider.environment.prefix`. or `CAMUNDA_CONNECTOR_SECRETPROVIDER_ENVIRONMENT_PREFIX`.");
    }
    String secretName =
        tenantAware
            ? (processDefinitionAware
                ? composeSecretNameTenantAwareProcessDefinitionAware(name, context)
                : composeSecretNameTenantAware(name, context))
            : (processDefinitionAware
                ? composeSecretNameProcessDefinitionAware(name, context)
                : composeSecretName(name));
    LOG.debug("Getting secret value for name '{}'", secretName);
    return environment.getProperty(secretName);
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
   * returns the secret name in format ${prefix}${tenantId}_${name}
   *
   * @param name the secrets' name to find the value for
   * @param context the context of where the secret is originated
   * @return the final secret name
   */
  private String composeSecretNameTenantAware(String name, SecretContext context) {
    return String.format(
        "%s%s_%s", StringUtils.hasText(prefix) ? prefix : "", context.tenantId(), name);
  }

  /**
   * returns the secret name in format ${prefix}${processDefinitionId}_${name}
   *
   * @param name the secrets' name to find the value for
   * @return the final secret name
   */
  private String composeSecretNameProcessDefinitionAware(String name, SecretContext context) {
    return String.format(
        "%s%s_%s", StringUtils.hasText(prefix) ? prefix : "", context.processDefinitionId(), name);
  }

  /**
   * returns the secret name in format ${prefix}${tenantId}_${processDefinitionId}_${name}
   *
   * @param name the secrets' name to find the value for
   * @param context the context of where the secret is originated
   * @return the final secret name
   */
  private String composeSecretNameTenantAwareProcessDefinitionAware(
      String name, SecretContext context) {
    return String.format(
        "%s%s_%s_%s",
        StringUtils.hasText(prefix) ? prefix : "",
        context.tenantId(),
        context.processDefinitionId(),
        name);
  }
}
