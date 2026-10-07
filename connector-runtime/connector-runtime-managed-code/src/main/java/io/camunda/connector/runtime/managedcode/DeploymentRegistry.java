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

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * In-memory map from an artifact to its provider deployment, local to this runtime instance.
 *
 * <p>An operational cache, not a source of truth: losing it costs first-run latency, because
 * provisioning is idempotent and the provider deployment name is deterministic. Concurrent jobs for
 * the same key share one provisioning future (single flight). Runtime instances do not coordinate
 * with each other; they converge through create-if-absent at the provider (GAP-012).
 *
 * <p>A transiently failed entry is removed so that the next job attempt provisions again. A
 * permanently failed entry, such as a packaging error, is kept: the same key means the same bytes
 * and the same error. A provisioning that outlives a job's wait keeps running and is reused by the
 * next attempt.
 */
final class DeploymentRegistry implements AutoCloseable {

  enum Status {
    BUILDING,
    DEPLOYING,
    READY,
    FAILED
  }

  record Entry(
      ArtifactSpec.Key key,
      String language,
      String runtime,
      Status status,
      String providerDeploymentId,
      Instant createdAt,
      Instant lastUsedAt,
      String failureCode,
      String failureMessage) {

    private Entry with(Status status, String providerDeploymentId, Instant lastUsedAt) {
      return new Entry(
          key,
          language,
          runtime,
          status,
          providerDeploymentId,
          createdAt,
          lastUsedAt,
          failureCode,
          failureMessage);
    }

    private Entry failed(String failureCode, String failureMessage) {
      return new Entry(
          key,
          language,
          runtime,
          Status.FAILED,
          providerDeploymentId,
          createdAt,
          lastUsedAt,
          failureCode,
          failureMessage);
    }
  }

  private static final Logger LOG = LoggerFactory.getLogger(DeploymentRegistry.class);

  private final ManagedCodeProvider provider;
  private final ExecutorService provisioningExecutor;
  private final Clock clock;
  private final Map<ArtifactSpec.Key, CompletableFuture<ProviderDeployment>> deployments =
      new ConcurrentHashMap<>();
  private final Map<ArtifactSpec.Key, Entry> entries = new ConcurrentHashMap<>();

  DeploymentRegistry(ManagedCodeProvider provider, int provisioningConcurrency) {
    this(
        provider,
        Executors.newFixedThreadPool(
            provisioningConcurrency,
            Thread.ofPlatform().name("managed-code-provisioning-", 0).daemon().factory()),
        Clock.systemUTC());
  }

  DeploymentRegistry(
      ManagedCodeProvider provider, ExecutorService provisioningExecutor, Clock clock) {
    this.provider = provider;
    this.provisioningExecutor = provisioningExecutor;
    this.clock = clock;
  }

  /**
   * Returns the deployment for the artifact, starting provisioning if no deployment or provisioning
   * exists for its key. Callers must wait on the returned future with their own timeout and must
   * not complete or cancel it: it is shared by every job of the same artifact.
   */
  CompletableFuture<ProviderDeployment> ensureDeployment(ArtifactSpec artifact) {
    final var key = artifact.key();
    final var created = new CompletableFuture<ProviderDeployment>();
    final var existing = deployments.putIfAbsent(key, created);
    if (existing != null) {
      return existing;
    }
    final var now = clock.instant();
    entries.put(
        key,
        new Entry(
            key,
            artifact.language().language(),
            artifact.runtime(),
            Status.BUILDING,
            null,
            now,
            null,
            null,
            null));
    try {
      provisioningExecutor.execute(() -> provision(artifact, created));
    } catch (RejectedExecutionException e) {
      fail(
          key,
          created,
          new ProvisioningException("PLATFORM_UNAVAILABLE", "Provisioning was rejected", true, e));
    }
    return created;
  }

  /** Records an invocation of the deployment; used by the deferred eviction of unused entries. */
  void markUsed(ArtifactSpec.Key key) {
    entries.computeIfPresent(
        key,
        (ignored, entry) ->
            entry.with(entry.status(), entry.providerDeploymentId(), clock.instant()));
  }

  /**
   * Forgets a deployment that the provider no longer has, unless another job already replaced it.
   */
  void evict(ArtifactSpec.Key key, ProviderDeployment missing) {
    final var removed =
        deployments.computeIfPresent(
            key,
            (ignored, future) ->
                future.isDone()
                        && !future.isCompletedExceptionally()
                        && missing.equals(future.join())
                    ? null
                    : future);
    if (removed == null) {
      entries.remove(key);
    }
  }

  Optional<Entry> find(ArtifactSpec.Key key) {
    return Optional.ofNullable(entries.get(key));
  }

  @Override
  public void close() {
    provisioningExecutor.shutdownNow();
  }

  private void provision(ArtifactSpec artifact, CompletableFuture<ProviderDeployment> result) {
    final var key = artifact.key();
    try {
      LOG.info(
          "Provisioning managed-script artifact {} as provider deployment '{}'",
          key.digest(),
          artifact.deploymentName());
      final var handle = provider.ensureProvisioned(artifact);
      entries.computeIfPresent(
          key, (ignored, entry) -> entry.with(Status.DEPLOYING, handle.deploymentName(), null));
      final var deployment = handle.ready().toCompletableFuture().get();
      entries.computeIfPresent(
          key, (ignored, entry) -> entry.with(Status.READY, deployment.deploymentId(), null));
      LOG.info(
          "Managed-script artifact {} is ready as provider deployment '{}'",
          key.digest(),
          deployment.deploymentId());
      result.complete(deployment);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      fail(
          key,
          result,
          new ProvisioningException("PLATFORM_UNAVAILABLE", "Provisioning was interrupted", true));
    } catch (Throwable t) {
      fail(key, result, toProvisioningException(t));
    }
  }

  private void fail(
      ArtifactSpec.Key key,
      CompletableFuture<ProviderDeployment> result,
      ProvisioningException failure) {
    LOG.warn(
        "Provisioning of managed-script artifact {} failed ({}, retryable={}): {}",
        key.digest(),
        failure.code(),
        failure.retryable(),
        failure.getMessage());
    entries.computeIfPresent(
        key, (ignored, entry) -> entry.failed(failure.code(), failure.getMessage()));
    if (failure.retryable()) {
      deployments.remove(key, result);
      entries.remove(key);
    }
    result.completeExceptionally(failure);
  }

  private static ProvisioningException toProvisioningException(Throwable failure) {
    var cause = failure;
    while ((cause instanceof ExecutionException || cause instanceof CompletionException)
        && cause.getCause() != null) {
      cause = cause.getCause();
    }
    if (cause instanceof ProvisioningException provisioningException) {
      return provisioningException;
    }
    return new ProvisioningException(
        "PLATFORM_UNAVAILABLE",
        "Provider failed while provisioning: " + cause.getClass().getSimpleName(),
        true,
        cause);
  }
}
