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
 * an activated job. A single configured client always wins regardless of the requested tenant: a
 * single-cluster runtime activates jobs that carry no physical tenant at all, and routing them to
 * the one client it has is the only sensible answer.
 *
 * <p>The mapping is built on first use rather than in the constructor, since some client test
 * doubles (e.g. the {@code camunda-process-test-spring} proxy) defer real initialization until the
 * test container is ready and throw if their configuration is read during Spring context startup.
 */
public class PhysicalTenantClientSelector {

  private final ObjectProvider<CamundaClient> camundaClientProvider;

  private volatile Map<String, CamundaClient> clientsByPhysicalTenantId;

  public PhysicalTenantClientSelector(ObjectProvider<CamundaClient> camundaClientProvider) {
    this.camundaClientProvider = camundaClientProvider;
  }

  /** Selects the client for the physical tenant the given job was activated from. */
  public CamundaClient forJob(JobContext jobContext) {
    return forPhysicalTenant(jobContext.getPhysicalTenantId());
  }

  /**
   * Whether exactly one client is configured, so every job resolves to it. Lets a collaborator keep
   * using an overridable single-client bean (an in-memory document store in tests, say) in that
   * case, and only build its own per-tenant instances when there is genuinely more than one cluster
   * to serve — mirroring how the runtime treats its own per-physical-tenant maps.
   */
  public boolean servesSinglePhysicalTenant() {
    return clients().size() == 1;
  }

  /**
   * Selects the client configured for {@code physicalTenantId}, or the only configured client when
   * there is just one.
   *
   * @throws IllegalStateException when no client serves that physical tenant, rather than falling
   *     back to another tenant's client and silently reading or writing on the wrong cluster
   */
  public CamundaClient forPhysicalTenant(String physicalTenantId) {
    var clients = clients();
    if (clients.size() == 1) {
      return clients.values().iterator().next();
    }
    var client = physicalTenantId == null ? null : clients.get(physicalTenantId);
    if (client == null) {
      throw new IllegalStateException(
          "No CamundaClient configured for physical tenant '"
              + physicalTenantId
              + "'; configured physical tenants: "
              + new TreeSet<>(clients.keySet()));
    }
    return client;
  }

  private Map<String, CamundaClient> clients() {
    var resolved = clientsByPhysicalTenantId;
    if (resolved == null) {
      synchronized (this) {
        resolved = clientsByPhysicalTenantId;
        if (resolved == null) {
          clientsByPhysicalTenantId = resolved = resolveClients();
        }
      }
    }
    return resolved;
  }

  /**
   * A client whose physical tenant ID cannot be read is kept only when it is the sole candidate:
   * there it is reachable through the single-client rule above, whereas among several clients it
   * could never be addressed by a job's physical tenant anyway.
   */
  private Map<String, CamundaClient> resolveClients() {
    List<CamundaClient> candidates = camundaClientProvider.orderedStream().toList();
    if (candidates.isEmpty()) {
      throw new IllegalStateException("No CamundaClient configured");
    }
    Map<String, CamundaClient> byPhysicalTenantId = new LinkedHashMap<>();
    for (CamundaClient candidate : candidates) {
      var physicalTenantId = PhysicalTenantClients.readPhysicalTenantIdOrNull(candidate);
      if (physicalTenantId == null) {
        if (candidates.size() == 1) {
          byPhysicalTenantId.put("", candidate);
        }
        continue;
      }
      if (byPhysicalTenantId.putIfAbsent(physicalTenantId, candidate) != null) {
        throw new IllegalStateException(
            "Multiple CamundaClients resolve to the same physical tenant ID '"
                + physicalTenantId
                + "'; each configured client must have a unique physical-tenant-id");
      }
    }
    if (byPhysicalTenantId.isEmpty()) {
      throw new IllegalStateException(
          "None of the "
              + candidates.size()
              + " configured CamundaClients has a physical-tenant-id; set "
              + "camunda.clients.<name>.physical-tenant-id so jobs can be routed to their cluster");
    }
    return byPhysicalTenantId;
  }
}
