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
import io.camunda.client.lifecycle.CamundaClientLifecycleAware;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

/** Bounded, per-physical-tenant managed-code reconciliation scheduler. */
public final class ManagedCodeReconciler implements CamundaClientLifecycleAware {

  private static final Logger LOG = LoggerFactory.getLogger(ManagedCodeReconciler.class);

  private final ManagedScriptControlPlane controlPlane;
  private final ManagedCodeDeploymentProvider deploymentProvider;
  private final ManagedCodeProperties properties;
  private final Map<String, String> physicalTenantIdByClientName;
  private final Map<String, CamundaClient> activeClientsByPhysicalTenantId;
  private final Map<String, AtomicBoolean> runningByPhysicalTenantId = new ConcurrentHashMap<>();
  private final ThreadPoolExecutor executor;
  private final ScheduledThreadPoolExecutor leaseRenewalExecutor;
  private final AtomicBoolean stopped = new AtomicBoolean();

  public ManagedCodeReconciler(
      ManagedScriptControlPlane controlPlane,
      ManagedCodeDeploymentProvider deploymentProvider,
      ManagedCodeProperties properties,
      Map<String, String> physicalTenantIdByClientName,
      Map<String, CamundaClient> clientsByPhysicalTenantId) {
    this.controlPlane = controlPlane;
    this.deploymentProvider = deploymentProvider;
    this.properties = properties;
    this.physicalTenantIdByClientName = Map.copyOf(physicalTenantIdByClientName);
    this.activeClientsByPhysicalTenantId = new ConcurrentHashMap<>(clientsByPhysicalTenantId);
    this.physicalTenantIdByClientName
        .values()
        .forEach(id -> runningByPhysicalTenantId.put(id, new AtomicBoolean()));
    this.executor =
        new ThreadPoolExecutor(
            properties.concurrency(),
            properties.concurrency(),
            0,
            TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(properties.queueCapacity()),
            Thread.ofPlatform().name("managed-code-reconciler-", 0).factory(),
            new ThreadPoolExecutor.AbortPolicy());
    leaseRenewalExecutor =
        new ScheduledThreadPoolExecutor(
            Math.min(properties.concurrency(), 4),
            Thread.ofPlatform().name("managed-code-lease-renewal-", 0).factory());
    leaseRenewalExecutor.setRemoveOnCancelPolicy(true);
  }

  @Scheduled(
      fixedDelayString = "${camunda.connector.managed-code.interval:PT5S}",
      initialDelayString = "${camunda.connector.managed-code.initial-delay:PT0S}")
  public void reconcile() {
    if (stopped.get()) {
      return;
    }
    activeClientsByPhysicalTenantId.forEach(this::submitIfIdle);
  }

  private void submitIfIdle(String physicalTenantId, CamundaClient client) {
    var running =
        runningByPhysicalTenantId.computeIfAbsent(physicalTenantId, ignored -> new AtomicBoolean());
    if (!running.compareAndSet(false, true)) {
      return;
    }
    try {
      executor.execute(() -> reconcileTenant(physicalTenantId, client, running));
    } catch (RejectedExecutionException e) {
      running.set(false);
      if (!stopped.get()) {
        LOG.warn(
            "Managed-code reconciliation queue is full; skipping physical tenant '{}'",
            physicalTenantId);
      }
    }
  }

  private void reconcileTenant(
      String physicalTenantId, CamundaClient client, AtomicBoolean running) {
    try {
      var deployments =
          controlPlane.acquireDeployments(
              client,
              physicalTenantId,
              properties.worker(),
              properties.batchSize(),
              properties.leaseDuration());
      for (var deployment : deployments) {
        reconcileDeployment(client, physicalTenantId, deployment);
      }
    } catch (Exception e) {
      LOG.error(
          "Failed to acquire managed-code deployments for physical tenant '{}'",
          physicalTenantId,
          e);
    } finally {
      running.set(false);
    }
  }

  private void reconcileDeployment(
      CamundaClient client, String physicalTenantId, ManagedScriptDeployment deployment) {
    final var currentDeployment = new AtomicReference<>(deployment);
    try {
      if (!deploymentProvider.provider().equals(deployment.provider())) {
        throw new IllegalArgumentException(
            "Deployment provider '%s' cannot handle provider '%s'"
                .formatted(deploymentProvider.provider(), deployment.provider()));
      }
      var renewal = scheduleLeaseRenewal(client, physicalTenantId, currentDeployment);
      try {
        var operation = resolveOperationId(client, physicalTenantId, currentDeployment.get());
        currentDeployment.set(operation.deployment());
        var result =
            deploymentProvider.resumeDeployment(operation.deployment(), operation.operationId());
        controlPlane.completeDeployment(client, physicalTenantId, operation.deployment(), result);
      } finally {
        renewal.cancel(false);
      }
    } catch (Exception deploymentFailure) {
      LOG.error(
          "Failed to reconcile managed-code deployment '{}' for physical tenant '{}'",
          deployment.deploymentId(),
          physicalTenantId,
          deploymentFailure);
      try {
        controlPlane.failDeployment(
            client,
            physicalTenantId,
            currentDeployment.get(),
            ManagedCodeDeploymentFailure.from(deploymentFailure));
      } catch (Exception reportingFailure) {
        deploymentFailure.addSuppressed(reportingFailure);
        LOG.error(
            "Failed to report managed-code deployment '{}' failure for physical tenant '{}'",
            deployment.deploymentId(),
            physicalTenantId,
            reportingFailure);
      }
    }
  }

  private ResolvedOperation resolveOperationId(
      CamundaClient client, String physicalTenantId, ManagedScriptDeployment deployment)
      throws Exception {
    if (deployment.existingProviderOperationId().isPresent()) {
      return new ResolvedOperation(
          deployment, deployment.existingProviderOperationId().orElseThrow());
    }
    var operationId = deploymentProvider.startDeployment(deployment);
    var updatedDeployment =
        controlPlane.recordProviderOperation(client, physicalTenantId, deployment, operationId);
    return new ResolvedOperation(updatedDeployment, operationId);
  }

  private ScheduledFuture<?> scheduleLeaseRenewal(
      CamundaClient client,
      String physicalTenantId,
      AtomicReference<ManagedScriptDeployment> deployment) {
    final var renewalInterval = properties.leaseDuration().dividedBy(2);
    final var renewalIntervalMillis = Math.max(1, renewalInterval.toMillis());
    return leaseRenewalExecutor.scheduleWithFixedDelay(
        () -> renewLease(client, physicalTenantId, deployment.get()),
        renewalIntervalMillis,
        renewalIntervalMillis,
        TimeUnit.MILLISECONDS);
  }

  private record ResolvedOperation(ManagedScriptDeployment deployment, String operationId) {}

  private void renewLease(
      CamundaClient client, String physicalTenantId, ManagedScriptDeployment deployment) {
    try {
      controlPlane.renewLease(client, physicalTenantId, deployment, properties.leaseDuration());
    } catch (Exception e) {
      LOG.warn(
          "Failed to renew lease for managed-code deployment '{}' in physical tenant '{}'",
          deployment.deploymentId(),
          physicalTenantId,
          e);
    }
  }

  @Override
  public void onStart(CamundaClient client) {
    onStart(client, "default");
  }

  @Override
  public void onStop(CamundaClient client) {
    onStop(client, "default");
  }

  @Override
  public void onStart(CamundaClient client, String clientName) {
    var physicalTenantId = physicalTenantIdByClientName.get(clientName);
    if (physicalTenantId == null) {
      logUnknownClient("start", clientName);
      return;
    }
    activeClientsByPhysicalTenantId.put(physicalTenantId, client);
  }

  @Override
  public void onStop(CamundaClient client, String clientName) {
    var physicalTenantId = physicalTenantIdByClientName.get(clientName);
    if (physicalTenantId == null) {
      logUnknownClient("stop", clientName);
      return;
    }
    activeClientsByPhysicalTenantId.remove(physicalTenantId);
  }

  private void logUnknownClient(String event, String clientName) {
    LOG.warn(
        "Ignoring {} event for CamundaClient '{}': it is not configured for managed-code"
            + " reconciliation {}",
        event,
        clientName,
        physicalTenantIdByClientName.keySet());
  }

  Set<String> activePhysicalTenantIds() {
    return Set.copyOf(activeClientsByPhysicalTenantId.keySet());
  }

  boolean isShutdown() {
    return executor.isShutdown();
  }

  public void shutdown() {
    if (!stopped.compareAndSet(false, true)) {
      return;
    }
    executor.shutdown();
    awaitTermination(properties.shutdownTimeout());
    leaseRenewalExecutor.shutdown();
    awaitLeaseRenewalTermination(properties.shutdownTimeout());
  }

  private void awaitTermination(Duration timeout) {
    try {
      if (!executor.awaitTermination(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
        executor.shutdownNow();
      }
    } catch (InterruptedException e) {
      executor.shutdownNow();
      Thread.currentThread().interrupt();
    }
  }

  private void awaitLeaseRenewalTermination(Duration timeout) {
    try {
      if (!leaseRenewalExecutor.awaitTermination(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
        leaseRenewalExecutor.shutdownNow();
      }
    } catch (InterruptedException e) {
      leaseRenewalExecutor.shutdownNow();
      Thread.currentThread().interrupt();
    }
  }
}
