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
import java.io.IOException;
import java.time.Duration;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.cloudwatchlogs.CloudWatchLogsClient;
import software.amazon.awssdk.services.cloudwatchlogs.model.ResourceAlreadyExistsException;
import software.amazon.awssdk.services.lambda.LambdaClient;
import software.amazon.awssdk.services.lambda.model.CreateFunctionRequest;
import software.amazon.awssdk.services.lambda.model.FunctionCode;
import software.amazon.awssdk.services.lambda.model.FunctionConfiguration;
import software.amazon.awssdk.services.lambda.model.InvocationType;
import software.amazon.awssdk.services.lambda.model.LambdaException;
import software.amazon.awssdk.services.lambda.model.LastUpdateStatus;
import software.amazon.awssdk.services.lambda.model.PackageType;
import software.amazon.awssdk.services.lambda.model.ResourceConflictException;
import software.amazon.awssdk.services.lambda.model.ResourceNotFoundException;
import software.amazon.awssdk.services.lambda.model.State;
import software.amazon.awssdk.services.lambda.model.TooManyRequestsException;

/**
 * Runs scripts through one AWS Lambda executor per language and cluster, created when the runtime
 * starts. A script is sent as data with its input; it creates no function.
 *
 * <ul>
 *   <li>An executor's name is the prefix of the cluster, its language and the digest of its
 *       wrapper, so concurrent runtime instances converge: one create is accepted, the others get a
 *       conflict and adopt the function. A new wrapper is a new function, never an update.
 *   <li>Functions are zip packages only: Lambda pulls a container image with its own identity, so
 *       an image would not be checked against the calling cluster.
 *   <li>The execution role is attached to the executor, which runs customer code, and must grant
 *       nothing beyond writing to the log group of its function, which this provider creates with a
 *       retention period before the function. Lambda would otherwise create it with no expiry.
 *   <li>Status is read with {@code GetFunction}, which has its own quota, rather than {@code
 *       GetFunctionConfiguration}, which shares the 15 calls per second of the control plane with
 *       every other runtime of the account.
 *   <li>Old executors are not deleted after an upgrade: older runtime instances may still call
 *       them.
 * </ul>
 */
public final class AwsLambdaManagedCodeProvider implements ManagedCodeProvider {

  public static final String NAME = "aws";

  static final String OWNER_TAG = "camunda.io/managed-by";
  static final String OWNER_VALUE = "camunda-managed-script";
  static final String EXECUTOR_TAG = "camunda.io/executor-digest";

  /** The payload limit of a synchronous invocation. */
  static final int MAX_REQUEST_BYTES = 6 * 1024 * 1024;

  /**
   * Beyond the execution timeout, so that the wrapper, not Lambda, ends a script that runs late.
   */
  static final Duration FUNCTION_TIMEOUT_MARGIN = Duration.ofSeconds(5);

  private static final Logger LOG = LoggerFactory.getLogger(AwsLambdaManagedCodeProvider.class);

  private final Map<ScriptLanguage, ExecutorPackage> executors =
      new EnumMap<>(ScriptLanguage.class);
  private final LambdaClient lambda;
  private final CloudWatchLogsClient logs;
  private final ObjectMapper objectMapper;
  private final String namePrefix;
  private final String executionRoleArn;
  private final int memoryMb;
  private final int logRetentionDays;
  private final Duration executionTimeout;
  private final Duration provisioningTimeout;
  private final Duration pollInterval;

  public AwsLambdaManagedCodeProvider(
      LambdaClient lambda,
      CloudWatchLogsClient logs,
      ObjectMapper objectMapper,
      String namePrefix,
      String executionRoleArn,
      int memoryMb,
      int logRetentionDays,
      Duration executionTimeout,
      Duration provisioningTimeout,
      Duration pollInterval) {
    this.lambda = lambda;
    this.logs = logs;
    this.objectMapper = objectMapper;
    this.namePrefix = namePrefix;
    this.executionRoleArn = executionRoleArn;
    this.memoryMb = memoryMb;
    this.logRetentionDays = logRetentionDays;
    this.executionTimeout = executionTimeout;
    this.provisioningTimeout = provisioningTimeout;
    this.pollInterval = pollInterval;
    for (var language : ScriptLanguage.values()) {
      executors.put(language, ExecutorPackage.aws(language));
    }
  }

  @Override
  public String name() {
    return NAME;
  }

  /** Creates each executor if absent and waits until it is active; reads only when they exist. */
  @Override
  public void ensureExecutors() {
    for (var executor : executors.values()) {
      final var name = executor.functionName(namePrefix);
      if (!exists(name)) {
        create(executor, name);
      }
      awaitActive(name);
    }
  }

  @Override
  public ExecutionResponse execute(ScriptLanguage language, ExecutionRequest request) {
    final var name = executors.get(language).functionName(namePrefix);
    final byte[] payload;
    try {
      payload = objectMapper.writeValueAsBytes(request.payload());
    } catch (IOException e) {
      return ExecutionResponse.failed("INVALID_INPUT", "Cannot serialize the script input", false);
    }
    if (payload.length > MAX_REQUEST_BYTES) {
      return ExecutionResponse.failed(
          "RESOURCE_LIMIT",
          "The script and its input take %d bytes, more than the %d of a Lambda invocation"
              .formatted(payload.length, MAX_REQUEST_BYTES),
          false);
    }
    final software.amazon.awssdk.services.lambda.model.InvokeResponse response;
    try {
      response =
          lambda.invoke(
              r ->
                  r.functionName(name)
                      .invocationType(InvocationType.REQUEST_RESPONSE)
                      .payload(SdkBytes.fromByteArray(payload)));
    } catch (ResourceNotFoundException e) {
      return ExecutionResponse.failed(
          ExecutionResponse.DEPLOYMENT_MISSING,
          "Lambda executor '%s' does not exist".formatted(name),
          false);
    } catch (TooManyRequestsException e) {
      return ExecutionResponse.failed("PLATFORM_THROTTLED", safeMessage(e), true);
    } catch (LambdaException e) {
      return ExecutionResponse.failed("PLATFORM_UNAVAILABLE", safeMessage(e), true);
    }
    final var body = response.payload() == null ? "" : response.payload().asUtf8String();
    if (response.functionError() != null) {
      // The wrapper returns script failures, timeouts and limits as a response; an error here is
      // the platform's, such as the function timeout or an exhausted function memory.
      return ExecutionResponse.failed(
          "PLATFORM_EXECUTION_FAILED",
          "Lambda function error (%s): %s".formatted(response.functionError(), bounded(body)),
          false);
    }
    try {
      return objectMapper.readValue(body, ExecutionResponse.class);
    } catch (IOException e) {
      return ExecutionResponse.failed(
          "INVALID_RESULT", "The executor returned no valid contract response", false);
    }
  }

  static int functionTimeoutSeconds(Duration executionTimeout) {
    return Math.toIntExact(
        Math.min(900, Math.max(1, executionTimeout.plus(FUNCTION_TIMEOUT_MARGIN).toSeconds())));
  }

  private boolean exists(String name) {
    try {
      call(() -> lambda.getFunction(r -> r.functionName(name)));
      return true;
    } catch (ResourceNotFoundException e) {
      return false;
    }
  }

  private void create(ExecutorPackage executor, String name) {
    ensureLogGroup(name);
    try {
      call(
          () ->
              lambda.createFunction(
                  CreateFunctionRequest.builder()
                      .functionName(name)
                      .description(
                          "Camunda managed-script executor, " + executor.language().language())
                      .role(executionRoleArn)
                      .packageType(PackageType.ZIP)
                      .runtime(executor.runtime())
                      .handler(executor.entrypoint())
                      .timeout(functionTimeoutSeconds(executionTimeout))
                      .memorySize(memoryMb)
                      .tags(Map.of(OWNER_TAG, OWNER_VALUE, EXECUTOR_TAG, executor.digest()))
                      .code(
                          FunctionCode.builder()
                              .zipFile(SdkBytes.fromByteArray(executor.zip()))
                              .build())
                      .build()));
      LOG.info("Created Lambda executor '{}'", name);
    } catch (ResourceConflictException e) {
      LOG.debug("Lambda executor '{}' already exists; adopting it", name);
    }
  }

  static String logGroupName(String functionName) {
    return "/aws/lambda/" + functionName;
  }

  /** Idempotent, so that concurrent instances and re-provisioning converge on one log group. */
  private void ensureLogGroup(String functionName) {
    final var logGroup = logGroupName(functionName);
    try {
      call(() -> logs.createLogGroup(r -> r.logGroupName(logGroup)));
    } catch (ResourceAlreadyExistsException e) {
      LOG.debug("Log group '{}' already exists", logGroup);
    }
    call(
        () ->
            logs.putRetentionPolicy(
                r -> r.logGroupName(logGroup).retentionInDays(logRetentionDays)));
  }

  private FunctionConfiguration awaitActive(String name) {
    final long deadline = System.nanoTime() + provisioningTimeout.toNanos();
    while (true) {
      final FunctionConfiguration configuration;
      try {
        configuration = call(() -> lambda.getFunction(r -> r.functionName(name))).configuration();
      } catch (ProvisioningException e) {
        if (!e.retryable() || System.nanoTime() > deadline) {
          throw e;
        }
        // Throttled or unavailable: keep waiting rather than fail the job.
        sleep(backoff(e.getCause()));
        continue;
      }
      if (configuration.state() == State.FAILED
          || configuration.lastUpdateStatus() == LastUpdateStatus.FAILED) {
        throw new ProvisioningException(
            "BUILD_FAILED",
            "Lambda function '%s' failed: %s"
                .formatted(
                    name,
                    configuration.stateReason() != null
                        ? configuration.stateReason()
                        : configuration.lastUpdateStatusReason()),
            false);
      }
      if (configuration.state() == State.ACTIVE
          && (configuration.lastUpdateStatus() == null
              || configuration.lastUpdateStatus() == LastUpdateStatus.SUCCESSFUL)) {
        return configuration;
      }
      if (System.nanoTime() > deadline) {
        throw new ProvisioningException(
            "PROVISIONING_TIMEOUT",
            "Lambda function '%s' is still %s/%s"
                .formatted(name, configuration.state(), configuration.lastUpdateStatus()),
            true);
      }
      sleep(withJitter(pollInterval));
    }
  }

  /** Honours the {@code Retry-After} of a throttling response, otherwise twice the interval. */
  private Duration backoff(Throwable cause) {
    if (cause instanceof TooManyRequestsException throttled
        && throttled.retryAfterSeconds() != null) {
      try {
        return withJitter(Duration.ofSeconds(Long.parseLong(throttled.retryAfterSeconds())));
      } catch (NumberFormatException ignored) {
        // Fall back to the interval.
      }
    }
    return withJitter(pollInterval.multipliedBy(2));
  }

  /** Up to 50% more, so that runtimes started together do not poll in step. */
  private static Duration withJitter(Duration duration) {
    final long millis = Math.max(1, duration.toMillis());
    return Duration.ofMillis(millis + ThreadLocalRandom.current().nextLong(millis / 2 + 1));
  }

  private static <T> T call(java.util.function.Supplier<T> operation) {
    try {
      return operation.get();
    } catch (ResourceConflictException
        | ResourceNotFoundException
        | ResourceAlreadyExistsException
        | software.amazon.awssdk.services.cloudwatchlogs.model.ResourceNotFoundException e) {
      throw e;
    } catch (AwsServiceException e) {
      if (e instanceof TooManyRequestsException || e.isThrottlingException()) {
        throw new ProvisioningException("PLATFORM_THROTTLED", safeMessage(e), true, e);
      }
      final var status = e.statusCode();
      // 4xx other than throttling means the request or the permissions are wrong.
      throw new ProvisioningException(
          status >= 400 && status < 500 ? "PROVIDER_REJECTED" : "PLATFORM_UNAVAILABLE",
          safeMessage(e),
          status >= 500,
          e);
    }
  }

  private static String safeMessage(Exception e) {
    final var message = e.getMessage();
    return message == null ? e.getClass().getSimpleName() : bounded(message);
  }

  private static String bounded(String value) {
    return value.length() <= 512 ? value : value.substring(0, 512);
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
