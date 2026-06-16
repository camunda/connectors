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
package io.camunda.connector.e2e;

import io.camunda.client.CamundaClient;
import org.junit.jupiter.api.extension.AfterTestExecutionCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.springframework.test.context.junit.jupiter.SpringExtension;

/**
 * On any test failure, dumps the Camunda cluster topology + per-partition health (and active
 * incidents) to {@code System.err}. Surefire captures this into the {@code *-output.txt} artifact,
 * so every flaky CI failure self-documents the broker state at failure time — making it possible to
 * tell a transient partition-health flap (e.g. "Services not installed" / "Snapshot not taken yet")
 * apart from a genuine test bug without re-running.
 *
 * <p>Implemented as an {@link AfterTestExecutionCallback} rather than a {@code TestWatcher} on
 * purpose: camunda-process-test unbinds the proxied {@link CamundaClient} in Spring's {@code
 * afterTestMethod} (an {@code AfterEachCallback}), which runs <em>after</em> {@code
 * TestWatcher#testFailed}. {@code afterTestExecution} runs before any {@code AfterEachCallback}, so
 * the client is still usable here.
 *
 * <p>Read-only and strictly best-effort: it swallows its own errors and never masks the original
 * test failure. Apply with {@code @ExtendWith(ClusterStateDumpExtension.class)} on a base test
 * class.
 */
public class ClusterStateDumpExtension implements AfterTestExecutionCallback {

  @Override
  public void afterTestExecution(final ExtensionContext context) {
    if (context.getExecutionException().isEmpty()) {
      return; // test passed — nothing to dump
    }
    System.err.println(
        "===== CLUSTER STATE DUMP (test failed: " + context.getDisplayName() + ") =====");
    try {
      final CamundaClient client = resolveClient(context);
      if (client == null) {
        System.err.println("[cluster-dump] no CamundaClient in application context; skipping");
        return;
      }
      dumpTopology(client);
      dumpIncidents(client);
    } catch (final Throwable t) {
      System.err.println("[cluster-dump] failed to collect cluster state: " + t);
    } finally {
      System.err.println("===== END CLUSTER STATE DUMP =====");
    }
  }

  private CamundaClient resolveClient(final ExtensionContext context) {
    try {
      return SpringExtension.getApplicationContext(context).getBean(CamundaClient.class);
    } catch (final Throwable t) {
      return null;
    }
  }

  private void dumpTopology(final CamundaClient client) {
    try {
      final var topology = client.newTopologyRequest().send().join();
      System.err.printf(
          "[cluster-dump] topology: clusterSize=%d partitions=%d replicationFactor=%d brokers=%d%n",
          topology.getClusterSize(),
          topology.getPartitionsCount(),
          topology.getReplicationFactor(),
          topology.getBrokers().size());
      topology
          .getBrokers()
          .forEach(
              broker -> {
                System.err.printf(
                    "[cluster-dump]   broker %d (%s:%d) version=%s%n",
                    broker.getNodeId(), broker.getHost(), broker.getPort(), broker.getVersion());
                broker
                    .getPartitions()
                    .forEach(
                        p ->
                            System.err.printf(
                                "[cluster-dump]     partition %d role=%s health=%s%n",
                                p.getPartitionId(), p.getRole(), p.getHealth()));
              });
    } catch (final Throwable t) {
      System.err.println(
          "[cluster-dump] TOPOLOGY REQUEST FAILED (broker unreachable/unhealthy?): " + t);
    }
  }

  private void dumpIncidents(final CamundaClient client) {
    try {
      final var incidents = client.newIncidentSearchRequest().send().join();
      final var items = incidents.items();
      System.err.printf("[cluster-dump] active incidents: %d%n", items.size());
      items.stream().limit(20).forEach(i -> System.err.println("[cluster-dump]   " + i));
    } catch (final Throwable t) {
      System.err.println("[cluster-dump] incident search failed: " + t);
    }
  }
}
