/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.managedscript.aws;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.managedscript.api.ErrorCode;
import io.camunda.connector.managedscript.api.ExecutionRequest;
import io.camunda.connector.managedscript.api.ExecutionResponse;
import io.camunda.connector.managedscript.api.ManagedCodeProvider;
import io.camunda.connector.managedscript.api.ProvisioningException;
import io.camunda.connector.managedscript.api.ScriptLanguage;
import java.io.IOException;
import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.cloudwatchlogs.CloudWatchLogsClient;
import software.amazon.awssdk.services.cloudwatchlogs.model.CreateLogGroupRequest;
import software.amazon.awssdk.services.cloudwatchlogs.model.PutRetentionPolicyRequest;
import software.amazon.awssdk.services.cloudwatchlogs.model.ResourceAlreadyExistsException;
import software.amazon.awssdk.services.lambda.LambdaClient;
import software.amazon.awssdk.services.lambda.model.CreateFunctionRequest;
import software.amazon.awssdk.services.lambda.model.FunctionCode;
import software.amazon.awssdk.services.lambda.model.FunctionConfiguration;
import software.amazon.awssdk.services.lambda.model.GetFunctionRequest;
import software.amazon.awssdk.services.lambda.model.InvalidParameterValueException;
import software.amazon.awssdk.services.lambda.model.InvocationType;
import software.amazon.awssdk.services.lambda.model.InvokeRequest;
import software.amazon.awssdk.services.lambda.model.InvokeResponse;
import software.amazon.awssdk.services.lambda.model.LastUpdateStatus;
import software.amazon.awssdk.services.lambda.model.PackageType;
import software.amazon.awssdk.services.lambda.model.RequestTooLargeException;
import software.amazon.awssdk.services.lambda.model.ResourceConflictException;
import software.amazon.awssdk.services.lambda.model.ResourceNotFoundException;
import software.amazon.awssdk.services.lambda.model.State;
import software.amazon.awssdk.services.lambda.model.TooManyRequestsException;
import software.amazon.awssdk.services.lambda.model.UpdateFunctionConfigurationRequest;

/**
 * Runs managed scripts through one AWS Lambda executor per language and cluster (ADR-0008): a zip
 * function running the wrapper, invoked synchronously with the script and its input.
 *
 * <ul>
 *   <li>The name of an executor carries the digest of its wrapper only. Runtime instances of a
 *       cluster converge on it: one creation is accepted, the others get a conflict and adopt the
 *       function.
 *   <li>Memory, function timeout and execution role are configuration: when the provider adopts an
 *       executor, it updates them in place if they differ, so a configuration change leaves no
 *       function behind.
 *   <li>The log group of an executor is created with its retention before the function, which
 *       Lambda would otherwise create with no expiry.
 *   <li>An invocation is never retried by the SDK, so that a script runs at most once per job
 *       attempt: job retries handle the rest.
 *   <li>Readiness is read with {@code GetFunction}, which has a quota of its own, rather than with
 *       {@code GetFunctionConfiguration}, which shares the control-plane calls of the account.
 *   <li>The provider never deletes an executor: Controller, or the operator, does.
 * </ul>
 */
public class AwsLambdaManagedCodeProvider implements ManagedCodeProvider, AutoCloseable {

  public static final String NAME = "aws";

  static final String OWNER_TAG = "camunda.io/managed-by";
  static final String OWNER_VALUE = "camunda-managed-script";
  static final String DIGEST_TAG = "camunda.io/executor-digest";

  /** The payload limit of a synchronous Lambda invocation. */
  static final int MAX_REQUEST_BYTES = 6 * 1024 * 1024;

  /**
   * Beyond the execution timeout, so that the wrapper, not Lambda, ends a script that runs late.
   */
  static final Duration FUNCTION_TIMEOUT_MARGIN = Duration.ofSeconds(5);

  static final int MAX_FUNCTION_TIMEOUT_SECONDS = 900;

  /** How long an executor may take to become active, or to apply new settings. */
  static final Duration ACTIVATION_TIMEOUT = Duration.ofMinutes(5);

  private static final Duration POLL_INTERVAL = Duration.ofSeconds(1);
  private static final int MAX_MESSAGE_LENGTH = 1024;

  private static final Logger LOG = LoggerFactory.getLogger(AwsLambdaManagedCodeProvider.class);

  private final Map<ScriptLanguage, LambdaExecutor> executors = new EnumMap<>(ScriptLanguage.class);
  private final LambdaClient lambda;
  private final LambdaClient invoker;
  private final CloudWatchLogsClient logs;
  private final ObjectMapper mapper;
  private final String prefix;
  private final String executionRoleArn;
  private final int memoryMb;
  private final int logRetentionDays;
  private final int functionTimeoutSeconds;
  private final Duration activationTimeout;
  private final Duration pollInterval;
  private final Sleeper sleeper;

  /**
   * @param lambda the control-plane client, with the SDK's retries
   * @param invoker the client of the invocations: no SDK retries, and a read timeout longer than
   *     the function timeout
   */
  public AwsLambdaManagedCodeProvider(
      LambdaClient lambda,
      LambdaClient invoker,
      CloudWatchLogsClient logs,
      ObjectMapper mapper,
      String prefix,
      AwsManagedCodeProperties properties,
      Duration executionTimeout) {
    this(
        lambda,
        invoker,
        logs,
        mapper,
        prefix,
        properties,
        executionTimeout,
        ACTIVATION_TIMEOUT,
        POLL_INTERVAL,
        Thread::sleep);
  }

  AwsLambdaManagedCodeProvider(
      LambdaClient lambda,
      LambdaClient invoker,
      CloudWatchLogsClient logs,
      ObjectMapper mapper,
      String prefix,
      AwsManagedCodeProperties properties,
      Duration executionTimeout,
      Duration activationTimeout,
      Duration pollInterval,
      Sleeper sleeper) {
    this.lambda = lambda;
    this.invoker = invoker;
    this.logs = logs;
    this.mapper = mapper;
    this.prefix = prefix;
    this.executionRoleArn = properties.executionRoleArn();
    this.memoryMb = properties.memoryMb();
    this.logRetentionDays = properties.logRetentionDays();
    this.functionTimeoutSeconds = functionTimeoutSeconds(executionTimeout);
    this.activationTimeout = activationTimeout;
    this.pollInterval = pollInterval;
    this.sleeper = sleeper;
    for (var language : ScriptLanguage.values()) {
      executors.put(language, LambdaExecutor.of(language));
    }
    LOG.info(
        "The aws managed-code provider runs scripts through the AWS Lambda executors named"
            + " '{}py-*' and '{}js-*'",
        prefix,
        prefix);
  }

  @Override
  public String name() {
    return NAME;
  }

  /**
   * Creates the executor of the language, after its log group, unless it exists. Then waits until
   * it is active, and updates its settings in place if they differ from the configuration.
   *
   * @throws ProvisioningException with a code from the failure of the AWS call; {@link
   *     ProvisioningException#permissionDenied()} for a denied identity, or a role that Lambda
   *     cannot assume yet
   */
  @Override
  public void ensureExecutors(ScriptLanguage language) {
    final var executor = executors.get(language);
    final var name = executor.functionName(prefix);
    try {
      if (read(name) == null) {
        create(executor, name);
      }
      converge(name);
    } catch (AwsServiceException e) {
      throw failure(name, e);
    } catch (SdkClientException e) {
      throw new ProvisioningException(
          ErrorCode.PLATFORM_UNAVAILABLE,
          bounded(
              "Cannot reach AWS to make Lambda executor '%s' ready: %s"
                  .formatted(name, message(e))),
          e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new ProvisioningException(
          ErrorCode.PLATFORM_INTERRUPTED,
          "Interrupted while making Lambda executor '%s' ready".formatted(name),
          e);
    }
  }

  /**
   * Invokes the executor of the language synchronously, without SDK retries.
   *
   * @throws ProvisioningException only for a denied invocation, marked {@link
   *     ProvisioningException#permissionDenied()}; every other failure is a {@code FAILED} response
   */
  @Override
  public ExecutionResponse execute(ScriptLanguage language, ExecutionRequest request) {
    final var name = executors.get(language).functionName(prefix);
    final byte[] payload;
    try {
      payload = mapper.writeValueAsBytes(request.payload());
    } catch (JsonProcessingException e) {
      return ExecutionResponse.failed(
          ErrorCode.INVALID_INPUT, "The input of the script cannot be serialized");
    }
    if (payload.length > MAX_REQUEST_BYTES) {
      return ExecutionResponse.failed(
          ErrorCode.RESOURCE_LIMIT,
          "The script and its input take %d bytes, more than the %d of a Lambda invocation"
              .formatted(payload.length, MAX_REQUEST_BYTES));
    }
    final InvokeResponse response;
    try {
      response =
          invoker.invoke(
              InvokeRequest.builder()
                  .functionName(name)
                  .invocationType(InvocationType.REQUEST_RESPONSE)
                  .payload(SdkBytes.fromByteArray(payload))
                  .build());
    } catch (ResourceNotFoundException e) {
      return ExecutionResponse.failed(
          ErrorCode.DEPLOYMENT_MISSING, "Lambda executor '%s' does not exist".formatted(name));
    } catch (RequestTooLargeException e) {
      return ExecutionResponse.failed(
          ErrorCode.RESOURCE_LIMIT,
          "The script and its input are too large for Lambda executor '%s'".formatted(name));
    } catch (AwsServiceException e) {
      if (denied(e)) {
        // Thrown, not returned: the connector retries it while the grants of a new cluster
        // propagate.
        throw ProvisioningException.permissionDenied(
            bounded("AWS denied the call to Lambda executor '%s': %s".formatted(name, message(e))),
            e);
      }
      return ExecutionResponse.failed(
          invocationCode(e),
          bounded("Lambda executor '%s' was not called: %s".formatted(name, message(e))));
    } catch (SdkClientException e) {
      if (notSent(e)) {
        return ExecutionResponse.failed(
            ErrorCode.PLATFORM_UNAVAILABLE,
            bounded("Lambda executor '%s' was not called: %s".formatted(name, message(e))));
      }
      // The request may have reached the function: calling it again could run the script twice.
      return ExecutionResponse.failed(
          ErrorCode.PLATFORM_EXECUTION_FAILED,
          bounded(
              "The call to Lambda executor '%s' failed after it was sent: %s"
                  .formatted(name, message(e))));
    }
    final var body = response.payload() == null ? null : response.payload().asUtf8String();
    if (response.functionError() != null) {
      // The wrapper returns script failures, timeouts and limits as a response: an error here is
      // the platform's, such as the function timeout or an exhausted function memory.
      return ExecutionResponse.failed(
          ErrorCode.PLATFORM_EXECUTION_FAILED,
          bounded(
              "Lambda executor '%s' failed (%s): %s"
                  .formatted(name, response.functionError(), body)));
    }
    try {
      final var result = body == null ? null : mapper.readValue(body, ExecutionResponse.class);
      if (result != null && result.wellFormed()) {
        return result;
      }
    } catch (IOException e) {
      // Not a response: reported below.
    }
    // The wrapper writes the response, not the script: a broken one is the platform's failure.
    return ExecutionResponse.failed(
        ErrorCode.PLATFORM_EXECUTION_FAILED,
        "Lambda executor '%s' returned no valid contract response".formatted(name));
  }

  /** Whether the connection failed before the request was sent, so that nothing of it ran. */
  static boolean notSent(Throwable failure) {
    for (var cause = failure; cause != null; cause = cause.getCause()) {
      if (cause instanceof ConnectException
          || cause instanceof UnknownHostException
          || cause instanceof NoRouteToHostException
          // The connect timeout of the Apache client, and its timeout waiting for a pooled
          // connection, without depending on their classes.
          || cause.getClass().getSimpleName().equals("ConnectTimeoutException")
          || cause.getClass().getSimpleName().equals("ConnectionRequestTimeoutException")) {
        return true;
      }
    }
    return false;
  }

  @Override
  public void close() {
    lambda.close();
    invoker.close();
    logs.close();
  }

  /**
   * The execution timeout and the margin of the wrapper, in whole seconds.
   *
   * @throws IllegalArgumentException above the 900 seconds of a Lambda function
   */
  static int functionTimeoutSeconds(Duration executionTimeout) {
    final long millis = executionTimeout.plus(FUNCTION_TIMEOUT_MARGIN).toMillis();
    final long seconds = (millis + 999) / 1000;
    if (seconds > MAX_FUNCTION_TIMEOUT_SECONDS) {
      throw new IllegalArgumentException(
          "camunda.connector.managed-code.execution-timeout must be at most %d seconds on AWS"
                  .formatted(MAX_FUNCTION_TIMEOUT_SECONDS - FUNCTION_TIMEOUT_MARGIN.toSeconds())
              + " Lambda, whose functions run for at most %d seconds"
                  .formatted(MAX_FUNCTION_TIMEOUT_SECONDS));
    }
    return Math.toIntExact(seconds);
  }

  static String logGroupName(String functionName) {
    return "/aws/lambda/" + functionName;
  }

  /** The configuration of the function, or {@code null} when it does not exist. */
  private FunctionConfiguration read(String name) {
    try {
      return lambda
          .getFunction(GetFunctionRequest.builder().functionName(name).build())
          .configuration();
    } catch (ResourceNotFoundException e) {
      return null;
    }
  }

  private void create(LambdaExecutor executor, String name) {
    ensureLogGroup(name);
    try {
      lambda.createFunction(
          CreateFunctionRequest.builder()
              .functionName(name)
              .description("Camunda managed-script executor of " + executor.language().language())
              .role(executionRoleArn)
              .packageType(PackageType.ZIP)
              .runtime(executor.runtime())
              .handler(executor.handler())
              .timeout(functionTimeoutSeconds)
              .memorySize(memoryMb)
              .tags(Map.of(OWNER_TAG, OWNER_VALUE, DIGEST_TAG, executor.digest()))
              .code(FunctionCode.builder().zipFile(SdkBytes.fromByteArray(executor.zip())).build())
              .build());
      LOG.info("Created Lambda executor '{}'", name);
    } catch (ResourceConflictException e) {
      LOG.info("Lambda executor '{}' was created by another runtime instance: adopting it", name);
    }
  }

  /** Idempotent, so that concurrent instances converge on one log group and its retention. */
  private void ensureLogGroup(String functionName) {
    final var logGroup = logGroupName(functionName);
    try {
      logs.createLogGroup(CreateLogGroupRequest.builder().logGroupName(logGroup).build());
    } catch (ResourceAlreadyExistsException e) {
      LOG.debug("Log group '{}' already exists", logGroup);
    }
    logs.putRetentionPolicy(
        PutRetentionPolicyRequest.builder()
            .logGroupName(logGroup)
            .retentionInDays(logRetentionDays)
            .build());
  }

  /**
   * Waits until the function is active and no update is in progress, then updates its settings if
   * they differ. Another instance may update it at the same time: the conflict is waited out, and
   * the settings compared again.
   */
  private void converge(String name) throws InterruptedException {
    final long deadline = System.nanoTime() + activationTimeout.toNanos();
    boolean updated = false;
    while (true) {
      final FunctionConfiguration configuration;
      try {
        configuration = read(name);
      } catch (AwsServiceException e) {
        if (!throttled(e)) {
          throw e;
        }
        pause(deadline, retryAfter(e), "Reading Lambda executor '%s' is throttled".formatted(name));
        continue;
      }
      if (configuration == null) {
        throw new ProvisioningException(
            ErrorCode.PLATFORM_UNAVAILABLE,
            "Lambda executor '%s' was deleted while it was made ready".formatted(name));
      }
      if (configuration.state() == State.FAILED) {
        throw new ProvisioningException(
            ErrorCode.PLATFORM_REJECTED,
            bounded(
                "Lambda executor '%s' failed: %s".formatted(name, configuration.stateReason())));
      }
      if (!settled(configuration)) {
        pause(
            deadline,
            withJitter(pollInterval),
            "Lambda executor '%s' is still %s, last update %s"
                .formatted(name, configuration.state(), configuration.lastUpdateStatus()));
        continue;
      }
      if (matches(configuration)) {
        return;
      }
      if (updated && configuration.lastUpdateStatus() == LastUpdateStatus.FAILED) {
        throw new ProvisioningException(
            ErrorCode.PLATFORM_REJECTED,
            bounded(
                "Lambda executor '%s' rejected its new settings: %s"
                    .formatted(name, configuration.lastUpdateStatusReason())));
      }
      try {
        lambda.updateFunctionConfiguration(
            UpdateFunctionConfigurationRequest.builder()
                .functionName(name)
                .memorySize(memoryMb)
                .timeout(functionTimeoutSeconds)
                .role(executionRoleArn)
                .build());
        updated = true;
        LOG.info(
            "Updated the settings of Lambda executor '{}': memory {} MB, timeout {} s, role {}",
            name,
            memoryMb,
            functionTimeoutSeconds,
            executionRoleArn);
      } catch (ResourceConflictException e) {
        LOG.debug("Lambda executor '{}' is being updated by another runtime instance", name);
      }
      pause(
          deadline,
          withJitter(pollInterval),
          "Lambda executor '%s' did not apply its settings".formatted(name));
    }
  }

  /** Invocable: active, or inactive, which the next invocation activates; no update running. */
  private static boolean settled(FunctionConfiguration configuration) {
    return (configuration.state() == State.ACTIVE || configuration.state() == State.INACTIVE)
        && configuration.lastUpdateStatus() != LastUpdateStatus.IN_PROGRESS;
  }

  private boolean matches(FunctionConfiguration configuration) {
    return Objects.equals(configuration.memorySize(), memoryMb)
        && Objects.equals(configuration.timeout(), functionTimeoutSeconds)
        && executionRoleArn.equals(configuration.role());
  }

  /**
   * Sleeps, or gives up when the wait would end after the deadline.
   *
   * @param pending what is still awaited, for the failure
   */
  private void pause(long deadline, Duration duration, String pending) throws InterruptedException {
    if (System.nanoTime() + duration.toNanos() > deadline) {
      throw new ProvisioningException(
          ErrorCode.PLATFORM_UNAVAILABLE,
          "%s after %d seconds".formatted(pending, activationTimeout.toSeconds()));
    }
    sleeper.sleep(duration);
  }

  /** The {@code Retry-After} of a throttled call, otherwise twice the poll interval. */
  private Duration retryAfter(AwsServiceException e) {
    if (e instanceof TooManyRequestsException throttled && throttled.retryAfterSeconds() != null) {
      try {
        return withJitter(Duration.ofSeconds(Long.parseLong(throttled.retryAfterSeconds())));
      } catch (NumberFormatException ignored) {
        // Falls back to the poll interval.
      }
    }
    return withJitter(pollInterval.multipliedBy(2));
  }

  /** Up to half more, so that instances started together do not poll in step. */
  private static Duration withJitter(Duration duration) {
    final long millis = Math.max(1, duration.toMillis());
    return Duration.ofMillis(millis + ThreadLocalRandom.current().nextLong(millis / 2 + 1));
  }

  /** The error code of a control-plane call that failed, as ADR-0008 maps it. */
  private static ProvisioningException failure(String name, AwsServiceException e) {
    final var message =
        bounded(
            "AWS rejected a call to make Lambda executor '%s' ready: %s"
                .formatted(name, message(e)));
    if (denied(e)) {
      return ProvisioningException.permissionDenied(message, e);
    }
    if (throttled(e)) {
      return new ProvisioningException(ErrorCode.PLATFORM_THROTTLED, message, e);
    }
    return new ProvisioningException(
        e.statusCode() >= 500 || conflicting(e)
            ? ErrorCode.PLATFORM_UNAVAILABLE
            : ErrorCode.PLATFORM_REJECTED,
        message,
        e);
  }

  /**
   * Another call on the same resource is in progress: another instance setting the retention of the
   * log group, or a function that is being updated or reactivated. It passes.
   */
  private static boolean conflicting(AwsServiceException e) {
    final var details = e.awsErrorDetails();
    final var code = details == null ? null : details.errorCode();
    return e.statusCode() == 409
        || "OperationAbortedException".equals(code)
        || "ResourceConflictException".equals(code);
  }

  /** Denied to the identity of the runtime, or the execution role is not assumable yet. */
  private static boolean denied(AwsServiceException e) {
    if (e.statusCode() == 403) {
      return true;
    }
    final var details = e.awsErrorDetails();
    if (details != null
        && details.errorCode() != null
        && details.errorCode().startsWith("AccessDenied")) {
      return true;
    }
    // A role created with the cluster may take a few seconds to be assumable by Lambda.
    return e instanceof InvalidParameterValueException
        && e.getMessage() != null
        && e.getMessage().contains("cannot be assumed");
  }

  private static ErrorCode invocationCode(AwsServiceException e) {
    if (throttled(e)) {
      return ErrorCode.PLATFORM_THROTTLED;
    }
    return e.statusCode() >= 500 || conflicting(e)
        ? ErrorCode.PLATFORM_UNAVAILABLE
        : ErrorCode.PLATFORM_REJECTED;
  }

  private static boolean throttled(AwsServiceException e) {
    return e instanceof TooManyRequestsException || e.isThrottlingException();
  }

  private static String message(Exception e) {
    return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
  }

  private static String bounded(String message) {
    return message.length() <= MAX_MESSAGE_LENGTH
        ? message
        : message.substring(0, MAX_MESSAGE_LENGTH);
  }

  /** {@link Thread#sleep}, replaced in tests. */
  @FunctionalInterface
  interface Sleeper {
    void sleep(Duration duration) throws InterruptedException;
  }
}
