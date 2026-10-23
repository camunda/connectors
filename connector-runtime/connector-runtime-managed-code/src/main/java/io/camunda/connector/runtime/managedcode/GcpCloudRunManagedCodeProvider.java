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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.cloud.functions.v2.BuildConfig;
import com.google.cloud.functions.v2.Environment;
import com.google.cloud.functions.v2.Function;
import com.google.cloud.functions.v2.ServiceConfig;
import com.google.cloud.functions.v2.Source;
import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Deploys each artifact as its own Google Cloud Run function (Cloud Functions v2 API) and invokes
 * it through its {@code run.app} URL.
 *
 * <ul>
 *   <li>The function name is the deterministic deployment name of the artifact, so concurrent
 *       runtime instances converge: one create is accepted, the others find the function and wait
 *       for it. An existing function is adopted without uploading its source again, which saves
 *       writes from the 60 per minute that every cluster of the project shares.
 *   <li>The function runs as a service account that holds no permissions. It is required: without
 *       it the platform would use the default Compute Engine service account, which often has
 *       Editor on the project.
 *   <li>Every create sets memory, timeout and {@code maxInstanceCount}, which function code cannot
 *       change.
 *   <li>The content of a function never changes, since its name is derived from its digest, so the
 *       revision it serves is the one that was built.
 * </ul>
 */
public final class GcpCloudRunManagedCodeProvider implements ManagedCodeProvider {

  public static final String NAME = "gcp";

  static final String OWNER_LABEL = "camunda-io_managed-by";
  static final String OWNER_VALUE = "camunda-managed-script";
  static final String DIGEST_LABEL = "camunda-io_artifact-digest";

  private static final Logger LOG = LoggerFactory.getLogger(GcpCloudRunManagedCodeProvider.class);
  private static final int MAX_LABEL_VALUE_LENGTH = 63;
  private static final String DEPLOYMENT_ID_SEPARATOR = "@";

  private final GcpCloudRunScriptPackager packager = new GcpCloudRunScriptPackager();
  private final CloudRunFunctionsApi functions;
  private final ObjectMapper objectMapper;
  private final String location;
  private final String functionServiceAccount;
  private final int memoryMb;
  private final int maxInstanceCount;
  private final Duration executionTimeout;
  private final Duration provisioningTimeout;
  private final Duration pollInterval;

  /**
   * @param location {@code projects/<project>/locations/<region>}
   */
  public GcpCloudRunManagedCodeProvider(
      CloudRunFunctionsApi functions,
      ObjectMapper objectMapper,
      String location,
      String functionServiceAccount,
      int memoryMb,
      int maxInstanceCount,
      Duration executionTimeout,
      Duration provisioningTimeout,
      Duration pollInterval) {
    this.functions = functions;
    this.objectMapper = objectMapper;
    this.location = location;
    this.functionServiceAccount = functionServiceAccount;
    this.memoryMb = memoryMb;
    this.maxInstanceCount = maxInstanceCount;
    this.executionTimeout = executionTimeout;
    this.provisioningTimeout = provisioningTimeout;
    this.pollInterval = pollInterval;
  }

  @Override
  public String name() {
    return NAME;
  }

  @Override
  public String buildVersion() {
    return packager.buildVersion();
  }

  /** Runs on the provisioning executor of the registry, so it creates and waits synchronously. */
  @Override
  public ProvisioningHandle ensureProvisioned(ArtifactSpec artifact) {
    final var name = artifact.deploymentName();
    final var functionName = functionName(name);
    final long start = System.nanoTime();
    final long deadline = start + provisioningTimeout.toNanos();
    // Shorter than the wait of the job, so that a lasting denial consumes a retry and ends in an
    // incident, instead of the job waiting again on every attempt.
    final long denialDeadline = start + provisioningTimeout.toNanos() / 2;
    boolean createRequested = false;
    while (true) {
      final Function function;
      try {
        final var existing = functions.get(functionName);
        if (existing.isEmpty()) {
          if (!createRequested) {
            create(artifact, functionName);
            createRequested = true;
          }
          function = null;
        } else {
          function = existing.get();
        }
      } catch (ProvisioningException e) {
        final long now = System.nanoTime();
        if (!e.retryable()
            || now > deadline
            || (GoogleCloudRunFunctionsApi.DENIED.equals(e.code()) && now > denialDeadline)) {
          throw e;
        }
        // Throttled, unavailable, or a new grant not yet in effect (about 2 minutes): keep
        // waiting. A get of a missing name of the prefix is NOT_FOUND, not a denial (SRE, Q3b).
        LOG.debug("Provisioning '{}' will be retried: {}", name, e.getMessage());
        sleep(withJitter(pollInterval.multipliedBy(2)));
        continue;
      }
      if (function != null) {
        switch (function.getState()) {
          case ACTIVE -> {
            final var uri = function.getServiceConfig().getUri();
            if (!uri.isBlank()) {
              final var deployment =
                  new ProviderDeployment(NAME, name + DEPLOYMENT_ID_SEPARATOR + uri);
              return new ProvisioningHandle(name, CompletableFuture.completedFuture(deployment));
            }
          }
          case FAILED ->
              throw new ProvisioningException(
                  "BUILD_FAILED",
                  "Cloud Run function '%s' failed: %s".formatted(name, stateMessages(function)),
                  false);
          default -> {
            // DEPLOYING, or DELETING after an eviction: wait.
          }
        }
      }
      if (System.nanoTime() > deadline) {
        throw new ProvisioningException(
            "PROVISIONING_TIMEOUT",
            "Cloud Run function '%s' is still %s"
                .formatted(name, function == null ? "being created" : function.getState()),
            true);
      }
      sleep(withJitter(pollInterval));
    }
  }

  @Override
  public ExecutionResponse invoke(ProviderDeployment deployment, ExecutionRequest request) {
    final var id = DeploymentId.parse(deployment.deploymentId());
    if (id.uri() == null) {
      return ExecutionResponse.failed(
          ExecutionResponse.DEPLOYMENT_MISSING,
          "Deployment '%s' has no function URL".formatted(deployment.deploymentId()),
          false);
    }
    final byte[] payload;
    try {
      payload = objectMapper.writeValueAsBytes(request.contractPayload());
    } catch (IOException e) {
      return ExecutionResponse.failed("INVALID_INPUT", "Cannot serialize the script input", false);
    }
    final CloudRunFunctionsApi.HttpResult result;
    try {
      // The platform's own timeout answers first; this one only guards against a lost response.
      result = functions.invoke(id.uri(), payload, executionTimeout.plusSeconds(30));
    } catch (ProvisioningException e) {
      return ExecutionResponse.failed(e.code(), e.getMessage(), e.retryable());
    }
    return switch (result.statusCode()) {
      case 200 -> parse(result.body());
      case 404 ->
          ExecutionResponse.failed(
              ExecutionResponse.DEPLOYMENT_MISSING,
              "Cloud Run function '%s' does not exist".formatted(id.name()),
              false);
      // A new cluster's invoker grant takes about a minute to apply.
      case 401, 403 ->
          ExecutionResponse.failed(
              "PLATFORM_UNAVAILABLE",
              "Not allowed to invoke '%s' (HTTP %d)".formatted(id.name(), result.statusCode()),
              true);
      case 429 ->
          ExecutionResponse.failed(
              "PLATFORM_THROTTLED",
              "Cloud Run function '%s' is throttled".formatted(id.name()),
              true);
      case 502, 503 ->
          ExecutionResponse.failed(
              "PLATFORM_UNAVAILABLE",
              "Cloud Run function '%s' is unavailable (HTTP %d)"
                  .formatted(id.name(), result.statusCode()),
              true);
      // The entrypoint answers script failures with 200, so anything else is the platform's, such
      // as a timeout (504) or an exhausted memory limit (500).
      default ->
          ExecutionResponse.failed(
              "PLATFORM_EXECUTION_FAILED",
              "Cloud Run function error (HTTP %d): %s"
                  .formatted(result.statusCode(), bounded(result.body())),
              false);
    };
  }

  @Override
  public void delete(ProviderDeployment deployment) {
    functions.delete(functionName(DeploymentId.parse(deployment.deploymentId()).name()));
  }

  /** Closes the API client; Spring calls it when the context stops. */
  public void close() {
    functions.close();
  }

  private void create(ArtifactSpec artifact, String functionName) {
    final var scriptPackage = packager.packageFor(artifact);
    final var source = functions.upload(scriptPackage.zip());
    final var function =
        Function.newBuilder()
            .setName(functionName)
            .setDescription("Camunda managed script " + artifact.resourceName())
            .setEnvironment(Environment.GEN_2)
            .putAllLabels(
                Map.of(
                    OWNER_LABEL,
                    OWNER_VALUE,
                    DIGEST_LABEL,
                    artifact.digest().substring(0, MAX_LABEL_VALUE_LENGTH)))
            .setBuildConfig(
                BuildConfig.newBuilder()
                    .setRuntime(scriptPackage.runtime())
                    .setEntryPoint(scriptPackage.entrypoint())
                    .setSource(Source.newBuilder().setStorageSource(source)))
            .setServiceConfig(
                ServiceConfig.newBuilder()
                    .setAvailableMemory(memoryMb + "Mi")
                    .setTimeoutSeconds(Math.toIntExact(Math.max(1, executionTimeout.toSeconds())))
                    .setServiceAccountEmail(functionServiceAccount)
                    .setMaxInstanceCount(maxInstanceCount)
                    .setMaxInstanceRequestConcurrency(1)
                    .setAllTrafficOnLatestRevision(true))
            .build();
    if (functions.create(artifact.deploymentName(), function)) {
      LOG.debug("Creating Cloud Run function '{}'", artifact.deploymentName());
    } else {
      LOG.debug("Cloud Run function '{}' already exists; adopting it", artifact.deploymentName());
    }
  }

  private ExecutionResponse parse(String body) {
    try {
      return objectMapper.readValue(body, ExecutionResponse.class);
    } catch (IOException e) {
      return ExecutionResponse.failed(
          "INVALID_RESULT", "The function returned no valid contract response", false);
    }
  }

  private String functionName(String name) {
    return location + "/functions/" + name;
  }

  private static String stateMessages(Function function) {
    final var messages =
        function.getStateMessagesList().stream()
            .map(message -> message.getMessage())
            .filter(message -> !message.isBlank())
            .collect(Collectors.joining("; "));
    return messages.isEmpty() ? "no reason given" : bounded(messages);
  }

  private static String bounded(String value) {
    return value.length() <= 512 ? value : value.substring(0, 512);
  }

  /** Up to 50% more, so that runtimes started together do not poll in step. */
  private static Duration withJitter(Duration duration) {
    final long millis = Math.max(1, duration.toMillis());
    return Duration.ofMillis(millis + ThreadLocalRandom.current().nextLong(millis / 2 + 1));
  }

  private static void sleep(Duration duration) {
    try {
      Thread.sleep(duration);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new ProvisioningException(
          "PLATFORM_UNAVAILABLE", "Provisioning was interrupted", true, e);
    }
  }

  /** {@code <name>@<run.app URL>}: the name to manage the function, the URL to invoke it. */
  record DeploymentId(String name, String uri) {
    static DeploymentId parse(String deploymentId) {
      final int separator = deploymentId.indexOf(DEPLOYMENT_ID_SEPARATOR);
      if (separator < 0) {
        return new DeploymentId(deploymentId, null);
      }
      return new DeploymentId(
          deploymentId.substring(0, separator), deploymentId.substring(separator + 1));
    }
  }
}
