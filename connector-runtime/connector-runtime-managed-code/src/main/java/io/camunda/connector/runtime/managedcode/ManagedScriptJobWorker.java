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
package io.camunda.connector.runtime.managedcode;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.worker.JobWorker;
import io.camunda.client.lifecycle.CamundaClientLifecycleAware;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Opens one managed-script job worker for each Camunda client, that is per physical tenant. */
public final class ManagedScriptJobWorker implements CamundaClientLifecycleAware {

  static final String JOB_TYPE = "io.camunda:managed-script:1";
  private static final Duration JOB_TIMEOUT_MARGIN = Duration.ofSeconds(10);
  private static final Logger LOG = LoggerFactory.getLogger(ManagedScriptJobWorker.class);
  private static final String DEFAULT_CLIENT_NAME = "default";

  private final ManagedScriptJobHandler handler;
  private final ManagedCodeProperties properties;
  private final Map<CamundaClient, JobWorker> workers = new ConcurrentHashMap<>();

  ManagedScriptJobWorker(ManagedScriptJobHandler handler, ManagedCodeProperties properties) {
    this.handler = handler;
    this.properties = properties;
  }

  /** The job must stay activated while the first job of an artifact waits for provisioning. */
  static Duration jobTimeout(ManagedCodeProperties properties) {
    return properties
        .provisioningTimeout()
        .plus(properties.executionTimeout())
        .plus(JOB_TIMEOUT_MARGIN);
  }

  @Override
  public void onStart(CamundaClient client) {
    onStart(client, DEFAULT_CLIENT_NAME);
  }

  @Override
  public void onStop(CamundaClient client) {
    onStop(client, DEFAULT_CLIENT_NAME);
  }

  @Override
  public void onStart(CamundaClient client, String clientName) {
    final var physicalTenantId = resolvePhysicalTenantId(client, clientName);
    workers.computeIfAbsent(
        client,
        ignored -> {
          LOG.info(
              "Starting managed-script worker for physical tenant '{}' with provider '{}'",
              physicalTenantId,
              properties.provider());
          return client
              .newWorker()
              .jobType(JOB_TYPE)
              .handler((jobClient, job) -> handler.handle(client, physicalTenantId, jobClient, job))
              .name(properties.workerName())
              .timeout(jobTimeout(properties))
              .maxJobsActive(properties.invocationConcurrency())
              .withLease(true)
              .open();
        });
  }

  @Override
  public void onStop(CamundaClient client, String clientName) {
    final var worker = workers.remove(client);
    if (worker != null) {
      worker.close();
    }
  }

  public void shutdown() {
    workers.values().forEach(JobWorker::close);
    workers.clear();
  }

  private static String resolvePhysicalTenantId(CamundaClient client, String clientName) {
    try {
      final var physicalTenantId = client.getConfiguration().getPhysicalTenantId();
      return physicalTenantId == null ? clientName : physicalTenantId;
    } catch (RuntimeException e) {
      return clientName;
    }
  }
}
