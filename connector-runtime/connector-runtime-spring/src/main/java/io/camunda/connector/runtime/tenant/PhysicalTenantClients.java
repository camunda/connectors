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
package io.camunda.connector.runtime.tenant;

import io.camunda.client.CamundaClient;
import io.camunda.client.spring.bean.CamundaClientRegistry;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;

/**
 * Physical-tenant resolution helpers shared by the direction-agnostic {@code @Configuration}
 * classes that build a per-physical-tenant map from a {@link CamundaClientRegistry} ({@code
 * OutboundConnectorRuntimeConfiguration}, {@code ConfigurationValidationConfiguration}).
 *
 * <p>Unlike the inbound-side {@code PhysicalTenantIds}, these tolerate a completely absent {@link
 * CamundaClientRegistry}: both consumers are reachable from minimal/test contexts that wire a raw
 * {@code CamundaClient} bean without the full {@code camunda-spring-boot-starter} auto-config chain
 * that would normally register a registry.
 *
 * <p>Plain static methods, deliberately not {@code @Bean}-produced: no consumer may declare a
 * {@code Map<String, X>}-typed {@code @Bean} parameter, since Spring's dependency resolution
 * special-cases any such parameter by collecting <em>all</em> beans of type {@code X} by name —
 * including scalar legacy/override beans — instead of using the bean whose own declared type is the
 * map. Keeping these as statics on a separate class also keeps them out of reach of
 * {@code @Configuration} CGLIB proxying, which re-resolves a {@code @Bean} method's parameters from
 * the container even when the method is called directly in code.
 */
public final class PhysicalTenantClients {

  private static final Logger LOG = LoggerFactory.getLogger(PhysicalTenantClients.class);

  private PhysicalTenantClients() {}

  /**
   * Resolves the {@code CamundaClient} that per-physical-tenant builders fall back to when the
   * registry cannot resolve a client name itself (see {@link #resolveClient}): the sole configured
   * client, or the one designated {@code camunda.clients.<name>.primary=true}.
   *
   * <p>Returns {@code null} when several clients are configured and none is designated primary,
   * which is exactly the case where a fallback must not be applied anyway — substituting one
   * tenant's client for another's lookup failure would misroute silently. A plain {@code @Autowired
   * CamundaClient} parameter cannot express this: {@code required = false} covers the "no bean"
   * case but still fails the whole context with {@code NoUniqueBeanDefinitionException} on an
   * ambiguous one (#8977).
   */
  public static CamundaClient legacyClient(ObjectProvider<CamundaClient> camundaClientProvider) {
    return camundaClientProvider.getIfUnique();
  }

  /**
   * Resolves the {@code CamundaClient} backing one of the legacy scalar, single-client beans kept
   * for backward compatibility ({@code documentStore}, {@code secretKeyCache}, {@code
   * brokerJobStreamClient}, the ambient FEEL evaluator): the sole configured client, or the one
   * designated {@code camunda.clients.<name>.primary=true}.
   *
   * <p>When several clients are configured and none is designated primary there is no such client,
   * so the first one is used and a warning naming {@code beanName} is logged. Every
   * physical-tenant-aware path — job worker fan-out, inbound executables, the {@code /outbound}
   * listing — goes through the per-physical-tenant maps built from the registry instead, so this
   * choice only affects code injecting one of those scalar beans directly; designating a primary
   * client makes it explicit.
   */
  public static CamundaClient defaultClient(
      ObjectProvider<CamundaClient> camundaClientProvider, String beanName) {
    var uniqueClient = camundaClientProvider.getIfUnique();
    if (uniqueClient != null) {
      return uniqueClient;
    }
    var client =
        camundaClientProvider
            .orderedStream()
            .findFirst()
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "No CamundaClient configured for '" + beanName + "'"));
    LOG.warn(
        "Several CamundaClients are configured and none is designated primary; the single-client "
            + "'{}' bean is bound to the client of physical tenant '{}'. Set "
            + "camunda.clients.<name>.primary=true to choose explicitly.",
        beanName,
        readPhysicalTenantIdIfAvailable(client));
    return client;
  }

  /**
   * Reads the client's own configured physical tenant ID for a log message, tolerating both an
   * unset one and a configuration that cannot be read at all — some test doubles defer real
   * initialization until a test container is ready and throw if queried during Spring context
   * startup. Only safe because nothing is decided on the result; routing reads it strictly instead,
   * since there a misread would send a job to the wrong cluster.
   */
  private static String readPhysicalTenantIdIfAvailable(CamundaClient client) {
    try {
      var physicalTenantId = client.getConfiguration().getPhysicalTenantId();
      return physicalTenantId == null || physicalTenantId.isBlank() ? "unknown" : physicalTenantId;
    } catch (RuntimeException e) {
      return "unknown";
    }
  }

  /**
   * Enumerates the configured client names: the {@link CamundaClientRegistry}'s own names when a
   * registry bean exists, otherwise a single synthetic {@code "default"} name representing a
   * directly-supplied legacy {@code CamundaClient} bean.
   */
  public static Set<String> clientNames(
      CamundaClientRegistry registry, CamundaClient legacyCamundaClient) {
    if (registry != null) {
      return registry.clientNames();
    }
    if (legacyCamundaClient != null) {
      return Set.of("default");
    }
    throw new IllegalStateException("No CamundaClient or CamundaClientRegistry configured");
  }

  /**
   * Resolves the {@link CamundaClient} for the given client name. Prefers the {@link
   * CamundaClientRegistry} entry, but falls back to a directly-supplied legacy {@code
   * CamundaClient} bean when the registry is absent entirely, or when the registry claims the name
   * exists but no matching bean was actually registered — e.g. when a {@code CamundaClient} bean is
   * supplied manually/overridden (as in test fixtures) instead of via {@code camunda.clients.*}.
   */
  public static CamundaClient resolveClient(
      CamundaClientRegistry registry, String name, CamundaClient legacyCamundaClient) {
    if (registry == null) {
      if (legacyCamundaClient == null) {
        throw new IllegalStateException("No CamundaClient configured for client '" + name + "'");
      }
      return legacyCamundaClient;
    }
    try {
      return registry.get(name);
    } catch (RuntimeException e) {
      if (legacyCamundaClient == null) {
        throw new IllegalStateException("No CamundaClient configured for client '" + name + "'", e);
      }
      return legacyCamundaClient;
    }
  }

  /**
   * Resolves the physical tenant ID for a configured {@code CamundaClientRegistry} client name: the
   * explicitly configured {@code physical-tenant-id} if present, otherwise the client name itself.
   * Falls back to the client name if the configuration cannot be read at all — some test doubles
   * defer real initialization until a test container is ready and throw if queried too early.
   */
  public static String resolvePhysicalTenantId(
      CamundaClientRegistry registry, String name, CamundaClient legacyCamundaClient) {
    try {
      var physicalTenantId =
          resolveClient(registry, name, legacyCamundaClient)
              .getConfiguration()
              .getPhysicalTenantId();
      return physicalTenantId != null ? physicalTenantId : name;
    } catch (RuntimeException e) {
      return name;
    }
  }

  /**
   * Builds a {@code Collectors.toMap} collector keyed by the resolved physical tenant ID for each
   * client name, failing clearly if two clients resolve to the same physical tenant ID rather than
   * silently dropping one.
   */
  public static <T> Collector<String, ?, Map<String, T>> toMapByPhysicalTenantId(
      CamundaClientRegistry registry,
      CamundaClient legacyCamundaClient,
      Function<String, T> valueFn) {
    return Collectors.toMap(
        name -> resolvePhysicalTenantId(registry, name, legacyCamundaClient),
        valueFn,
        (a, b) -> {
          throw new IllegalStateException(
              "Multiple CamundaClients resolve to the same physical tenant ID; "
                  + "each configured client must have a unique physical-tenant-id");
        });
  }
}
