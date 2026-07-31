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
import java.util.ArrayList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.util.StringUtils;

public class EnvironmentSecretProvider implements SecretProvider {
  private static final Logger LOG = LoggerFactory.getLogger(EnvironmentSecretProvider.class);

  /**
   * Segment used when the physical tenant is unknown, i.e. against a cluster that predates
   * multi-engine support. Matches the {@code "default"} convention already used for the physical
   * tenant elsewhere in the runtime, so a single-cluster deployment that enables {@code
   * physicalTenantAware} resolves a stable, predictable name instead of silently falling back to an
   * unscoped one.
   */
  private static final String DEFAULT_PHYSICAL_TENANT_ID = "default";

  private final Environment environment;
  private final String prefix;
  private final boolean physicalTenantAware;
  private final boolean tenantAware;
  private final boolean processDefinitionAware;

  public EnvironmentSecretProvider(
      Environment environment,
      String prefix,
      boolean physicalTenantAware,
      boolean tenantAware,
      boolean processDefinitionAware) {
    this.environment = environment;
    this.prefix = prefix;
    this.physicalTenantAware = physicalTenantAware;
    this.tenantAware = tenantAware;
    this.processDefinitionAware = processDefinitionAware;
  }

  /**
   * Retains the pre-multi-engine constructor signature, leaving the physical tenant out of the
   * composed secret name.
   */
  public EnvironmentSecretProvider(
      Environment environment, String prefix, boolean tenantAware, boolean processDefinitionAware) {
    this(environment, prefix, false, tenantAware, processDefinitionAware);
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

  /**
   * Composes the full secret name as {@code ${prefix}${scope segments...}_${name}}, where the scope
   * segments are those enabled by configuration, in the order physical tenant, tenant, process
   * definition. With no scope enabled this is just {@code ${prefix}${name}}.
   *
   * <p>{@code context} is only dereferenced for the scopes that are actually enabled, so an
   * unscoped provider still resolves with a {@code null} context.
   *
   * @param effectivePrefix the prefix to prepend (may be empty for unprefixed lookup)
   */
  private String composeSecretNameWithPrefix(
      String name, SecretContext context, String effectivePrefix) {
    String resolvedPrefix = StringUtils.hasText(effectivePrefix) ? effectivePrefix : "";
    var segments = new ArrayList<String>();
    if (physicalTenantAware) {
      segments.add(resolvePhysicalTenantId(context));
    }
    if (tenantAware) {
      segments.add(context.tenantId());
    }
    if (processDefinitionAware) {
      segments.add(context.processDefinitionId());
    }
    segments.add(name);
    return resolvedPrefix + String.join("_", segments);
  }

  /**
   * Returns the context's physical tenant, or {@link #DEFAULT_PHYSICAL_TENANT_ID} when it is unset
   * — resolving to an unscoped name instead would let a misconfigured multi-engine deployment
   * silently share one secret across engines.
   */
  private String resolvePhysicalTenantId(SecretContext context) {
    var physicalTenantId = context.physicalTenantId();
    return physicalTenantId != null ? physicalTenantId : DEFAULT_PHYSICAL_TENANT_ID;
  }
}
