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
package io.camunda.connector.runtime.core.secret;

import io.camunda.connector.api.secret.SecretContext;
import io.camunda.connector.api.secret.SecretProvider;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Resolves a legacy secret name from the orchestration cluster's secret stores, by looking it up as
 * {@code camunda.secrets.<name>}.
 *
 * <p>Used only under {@link LegacySecretMode#FALLBACK}, and only after the configured providers
 * have come up empty. It gives a deployment an incremental migration path: move the values into the
 * central store, drop the local provider, and diagrams still written in the legacy syntax keep
 * resolving.
 *
 * <p>A configuration lives on one orchestration cluster, so the lookup is made against the engine
 * the secret is being resolved for.
 */
public class CentralStoreSecretProvider implements SecretProvider {

  private static final Logger LOG = LoggerFactory.getLogger(CentralStoreSecretProvider.class);

  private final Map<String, SecretReferenceResolver> resolversByPhysicalTenantId;

  public CentralStoreSecretProvider(
      Map<String, SecretReferenceResolver> resolversByPhysicalTenantId) {
    this.resolversByPhysicalTenantId = Map.copyOf(resolversByPhysicalTenantId);
  }

  @Override
  public String getSecret(String name, SecretContext context) {
    if (!SecretReferenceUtil.isResolvableName(name)) {
      // Failing here says what is wrong. Returning nothing would report the name as merely
      // unavailable, which is what an ordinary missing secret looks like.
      throw new SecretLookupRefusedException(
          "Secret '"
              + name
              + "' cannot be read from the cluster's secret stores: a secret reference may only"
              + " contain letters, digits, underscores, and hyphens. Rename the secret, or keep it"
              + " in a locally configured secret provider.");
    }
    SecretReferenceResolver resolver = resolverFor(context);
    if (resolver == null) {
      return null;
    }
    String reference = SecretReferenceUtil.reference(name);
    // Strict: a legacy name this returns nothing for is turned into a permanent failure by the
    // caller, so a cluster that could not be reached must not look like a name the stores do not
    // hold. Only an answer about the reference itself decides that it is missing.
    return resolver.resolveOrFail(List.of(reference)).get(reference);
  }

  private SecretReferenceResolver resolverFor(SecretContext context) {
    String physicalTenantId = context == null ? null : context.physicalTenantId();
    if (physicalTenantId != null) {
      SecretReferenceResolver resolver = resolversByPhysicalTenantId.get(physicalTenantId);
      if (resolver == null) {
        LOG.warn(
            "No engine is configured for physical tenant '{}', so no secret can be read from its"
                + " secret stores",
            physicalTenantId);
      }
      return resolver;
    }
    if (resolversByPhysicalTenantId.size() == 1) {
      return resolversByPhysicalTenantId.values().iterator().next();
    }
    LOG.warn(
        "Cannot tell which engine to read a secret from: {} are configured and the lookup carries"
            + " no physical tenant",
        resolversByPhysicalTenantId.size());
    return null;
  }
}
