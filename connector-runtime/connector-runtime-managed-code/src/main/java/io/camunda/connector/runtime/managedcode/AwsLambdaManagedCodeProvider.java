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
import java.util.Map;
import java.util.concurrent.CompletableFuture;
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
import software.amazon.awssdk.services.lambda.model.ResourceConflictException;
import software.amazon.awssdk.services.lambda.model.ResourceNotFoundException;
import software.amazon.awssdk.services.lambda.model.State;
import software.amazon.awssdk.services.lambda.model.TooManyRequestsException;

/**
 * Deploys each artifact as its own AWS Lambda function and invokes a published, immutable version
 * of it.
 *
 * <ul>
 *   <li>The function name is the deterministic deployment name of the artifact, so concurrent
 *       runtime instances converge: one create is accepted, the others get a conflict and adopt the
 *       function.
 *   <li>The {@link AwsLambdaScriptPackager} provides the generated handler and the user script,
 *       zipped deterministically, so that the code hash is stable and publishing the version
 *       returns the same number to every instance.
 *   <li>The execution role is attached to customer code and must grant nothing beyond writing to
 *       the log group of its function, which this provider creates with a retention period before
 *       the function and deletes with it. Lambda would otherwise create it with no expiry, and
 *       deleting a function leaves its log group behind.
 *   <li>Status is read with {@code GetFunction}, which has its own quota, rather than {@code
 *       GetFunctionConfiguration}, which shares the 15 calls per second of the control plane with
 *       every other runtime of the account.
 * </ul>
 */
public final class AwsLambdaManagedCodeProvider implements ManagedCodeProvider {

  public static final String NAME = "aws";

  static final String OWNER_TAG = "camunda.io/managed-by";
  static final String OWNER_VALUE = "camunda-managed-script";
  static final String DIGEST_TAG = "camunda.io/artifact-digest";

  private static final Logger LOG = LoggerFactory.getLogger(AwsLambdaManagedCodeProvider.class);
  private static final int MAX_PUBLISH_ATTEMPTS = 20;

  private final AwsLambdaScriptPackager packager = new AwsLambdaScriptPackager();
  private final LambdaClient lambda;
  private final CloudWatchLogsClient logs;
  private final ObjectMapper objectMapper;
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
      String executionRoleArn,
      int memoryMb,
      int logRetentionDays,
      Duration executionTimeout,
      Duration provisioningTimeout,
      Duration pollInterval) {
    this.lambda = lambda;
    this.logs = logs;
    this.objectMapper = objectMapper;
    this.executionRoleArn = executionRoleArn;
    this.memoryMb = memoryMb;
    this.logRetentionDays = logRetentionDays;
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
    final var scriptPackage = packager.packageFor(artifact);
    final var name = artifact.deploymentName();
    final var code = SdkBytes.fromByteArray(scriptPackage.zip());
    ensureLogGroup(name);
    try {
      final var created =
          call(
              () ->
                  lambda.createFunction(
                      CreateFunctionRequest.builder()
                          .functionName(name)
                          .description("Camunda managed script " + artifact.resourceName())
                          .role(executionRoleArn)
                          .runtime(scriptPackage.runtime())
                          .handler(scriptPackage.entrypoint())
                          .timeout(Math.toIntExact(Math.max(1, executionTimeout.toSeconds())))
                          .memorySize(memoryMb)
                          .publish(true)
                          .tags(Map.of(OWNER_TAG, OWNER_VALUE, DIGEST_TAG, artifact.digest()))
                          .code(FunctionCode.builder().zipFile(code).build())
                          .build()));
      LOG.debug("Created Lambda function '{}' version {}", name, created.version());
    } catch (ResourceConflictException e) {
      LOG.debug("Lambda function '{}' already exists; adopting it", name);
    }
    final var configuration = awaitActive(name);
    final var version = publish(name, configuration.codeSha256());
    final var deployment = new ProviderDeployment(NAME, name + ":" + version);
    return new ProvisioningHandle(name, CompletableFuture.completedFuture(deployment));
  }

  @Override
  public ExecutionResponse invoke(ProviderDeployment deployment, ExecutionRequest request) {
    final var qualified = QualifiedName.parse(deployment.deploymentId());
    final byte[] payload;
    try {
      payload = objectMapper.writeValueAsBytes(request.contractPayload());
    } catch (IOException e) {
      return ExecutionResponse.failed("INVALID_INPUT", "Cannot serialize the script input", false);
    }
    final software.amazon.awssdk.services.lambda.model.InvokeResponse response;
    try {
      response =
          lambda.invoke(
              r ->
                  r.functionName(qualified.name())
                      .qualifier(qualified.version())
                      .invocationType(InvocationType.REQUEST_RESPONSE)
                      .payload(SdkBytes.fromByteArray(payload)));
    } catch (ResourceNotFoundException e) {
      return ExecutionResponse.failed(
          ExecutionResponse.DEPLOYMENT_MISSING,
          "Lambda function '%s' does not exist".formatted(deployment.deploymentId()),
          false);
    } catch (TooManyRequestsException e) {
      return ExecutionResponse.failed("PLATFORM_THROTTLED", safeMessage(e), true);
    } catch (LambdaException e) {
      return ExecutionResponse.failed("PLATFORM_UNAVAILABLE", safeMessage(e), true);
    }
    final var body = response.payload() == null ? "" : response.payload().asUtf8String();
    if (response.functionError() != null) {
      // The handler returns script failures as a response; an error here is the platform's, such
      // as a timeout or an exhausted memory limit.
      return ExecutionResponse.failed(
          "PLATFORM_EXECUTION_FAILED",
          "Lambda function error (%s): %s".formatted(response.functionError(), bounded(body)),
          false);
    }
    try {
      return objectMapper.readValue(body, ExecutionResponse.class);
    } catch (IOException e) {
      return ExecutionResponse.failed(
          "INVALID_RESULT", "The function returned no valid contract response", false);
    }
  }

  @Override
  public void delete(ProviderDeployment deployment) {
    final var name = QualifiedName.parse(deployment.deploymentId()).name();
    try {
      call(() -> lambda.deleteFunction(r -> r.functionName(name)));
    } catch (ResourceNotFoundException e) {
      // Already deleted.
    }
    // After the function, so that it never runs without its log group.
    try {
      call(() -> logs.deleteLogGroup(r -> r.logGroupName(logGroupName(name))));
    } catch (software.amazon.awssdk.services.cloudwatchlogs.model.ResourceNotFoundException e) {
      // Already deleted, or the function never logged.
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

  /**
   * Publishes a version guarded by the code hash. Lambda returns the existing version when nothing
   * changed, so every runtime instance gets the same number; a publication in progress elsewhere is
   * retried.
   */
  private String publish(String name, String codeSha256) {
    for (int attempt = 1; ; attempt++) {
      try {
        return call(() -> lambda.publishVersion(r -> r.functionName(name).codeSha256(codeSha256)))
            .version();
      } catch (ResourceConflictException e) {
        if (attempt >= MAX_PUBLISH_ATTEMPTS) {
          throw new ProvisioningException(
              "PLATFORM_CONFLICT",
              "Could not publish a version of Lambda function '%s'".formatted(name),
              true,
              e);
        }
        sleep(Duration.ofMillis(200 + (long) (Math.random() * 300)));
      }
    }
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

  record QualifiedName(String name, String version) {
    static QualifiedName parse(String deploymentId) {
      final int separator = deploymentId.lastIndexOf(':');
      if (separator < 0) {
        return new QualifiedName(deploymentId, null);
      }
      return new QualifiedName(
          deploymentId.substring(0, separator), deploymentId.substring(separator + 1));
    }
  }
}
