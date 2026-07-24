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
package io.camunda.connector.runtime.inbound;

import io.camunda.client.CamundaClient;
import io.camunda.client.spring.bean.CamundaClientRegistry;
import io.camunda.connector.runtime.inbound.search.SearchQueryClient;
import io.camunda.connector.runtime.inbound.search.SearchQueryClientImpl;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collector;
import java.util.stream.Collectors;

/**
 * Physical-tenant-id resolution helpers shared across every {@code @Configuration} class that needs
 * to build a per-physical-tenant map from a {@link CamundaClientRegistry} ({@link
 * InboundConnectorRuntimeConfiguration}, {@link InboundCorrelationConfiguration}, {@code
 * ProcessDefinitionImportConfiguration}, {@code ProcessInstanceClientConfiguration}). Plain static
 * methods, deliberately not {@code @Bean}-produced: none of these consumers may declare a {@code
 * Map<String, X>}-typed {@code @Bean} parameter, since Spring's dependency resolution special-cases
 * any such parameter by collecting *all* beans of type {@code X} by name — including scalar
 * override beans (e.g. a test's {@code @MockitoBean SearchQueryClient}) — instead of using the bean
 * whose own declared type is the map. That would silently produce a map keyed by the scalar bean's
 * name rather than the real per-physical-tenant map.
 */
public final class PhysicalTenantIds {

  private PhysicalTenantIds() {}

  /**
   * Resolves the {@link CamundaClient} for the given client name. Prefers the {@link
   * CamundaClientRegistry} entry, but falls back to a directly-supplied single {@code
   * CamundaClient} bean when the registry claims the name exists (e.g. the "default" entry always
   * synthesized for legacy single-client configs) but no matching bean was actually registered —
   * for example when a {@code CamundaClient} bean is supplied manually/overridden (as in test
   * fixtures) instead of via {@code camunda.clients.*}. Note {@link
   * CamundaClientRegistry#find(String)} does not guard against this case itself: it still resolves
   * the underlying bean and throws if it is missing, so the fallback must catch that failure
   * directly rather than rely on an empty {@code Optional}.
   */
  static CamundaClient resolveClient(
      CamundaClientRegistry registry, String name, CamundaClient legacyCamundaClient) {
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
   * explicitly configured {@code physical-tenant-id} if present, otherwise the client name itself
   * (which is always present, unlike the optional physical-tenant-id configuration). Falls back to
   * the client name if the configuration cannot be read at all — some test doubles (e.g. the {@code
   * camunda-process-test-spring} client proxy) defer real initialization until the test container
   * is ready and throw if queried during Spring context startup.
   */
  static String resolvePhysicalTenantId(
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
   * client name, failing clearly if two clients resolve to the same physical tenant ID (a
   * misconfiguration) rather than silently dropping one.
   */
  static <T> Collector<String, ?, Map<String, T>> toMapByPhysicalTenantId(
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

  /**
   * Resolves the single value of a per-physical-tenant map for backward-compatible scalar beans,
   * failing clearly when more than one physical tenant is configured instead of silently picking an
   * arbitrary one.
   */
  static <T> T onlyValue(Map<String, T> byPhysicalTenantId, Class<T> beanType) {
    if (byPhysicalTenantId.size() != 1) {
      throw new IllegalStateException(
          "No single "
              + beanType.getSimpleName()
              + " bean available: "
              + byPhysicalTenantId.size()
              + " physical tenants are configured; inject Map<String, "
              + beanType.getSimpleName()
              + "> instead.");
    }
    return byPhysicalTenantId.values().iterator().next();
  }

  /**
   * Builds one {@link SearchQueryClient} per configured physical tenant. When a {@code
   * SearchQueryClient} bean is manually supplied (e.g. a test's {@code @MockitoBean}, used to
   * control process-definition search results), that bean is used in place of constructing a real
   * client — mirroring the {@code legacyCamundaClient} fallback above, since overriding this bean
   * only makes sense for a single, legacy-style client configuration.
   */
  public static Map<String, SearchQueryClient> buildSearchQueryClientsByPhysicalTenantId(
      CamundaClientRegistry registry,
      CamundaClient legacyCamundaClient,
      SearchQueryClient legacySearchQueryClient,
      int limit) {
    return registry.clientNames().stream()
        .collect(
            toMapByPhysicalTenantId(
                registry,
                legacyCamundaClient,
                name ->
                    legacySearchQueryClient != null
                        ? legacySearchQueryClient
                        : new SearchQueryClientImpl(
                            resolveClient(registry, name, legacyCamundaClient), limit)));
  }
}
