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
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Runs scripts through one Google Cloud Run function (Cloud Functions v2 API) per language and
 * cluster, created when the runtime starts, and invokes it through its {@code run.app} URL. A
 * script is sent as data with its input; it creates no function.
 *
 * <ul>
 *   <li>An executor's name is the prefix of the cluster, its language and the digest of its
 *       wrapper, so concurrent runtime instances converge: one create is accepted, the others find
 *       the function and wait for it. An existing executor is adopted without uploading its source
 *       again, which saves writes from the 60 per minute that every cluster of the project shares.
 *   <li>The function runs as a service account that holds no permissions. It is required: without
 *       it the platform would use the default Compute Engine service account, which often has
 *       Editor on the project.
 *   <li>Every create sets memory, timeout and {@code maxInstanceCount}, which function code cannot
 *       change, and a request concurrency of 1: the wrapper kills every process it did not start
 *       with after each call, which would kill a concurrent script.
 *   <li>Old executors are not deleted after an upgrade: older runtime instances may still call
 *       them.
 * </ul>
 */
public final class GcpCloudRunManagedCodeProvider implements ManagedCodeProvider {

  public static final String NAME = "gcp";

  static final String OWNER_LABEL = "camunda-io_managed-by";
  static final String OWNER_VALUE = "camunda-managed-script";
  static final String EXECUTOR_LABEL = "camunda-io_executor-digest";

  /** The request size limit of Cloud Run. */
  static final int MAX_REQUEST_BYTES = 32 * 1024 * 1024;

  /**
   * Beyond the execution timeout, so that the wrapper, not Cloud Run, ends a script that runs late.
   */
  static final Duration FUNCTION_TIMEOUT_MARGIN = Duration.ofSeconds(5);

  private static final Logger LOG = LoggerFactory.getLogger(GcpCloudRunManagedCodeProvider.class);
  private static final int MAX_LABEL_VALUE_LENGTH = 63;

  private final Map<ScriptLanguage, ExecutorPackage> executors =
      new EnumMap<>(ScriptLanguage.class);
  private final Map<ScriptLanguage, String> uris = new ConcurrentHashMap<>();
  private final CloudRunFunctionsApi functions;
  private final ObjectMapper objectMapper;
  private final String location;
  private final String namePrefix;
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
      String namePrefix,
      String functionServiceAccount,
      int memoryMb,
      int maxInstanceCount,
      Duration executionTimeout,
      Duration provisioningTimeout,
      Duration pollInterval) {
    this.functions = functions;
    this.objectMapper = objectMapper;
    this.location = location;
    this.namePrefix = namePrefix;
    this.functionServiceAccount = functionServiceAccount;
    this.memoryMb = memoryMb;
    this.maxInstanceCount = maxInstanceCount;
    this.executionTimeout = executionTimeout;
    this.provisioningTimeout = provisioningTimeout;
    this.pollInterval = pollInterval;
    for (var language : ScriptLanguage.values()) {
      executors.put(language, ExecutorPackage.gcp(language));
    }
  }

  @Override
  public String name() {
    return NAME;
  }

  /** Creates each executor if absent and waits until it serves a URL. */
  @Override
  public void ensureExecutors() {
    for (var executor : executors.entrySet()) {
      uris.put(executor.getKey(), ensure(executor.getValue()));
    }
  }

  private String ensure(ExecutorPackage executor) {
    final var name = executor.functionName(namePrefix);
    final var functionName = functionName(name);
    final long start = System.nanoTime();
    final long deadline = start + provisioningTimeout.toNanos();
    // Shorter than the whole wait, so that a lasting denial is reported instead of waited on.
    final long denialDeadline = start + provisioningTimeout.toNanos() / 2;
    boolean createRequested = false;
    while (true) {
      final Function function;
      try {
        final var existing = functions.get(functionName);
        if (existing.isEmpty()) {
          if (!createRequested) {
            create(executor, name, functionName);
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
        LOG.debug("Creating executor '{}' will be retried: {}", name, e.getMessage());
        sleep(withJitter(pollInterval.multipliedBy(2)));
        continue;
      }
      if (function != null) {
        switch (function.getState()) {
          case ACTIVE -> {
            final var uri = function.getServiceConfig().getUri();
            if (!uri.isBlank()) {
              return uri;
            }
          }
          case FAILED ->
              throw new ProvisioningException(
                  "BUILD_FAILED",
                  "Cloud Run executor '%s' failed: %s".formatted(name, stateMessages(function)),
                  false);
          default -> {
            // DEPLOYING: wait.
          }
        }
      }
      if (System.nanoTime() > deadline) {
        throw new ProvisioningException(
            "PROVISIONING_TIMEOUT",
            "Cloud Run executor '%s' is still %s"
                .formatted(name, function == null ? "being created" : function.getState()),
            true);
      }
      sleep(withJitter(pollInterval));
    }
  }

  @Override
  public ExecutionResponse execute(ScriptLanguage language, ExecutionRequest request) {
    final var name = executors.get(language).functionName(namePrefix);
    final var uri = uris.get(language);
    if (uri == null) {
      return ExecutionResponse.failed(
          ExecutionResponse.DEPLOYMENT_MISSING,
          "The URL of Cloud Run executor '%s' is not known yet".formatted(name),
          false);
    }
    final byte[] payload;
    try {
      payload = objectMapper.writeValueAsBytes(request.payload());
    } catch (IOException e) {
      return ExecutionResponse.failed("INVALID_INPUT", "Cannot serialize the script input", false);
    }
    if (payload.length > MAX_REQUEST_BYTES) {
      return ExecutionResponse.failed(
          "RESOURCE_LIMIT",
          "The script and its input take %d bytes, more than the %d of a Cloud Run request"
              .formatted(payload.length, MAX_REQUEST_BYTES),
          false);
    }
    final CloudRunFunctionsApi.HttpResult result;
    try {
      // The platform's own timeout answers first; this one only guards against a lost response.
      result = functions.invoke(uri, payload, executionTimeout.plusSeconds(30));
    } catch (ProvisioningException e) {
      return ExecutionResponse.failed(e.code(), e.getMessage(), e.retryable());
    }
    return switch (result.statusCode()) {
      case 200 -> parse(result.body());
      case 404 -> {
        uris.remove(language, uri);
        yield ExecutionResponse.failed(
            ExecutionResponse.DEPLOYMENT_MISSING,
            "Cloud Run executor '%s' does not exist".formatted(name),
            false);
      }
      // A new cluster's invoker grant takes about a minute to apply.
      case 401, 403 ->
          ExecutionResponse.failed(
              "PLATFORM_UNAVAILABLE",
              "Not allowed to invoke '%s' (HTTP %d)".formatted(name, result.statusCode()),
              true);
      case 429 ->
          ExecutionResponse.failed(
              "PLATFORM_THROTTLED", "Cloud Run function '%s' is throttled".formatted(name), true);
      case 502, 503 ->
          ExecutionResponse.failed(
              "PLATFORM_UNAVAILABLE",
              "Cloud Run function '%s' is unavailable (HTTP %d)"
                  .formatted(name, result.statusCode()),
              true);
      // The wrapper answers script failures, timeouts and limits with 200, so anything else is the
      // platform's, such as the function timeout (504) or an exhausted function memory (500).
      default ->
          ExecutionResponse.failed(
              "PLATFORM_EXECUTION_FAILED",
              "Cloud Run function error (HTTP %d): %s"
                  .formatted(result.statusCode(), bounded(result.body())),
              false);
    };
  }

  /** Closes the API client; Spring calls it when the context stops. */
  public void close() {
    functions.close();
  }

  static int functionTimeoutSeconds(Duration executionTimeout) {
    return Math.toIntExact(
        Math.min(3_600, Math.max(1, executionTimeout.plus(FUNCTION_TIMEOUT_MARGIN).toSeconds())));
  }

  private void create(ExecutorPackage executor, String name, String functionName) {
    final var source = functions.upload(executor.zip());
    final int timeoutSeconds = functionTimeoutSeconds(executionTimeout);
    final var function =
        Function.newBuilder()
            .setName(functionName)
            .setDescription("Camunda managed-script executor, " + executor.language().language())
            .setEnvironment(Environment.GEN_2)
            .putAllLabels(
                Map.of(
                    OWNER_LABEL,
                    OWNER_VALUE,
                    EXECUTOR_LABEL,
                    executor.digest().substring(0, MAX_LABEL_VALUE_LENGTH)))
            .setBuildConfig(
                BuildConfig.newBuilder()
                    .setRuntime(executor.runtime())
                    .setEntryPoint(executor.entrypoint())
                    .setSource(Source.newBuilder().setStorageSource(source)))
            .setServiceConfig(
                ServiceConfig.newBuilder()
                    .setAvailableMemory(memoryMb + "Mi")
                    .setTimeoutSeconds(timeoutSeconds)
                    // Cloud Run gives the wrapper no remaining-time API.
                    .putEnvironmentVariables(
                        "EXECUTOR_FUNCTION_TIMEOUT_MS", Long.toString(timeoutSeconds * 1_000L))
                    .setServiceAccountEmail(functionServiceAccount)
                    .setMaxInstanceCount(maxInstanceCount)
                    .setMaxInstanceRequestConcurrency(1)
                    .setAllTrafficOnLatestRevision(true))
            .build();
    if (functions.create(name, function)) {
      LOG.info("Creating Cloud Run executor '{}'", name);
    } else {
      LOG.debug("Cloud Run executor '{}' already exists; adopting it", name);
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
}
