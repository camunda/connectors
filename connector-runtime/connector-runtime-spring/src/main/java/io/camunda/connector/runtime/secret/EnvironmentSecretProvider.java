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
                You are using connector environment secrets in unsafe mode. \
                All environment variables are accessible as connector secrets. \
                Please configure a meaningful secret prefix using \
                `camunda.connector.secretprovider.environment.prefix` \
                or `CAMUNDA_CONNECTOR_SECRETPROVIDER_ENVIRONMENT_PREFIX`.
                """);
    } else {
      LOG.debug(
          "Prefix '{}' has been configured, only environment variables with this prefix are available as connector secrets",
          prefix);
    }
  }

  @Override
  public String getSecret(String name, SecretContext context) {
    String secretName = composeSecretName(name, context);
    LOG.debug("Getting secret value for name '{}'", secretName);

    String secretValue = environment.getProperty(secretName);
    if (secretValue != null) {
      return secretValue;
    }

    // If prefix is configured and value was not found, check whether the unprefixed key exists.
    // If so, log a warning explaining that the secret was rejected because it is missing the
    // configured prefix.
    if (StringUtils.hasText(prefix)) {
      String unprefixedName = composeSecretNameWithPrefix(name, context, "");
      if (environment.containsProperty(unprefixedName)) {
        LOG.warn(
            "Rejected connector secret '{}': environment variable '{}' exists but does not match "
                + "the configured prefix '{}'. Rename it to '{}' to make it available as a "
                + "connector secret.",
            name,
            unprefixedName,
            prefix,
            secretName);
      }
    }

    return null;
  }

  /** Composes the full secret name using the configured prefix. */
  private String composeSecretName(String name, SecretContext context) {
    return composeSecretNameWithPrefix(name, context, prefix);
  }

  /** Composes the full secret name using the given prefix (may be empty for unprefixed lookup). */
  private String composeSecretNameWithPrefix(
      String name, SecretContext context, String effectivePrefix) {
    String resolvedPrefix = StringUtils.hasText(effectivePrefix) ? effectivePrefix : "";
    return tenantAware
        ? (processDefinitionAware
            ? composeSecretNameTenantAwareProcessDefinitionAware(name, context, resolvedPrefix)
            : composeSecretNameTenantAware(name, context, resolvedPrefix))
        : (processDefinitionAware
            ? composeSecretNameProcessDefinitionAware(name, context, resolvedPrefix)
            : composeSecretNameSimple(name, resolvedPrefix));
  }

  /**
   * Returns the secret name in format {@code ${prefix}${name}}.
   *
   * @param name the secret name to find the value for
   * @param resolvedPrefix the prefix to prepend (may be empty)
   * @return the final secret name
   */
  private String composeSecretNameSimple(String name, String resolvedPrefix) {
    return String.format("%s%s", resolvedPrefix, name);
  }

  /**
   * Returns the secret name in format {@code ${prefix}${tenantId}_${name}}.
   *
   * @param name the secret name to find the value for
   * @param context the context of where the secret is originated
   * @param resolvedPrefix the prefix to prepend (may be empty)
   * @return the final secret name
   */
  private String composeSecretNameTenantAware(
      String name, SecretContext context, String resolvedPrefix) {
    return String.format("%s%s_%s", resolvedPrefix, context.tenantId(), name);
  }

  /**
   * Returns the secret name in format {@code ${prefix}${processDefinitionId}_${name}}.
   *
   * @param name the secret name to find the value for
   * @param context the context of where the secret is originated
   * @param resolvedPrefix the prefix to prepend (may be empty)
   * @return the final secret name
   */
  private String composeSecretNameProcessDefinitionAware(
      String name, SecretContext context, String resolvedPrefix) {
    return String.format("%s%s_%s", resolvedPrefix, context.processDefinitionId(), name);
  }

  /**
   * Returns the secret name in format {@code ${prefix}${tenantId}_${processDefinitionId}_${name}}.
   *
   * @param name the secret name to find the value for
   * @param context the context of where the secret is originated
   * @param resolvedPrefix the prefix to prepend (may be empty)
   * @return the final secret name
   */
  private String composeSecretNameTenantAwareProcessDefinitionAware(
      String name, SecretContext context, String resolvedPrefix) {
    return String.format(
        "%s%s_%s_%s", resolvedPrefix, context.tenantId(), context.processDefinitionId(), name);
  }
}
