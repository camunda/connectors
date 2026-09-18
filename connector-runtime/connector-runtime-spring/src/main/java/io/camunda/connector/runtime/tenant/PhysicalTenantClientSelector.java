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
import io.camunda.connector.api.outbound.JobContext;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import org.springframework.beans.factory.ObjectProvider;

/**
 * Selects the {@link CamundaClient} serving a job's physical tenant, for collaborators that call
 * the orchestration cluster while handling a job and therefore have to reach <em>that job's</em>
 * cluster.
 *
 * <p>A collaborator holding a single client picked once at startup (see {@link
 * PhysicalTenantClients#defaultClient}) sends every request to one cluster, which misroutes as soon
 * as the runtime serves more than one physical tenant. Such a collaborator should take this
 * selector instead and resolve per job, from {@link JobContext#getPhysicalTenantId()}.
 *
 * <p>Clients are keyed by their own configured {@code physical-tenant-id} rather than by their
 * {@code camunda.clients.<name>} bean name, because the configured ID is what the engine reports on
 * an activated job. A client configured without one keeps an explicit route of its own, so jobs
 * from its cluster — which carry no physical tenant either — reach it instead of being handed to
 * some other tenant's client. Two such clients cannot be told apart and are rejected.
 *
 * <p>A lone configured client always wins, whatever the job reports: a single-cluster runtime
 * activates jobs that carry no physical tenant at all, and routing them to the one client it has is
 * the only sensible answer.
 *
 * <p>The mapping is built on first use rather than in the constructor, since some client test
 * doubles (e.g. the {@code camunda-process-test-spring} proxy) defer real initialization until the
 * test container is ready and throw if their configuration is read during Spring context startup.
 */
public class PhysicalTenantClientSelector {

  /**
   * Route of a client configured without a physical tenant. Not a possible tenant ID of its own:
   * both a job's and a client's blank physical tenant are normalized to {@code null} before they
   * are keyed.
   */
  private static final String NO_PHYSICAL_TENANT = "";

  private final ObjectProvider<CamundaClient> camundaClientProvider;

  private volatile Clients clients;

  public PhysicalTenantClientSelector(ObjectProvider<CamundaClient> camundaClientProvider) {
    this.camundaClientProvider = camundaClientProvider;
  }

  private record Clients(List<CamundaClient> candidates, Map<String, CamundaClient> byRoute) {}

  /** Selects the client for the physical tenant the given job was activated from. */
  public CamundaClient forJob(JobContext jobContext) {
    return forPhysicalTenant(jobContext.getPhysicalTenantId());
  }

  /**
   * Whether exactly one client is configured, so every job resolves to it. Lets a collaborator keep
   * using an overridable single-client bean (an in-memory document store in tests, say) in that
   * case, and only build its own per-tenant instances when there is genuinely more than one cluster
   * to serve — mirroring how the runtime treats its own per-physical-tenant maps.
   *
   * <p>Counts configured clients, not resolvable routes: were it the latter, a topology whose
   * clients do not all carry a physical tenant could enable the shortcut while still serving
   * several clusters, and send every job to whichever one happens to be routable.
   */
  public boolean servesSinglePhysicalTenant() {
    return clients().candidates().size() == 1;
  }

  /**
   * Selects the client configured for {@code physicalTenantId} — or the client configured without
   * one when {@code physicalTenantId} is absent, or the only configured client when there is just
   * one.
   *
   * @throws IllegalStateException when no client serves that physical tenant, rather than falling
   *     back to another tenant's client and silently reading or writing on the wrong cluster
   */
  public CamundaClient forPhysicalTenant(String physicalTenantId) {
    var resolved = clients();
    if (resolved.candidates().size() == 1) {
      return resolved.candidates().getFirst();
    }
    var client = resolved.byRoute().get(route(physicalTenantId));
    if (client == null) {
      throw new IllegalStateException(
          "No CamundaClient configured for physical tenant '"
              + physicalTenantId
              + "'; configured physical tenants: "
              + describeRoutes(resolved.byRoute().keySet()));
    }
    return client;
  }

  private static String route(String physicalTenantId) {
    return physicalTenantId == null || physicalTenantId.isBlank()
        ? NO_PHYSICAL_TENANT
        : physicalTenantId;
  }

  private static String describeRoutes(java.util.Set<String> routes) {
    var named = new TreeSet<>(routes);
    boolean hasUntenanted = named.remove(NO_PHYSICAL_TENANT);
    return hasUntenanted ? named + " plus one client without a physical tenant" : named.toString();
  }

  private Clients clients() {
    var resolved = clients;
    if (resolved == null) {
      synchronized (this) {
        resolved = clients;
        if (resolved == null) {
          clients = resolved = resolveClients();
        }
      }
    }
    return resolved;
  }

  private Clients resolveClients() {
    List<CamundaClient> candidates = camundaClientProvider.orderedStream().toList();
    if (candidates.isEmpty()) {
      throw new IllegalStateException("No CamundaClient configured");
    }
    Map<String, CamundaClient> byRoute = new LinkedHashMap<>();
    for (CamundaClient candidate : candidates) {
      var route = route(PhysicalTenantClients.readPhysicalTenantIdOrNull(candidate));
      if (byRoute.putIfAbsent(route, candidate) != null) {
        throw new IllegalStateException(
            NO_PHYSICAL_TENANT.equals(route)
                ? "Several CamundaClients are configured without a physical-tenant-id, so the "
                    + "cluster a job came from cannot be told apart; set "
                    + "camunda.clients.<name>.physical-tenant-id on all but at most one of them"
                : "Multiple CamundaClients resolve to the same physical tenant ID '"
                    + route
                    + "'; each configured client must have a unique physical-tenant-id");
      }
    }
    return new Clients(candidates, byRoute);
  }
}
