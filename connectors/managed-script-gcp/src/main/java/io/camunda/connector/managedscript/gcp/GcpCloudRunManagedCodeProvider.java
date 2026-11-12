/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.managedscript.gcp;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.gax.rpc.ApiException;
import com.google.api.gax.rpc.StatusCode;
import com.google.auth.oauth2.IdTokenCredentials;
import com.google.auth.oauth2.IdTokenProvider;
import com.google.cloud.functions.v2.BuildConfig;
import com.google.cloud.functions.v2.CreateFunctionRequest;
import com.google.cloud.functions.v2.Environment;
import com.google.cloud.functions.v2.Function;
import com.google.cloud.functions.v2.FunctionServiceClient;
import com.google.cloud.functions.v2.GenerateUploadUrlRequest;
import com.google.cloud.functions.v2.ServiceConfig;
import com.google.cloud.functions.v2.Source;
import com.google.cloud.functions.v2.StateMessage;
import com.google.cloud.functions.v2.UpdateFunctionRequest;
import com.google.longrunning.Operation;
import com.google.protobuf.FieldMask;
import com.google.rpc.Code;
import com.google.rpc.Status;
import io.camunda.connector.managedscript.api.ErrorCode;
import io.camunda.connector.managedscript.api.ExecutionRequest;
import io.camunda.connector.managedscript.api.ExecutionResponse;
import io.camunda.connector.managedscript.api.ExecutorReference;
import io.camunda.connector.managedscript.api.ManagedCodeProvider;
import io.camunda.connector.managedscript.api.ProvisioningException;
import io.camunda.connector.managedscript.api.ScriptLanguage;
import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Runs managed scripts through one Cloud Run function (Cloud Functions v2 API) per language and
 * cluster (ADR-0008): a function running the wrapper, invoked through its {@code run.app} URL with
 * the script and its input.
 *
 * <ul>
 *   <li>The name of an executor carries the digest of its wrapper only. Runtime instances of a
 *       cluster converge on it: one creation is accepted, the others find the function and wait
 *       until it is active. An existing executor is adopted without uploading its source again,
 *       which saves writes from the quota that every cluster of the project shares.
 *   <li>Memory, function timeout, {@code maxInstanceCount}, service account and request concurrency
 *       are configuration: when the provider adopts an executor, it updates them in place, as a new
 *       revision, if they differ, so a configuration change leaves no function behind.
 *   <li>The request concurrency is 1: after each call, the wrapper kills every process it did not
 *       start with, which would kill a concurrent script.
 *   <li>An invocation is a single HTTP request, never sent again, so that a script runs at most
 *       once per job attempt: job retries handle the rest.
 *   <li>The provider never deletes an executor: Controller, or the operator, does.
 * </ul>
 */
public class GcpCloudRunManagedCodeProvider implements ManagedCodeProvider, AutoCloseable {

  public static final String NAME = "gcp";

  static final String OWNER_LABEL = "camunda-io_managed-by";
  static final String OWNER_VALUE = "camunda-managed-script";
  static final String DIGEST_LABEL = "camunda-io_executor-digest";

  /** Cloud Run gives the wrapper no remaining time: it reads the function timeout from here. */
  static final String FUNCTION_TIMEOUT_VARIABLE = "EXECUTOR_FUNCTION_TIMEOUT_MS";

  /** The request size limit of Cloud Run for an HTTP/1 server, as the Functions Framework is. */
  static final int MAX_REQUEST_BYTES = 32 * 1024 * 1024;

  /**
   * Beyond the execution timeout, so that the wrapper, not Cloud Run, ends a script that runs late.
   */
  static final Duration FUNCTION_TIMEOUT_MARGIN = Duration.ofSeconds(5);

  /** The longest timeout of an HTTP Cloud Run function. */
  static final int MAX_FUNCTION_TIMEOUT_SECONDS = 3600;

  /** What an invocation may take beyond the function timeout: start and network. */
  static final Duration INVOCATION_MARGIN = Duration.ofSeconds(30);

  /** How long an executor may take to build and become active, or to apply new settings. */
  static final Duration ACTIVATION_TIMEOUT = Duration.ofMinutes(10);

  /** The settings the provider updates in place; the source is never updated. */
  static final List<String> SETTINGS =
      List.of(
          "service_config.available_memory",
          "service_config.timeout_seconds",
          "service_config.max_instance_count",
          "service_config.max_instance_request_concurrency",
          "service_config.service_account_email",
          "service_config.environment_variables",
          "service_config.all_traffic_on_latest_revision");

  private static final Duration POLL_INTERVAL = Duration.ofSeconds(5);
  private static final Duration UPLOAD_TIMEOUT = Duration.ofMinutes(1);
  private static final int MAX_LABEL_VALUE_LENGTH = 63;
  private static final int MAX_MESSAGE_LENGTH = 1024;

  /** How IAM reports a denied ID token, which the auth library only gives as a message. */
  private static final Pattern DENIED_ID_TOKEN = Pattern.compile("Error code 40[13] ");

  private static final Logger LOG = LoggerFactory.getLogger(GcpCloudRunManagedCodeProvider.class);

  private final Map<ScriptLanguage, CloudRunExecutor> executors =
      new EnumMap<>(ScriptLanguage.class);
  private final Map<ScriptLanguage, Endpoint> endpoints = new ConcurrentHashMap<>();
  private final Map<String, IdTokenCredentials> idTokens = new ConcurrentHashMap<>();
  private final FunctionServiceClient functions;
  private final HttpClient http;
  private final IdTokenProvider idTokenProvider;
  private final ObjectMapper mapper;
  private final String prefix;
  private final String location;
  private final String functionServiceAccount;
  private final String buildServiceAccount;
  private final int memoryMb;
  private final long memoryBytes;
  private final int maxInstanceCount;
  private final int functionTimeoutSeconds;
  private final Duration activationTimeout;
  private final Duration pollInterval;
  private final Sleeper sleeper;

  /**
   * @param functions the control-plane client, with its default retries
   * @param http the client of the uploads and invocations, which never sends a request again
   * @param idTokenProvider the identity that calls the executors
   */
  public GcpCloudRunManagedCodeProvider(
      FunctionServiceClient functions,
      HttpClient http,
      IdTokenProvider idTokenProvider,
      ObjectMapper mapper,
      String prefix,
      GcpManagedCodeProperties properties,
      Duration executionTimeout) {
    this(
        functions,
        http,
        idTokenProvider,
        mapper,
        prefix,
        properties,
        executionTimeout,
        ACTIVATION_TIMEOUT,
        POLL_INTERVAL,
        Thread::sleep);
  }

  GcpCloudRunManagedCodeProvider(
      FunctionServiceClient functions,
      HttpClient http,
      IdTokenProvider idTokenProvider,
      ObjectMapper mapper,
      String prefix,
      GcpManagedCodeProperties properties,
      Duration executionTimeout,
      Duration activationTimeout,
      Duration pollInterval,
      Sleeper sleeper) {
    this.functions = functions;
    this.http = http;
    this.idTokenProvider = idTokenProvider;
    this.mapper = mapper;
    this.prefix = prefix;
    this.location = properties.location();
    this.functionServiceAccount = properties.functionServiceAccount();
    this.buildServiceAccount =
        "projects/%s/serviceAccounts/%s"
            .formatted(properties.projectId(), properties.buildServiceAccount());
    this.memoryMb = properties.memoryMb();
    this.memoryBytes = GcpManagedCodeProperties.memoryBytes(properties.availableMemory());
    this.maxInstanceCount = properties.maxInstanceCount();
    this.functionTimeoutSeconds = functionTimeoutSeconds(executionTimeout);
    this.activationTimeout = activationTimeout;
    this.pollInterval = pollInterval;
    this.sleeper = sleeper;
    for (var language : ScriptLanguage.values()) {
      executors.put(language, CloudRunExecutor.of(language));
    }
    LOG.info(
        "The gcp managed-code provider runs scripts through the Cloud Run functions named"
            + " '{}py-*' and '{}js-*' in {}",
        prefix,
        prefix,
        location);
  }

  @Override
  public String name() {
    return NAME;
  }

  /**
   * The executor of each language. Cloud Run functions have no log group of their own: their logs
   * are entries of the project, which expire with its retention.
   */
  @Override
  public List<ExecutorReference> executors() {
    return executors.values().stream()
        .map(executor ->
            new ExecutorReference(executor.language(), executor.functionName(prefix), null))
        .toList();
  }

  /**
   * Creates the executor of the language unless it exists, uploading its source first. Then waits
   * until it is active, and updates its settings in place if they differ from the configuration.
   *
   * @throws ProvisioningException with a code from the failure of the GCP call; {@link
   *     ProvisioningException#permissionDenied()} for a denied identity, such as a grant that has
   *     not applied yet
   */
  @Override
  public void ensureExecutors(ScriptLanguage language) {
    final var executor = executors.get(language);
    final var name = executor.functionName(prefix);
    try {
      final var existing = read(name);
      final var creation = existing == null ? create(executor, name) : null;
      endpoints.put(language, new Endpoint(converge(name, creation, existing)));
    } catch (ApiException e) {
      throw failure(name, code(e), message(e), e);
    } catch (IOException e) {
      throw new ProvisioningException(
          ErrorCode.PLATFORM_UNAVAILABLE,
          bounded(
              "Cannot upload the source of Cloud Run executor '%s': %s"
                  .formatted(name, message(e))),
          e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new ProvisioningException(
          ErrorCode.PLATFORM_INTERRUPTED,
          "Interrupted while making Cloud Run executor '%s' ready".formatted(name),
          e);
    }
  }

  /**
   * Posts the request to the URL of the executor of the language, with an ID token of the identity,
   * once.
   *
   * @throws ProvisioningException only for a denied invocation or ID token, marked {@link
   *     ProvisioningException#permissionDenied()}; every other failure is a {@code FAILED} response
   */
  @Override
  public ExecutionResponse execute(ScriptLanguage language, ExecutionRequest request) {
    final var name = executors.get(language).functionName(prefix);
    final var endpoint = endpoints.get(language);
    if (endpoint == null) {
      return ExecutionResponse.failed(
          ErrorCode.DEPLOYMENT_MISSING,
          "The URL of Cloud Run executor '%s' is not known: it was not made ready".formatted(name));
    }
    final var url = endpoint.url();
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
          "The script and its input take %d bytes, more than the %d of a Cloud Run request"
              .formatted(payload.length, MAX_REQUEST_BYTES));
    }
    final String token;
    try {
      token = idToken(url);
    } catch (IOException e) {
      if (DENIED_ID_TOKEN.matcher(message(e)).find()) {
        // Thrown, not returned: the connector retries it while the grants of a new cluster
        // propagate.
        throw ProvisioningException.permissionDenied(
            bounded(
                "GCP denied an ID token to call Cloud Run executor '%s': %s"
                    .formatted(name, message(e))),
            e);
      }
      return ExecutionResponse.failed(
          ErrorCode.PLATFORM_UNAVAILABLE,
          bounded(
              "Cannot get an ID token to call Cloud Run executor '%s': %s"
                  .formatted(name, message(e))));
    }
    final HttpResponse<String> response;
    try {
      response =
          GcpClients.send(
              http,
              HttpRequest.newBuilder(URI.create(url))
                  .timeout(Duration.ofSeconds(functionTimeoutSeconds).plus(INVOCATION_MARGIN))
                  .header("Authorization", "Bearer " + token)
                  .header("Content-Type", "application/json")
                  .POST(HttpRequest.BodyPublishers.ofByteArray(payload))
                  .build(),
              HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    } catch (ConnectException | HttpConnectTimeoutException e) {
      return ExecutionResponse.failed(
          ErrorCode.PLATFORM_UNAVAILABLE,
          bounded("Cloud Run executor '%s' was not called: %s".formatted(name, message(e))));
    } catch (IOException e) {
      // The request may have reached the function: calling it again could run the script twice.
      return ExecutionResponse.failed(
          ErrorCode.PLATFORM_EXECUTION_FAILED,
          bounded(
              "The call to Cloud Run executor '%s' failed after it was sent: %s"
                  .formatted(name, message(e))));
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return ExecutionResponse.failed(
          ErrorCode.PLATFORM_INTERRUPTED,
          "Interrupted while calling Cloud Run executor '%s'".formatted(name));
    }
    return response(language, name, endpoint, response);
  }

  @Override
  public void close() {
    functions.close();
    http.close();
  }

  /**
   * The execution timeout and the margin of the wrapper, in whole seconds.
   *
   * @throws IllegalArgumentException above the 3600 seconds of an HTTP Cloud Run function
   */
  static int functionTimeoutSeconds(Duration executionTimeout) {
    final long millis = executionTimeout.plus(FUNCTION_TIMEOUT_MARGIN).toMillis();
    final long seconds = (millis + 999) / 1000;
    if (seconds > MAX_FUNCTION_TIMEOUT_SECONDS) {
      throw new IllegalArgumentException(
          "camunda.connector.managed-code.execution-timeout must be at most %d seconds on Cloud Run"
                  .formatted(MAX_FUNCTION_TIMEOUT_SECONDS - FUNCTION_TIMEOUT_MARGIN.toSeconds())
              + " functions, whose HTTP functions run for at most %d seconds"
                  .formatted(MAX_FUNCTION_TIMEOUT_SECONDS));
    }
    return Math.toIntExact(seconds);
  }

  private String functionName(String name) {
    return location + "/functions/" + name;
  }

  /** The function, or {@code null} when it does not exist. */
  private Function read(String name) {
    try {
      return functions.getFunction(functionName(name));
    } catch (ApiException e) {
      if (code(e) == StatusCode.Code.NOT_FOUND) {
        return null;
      }
      throw e;
    }
  }

  /**
   * Uploads the source, then starts creating the function.
   *
   * @return the operation of the creation, or {@code null} when another instance created the
   *     function first
   */
  private Operation create(CloudRunExecutor executor, String name)
      throws IOException, InterruptedException {
    final var upload =
        functions.generateUploadUrl(
            GenerateUploadUrlRequest.newBuilder().setParent(location).build());
    upload(name, upload.getUploadUrl(), executor.zip());
    final var function =
        Function.newBuilder()
            // Required although the request names the function too: an empty name is refused.
            .setName(functionName(name))
            .setDescription("Camunda managed-script executor of " + executor.language().language())
            .setEnvironment(Environment.GEN_2)
            .putLabels(OWNER_LABEL, OWNER_VALUE)
            .putLabels(DIGEST_LABEL, executor.digest().substring(0, MAX_LABEL_VALUE_LENGTH))
            .setBuildConfig(
                BuildConfig.newBuilder()
                    .setRuntime(executor.runtime())
                    .setEntryPoint(executor.entryPoint())
                    .setServiceAccount(buildServiceAccount)
                    .setSource(Source.newBuilder().setStorageSource(upload.getStorageSource())))
            .setServiceConfig(serviceConfig(Map.of()))
            .build();
    try {
      final var operation =
          functions
              .createFunctionCallable()
              .call(
                  CreateFunctionRequest.newBuilder()
                      .setParent(location)
                      .setFunctionId(name)
                      .setFunction(function)
                      .build());
      LOG.info("Creating Cloud Run executor '{}'", name);
      return operation;
    } catch (ApiException e) {
      if (code(e) != StatusCode.Code.ALREADY_EXISTS) {
        throw e;
      }
      LOG.info(
          "Cloud Run executor '{}' was created by another runtime instance: adopting it", name);
      return null;
    }
  }

  /** PUT to the signed URL, which authorizes the upload by itself. */
  private void upload(String name, String url, byte[] zip)
      throws IOException, InterruptedException {
    final var response =
        GcpClients.send(
            http,
            HttpRequest.newBuilder(URI.create(url))
                .timeout(UPLOAD_TIMEOUT)
                .header("Content-Type", "application/zip")
                .PUT(HttpRequest.BodyPublishers.ofByteArray(zip))
                .build(),
            HttpResponse.BodyHandlers.discarding());
    final int status = response.statusCode();
    if (status == 401 || status == 403) {
      // The URL comes from a grant the runtime was given, which may still propagate.
      throw ProvisioningException.permissionDenied(
          "GCP denied the upload of the source of Cloud Run executor '%s' (HTTP %d)"
              .formatted(name, status),
          null);
    }
    if (status / 100 != 2) {
      throw new ProvisioningException(
          status == 429
              ? ErrorCode.PLATFORM_THROTTLED
              : status >= 500 ? ErrorCode.PLATFORM_UNAVAILABLE : ErrorCode.PLATFORM_REJECTED,
          "Uploading the source of Cloud Run executor '%s' failed with HTTP %d"
              .formatted(name, status));
    }
  }

  /**
   * Waits until the operation of this instance, if any, is done and the function is active, then
   * updates its settings if they differ, and waits for that update. Another instance may update it
   * at the same time: the conflict is waited out, and the settings compared again.
   *
   * @param pending the operation that created the function, or {@code null}
   * @param read the function as just read, or {@code null} to read it
   * @return the {@code run.app} URL of the function
   */
  private String converge(String name, Operation pending, Function read)
      throws InterruptedException {
    final long deadline = System.nanoTime() + activationTimeout.toNanos();
    boolean updated = false;
    Function known = read;
    while (true) {
      if (pending != null) {
        final Operation operation;
        try {
          operation = functions.getOperationsClient().getOperation(pending.getName());
        } catch (ApiException e) {
          if (code(e) != StatusCode.Code.RESOURCE_EXHAUSTED) {
            throw e;
          }
          pause(
              deadline,
              retryAfter(e),
              "Reading the operation of Cloud Run executor '%s' is throttled".formatted(name));
          continue;
        }
        if (!operation.getDone()) {
          pause(
              deadline,
              withJitter(pollInterval),
              "Cloud Run executor '%s' is still deploying".formatted(name));
          continue;
        }
        if (operation.hasError()) {
          throw failure(name, operation.getError());
        }
        pending = null;
      }
      final Function function;
      try {
        function = known != null ? known : read(name);
        known = null;
      } catch (ApiException e) {
        if (code(e) != StatusCode.Code.RESOURCE_EXHAUSTED) {
          throw e;
        }
        pause(
            deadline,
            retryAfter(e),
            "Reading Cloud Run executor '%s' is throttled".formatted(name));
        continue;
      }
      if (function == null) {
        throw new ProvisioningException(
            ErrorCode.PLATFORM_UNAVAILABLE,
            "Cloud Run executor '%s' was deleted while it was made ready".formatted(name));
      }
      switch (function.getState()) {
        case ACTIVE -> {
          // Compared below.
        }
        case FAILED, UNKNOWN ->
            throw new ProvisioningException(
                ErrorCode.PLATFORM_REJECTED,
                bounded(
                    "Cloud Run executor '%s' is %s: %s"
                        .formatted(name, function.getState(), stateMessages(function))));
        default -> {
          pause(
              deadline,
              withJitter(pollInterval),
              "Cloud Run executor '%s' is still %s".formatted(name, function.getState()));
          continue;
        }
      }
      final var url = function.getServiceConfig().getUri();
      if (matches(function.getServiceConfig())) {
        if (!url.isBlank()) {
          return url;
        }
        pause(
            deadline,
            withJitter(pollInterval),
            "Cloud Run executor '%s' has no URL".formatted(name));
        continue;
      }
      if (updated) {
        throw new ProvisioningException(
            ErrorCode.PLATFORM_REJECTED,
            bounded(
                "Cloud Run executor '%s' did not apply its new settings: %s"
                    .formatted(name, stateMessages(function))));
      }
      try {
        pending =
            functions
                .updateFunctionCallable()
                .call(
                    UpdateFunctionRequest.newBuilder()
                        .setFunction(
                            Function.newBuilder()
                                .setName(functionName(name))
                                .setServiceConfig(
                                    serviceConfig(
                                        function.getServiceConfig().getEnvironmentVariablesMap())))
                        .setUpdateMask(FieldMask.newBuilder().addAllPaths(SETTINGS))
                        .build());
        updated = true;
        LOG.info(
            "Updating the settings of Cloud Run executor '{}': memory {} MiB, timeout {} s,"
                + " max instances {}, service account {}",
            name,
            memoryMb,
            functionTimeoutSeconds,
            maxInstanceCount,
            functionServiceAccount);
      } catch (ApiException e) {
        if (!conflicting(code(e), message(e))) {
          throw e;
        }
        LOG.debug("Cloud Run executor '{}' is being updated by another runtime instance", name);
        pause(
            deadline,
            withJitter(pollInterval),
            "Cloud Run executor '%s' did not apply its settings".formatted(name));
      }
    }
  }

  /**
   * The configured settings, with the environment variables of the function, which the platform
   * adds to.
   */
  private ServiceConfig.Builder serviceConfig(Map<String, String> environment) {
    return ServiceConfig.newBuilder()
        .setAvailableMemory(memoryMb + "Mi")
        .setTimeoutSeconds(functionTimeoutSeconds)
        .setMaxInstanceCount(maxInstanceCount)
        .setMaxInstanceRequestConcurrency(1)
        .setServiceAccountEmail(functionServiceAccount)
        // Over any traffic split of an adopted function, so that every call gets these settings.
        .setAllTrafficOnLatestRevision(true)
        .putAllEnvironmentVariables(environment)
        .putEnvironmentVariables(FUNCTION_TIMEOUT_VARIABLE, functionTimeoutMillis());
  }

  private boolean matches(ServiceConfig settings) {
    return GcpManagedCodeProperties.memoryBytes(settings.getAvailableMemory()) == memoryBytes
        && settings.getTimeoutSeconds() == functionTimeoutSeconds
        && settings.getMaxInstanceCount() == maxInstanceCount
        && settings.getMaxInstanceRequestConcurrency() == 1
        && functionServiceAccount.equals(settings.getServiceAccountEmail())
        && settings.getAllTrafficOnLatestRevision()
        && functionTimeoutMillis()
            .equals(settings.getEnvironmentVariablesOrDefault(FUNCTION_TIMEOUT_VARIABLE, null));
  }

  private String functionTimeoutMillis() {
    return Long.toString(functionTimeoutSeconds * 1_000L);
  }

  /** The response of the wrapper, or the failure of the platform that answered instead. */
  private ExecutionResponse response(
      ScriptLanguage language, String name, Endpoint endpoint, HttpResponse<String> response) {
    final int status = response.statusCode();
    final var body = response.body() == null ? "" : response.body();
    if (status == 401 || status == 403) {
      // Thrown, not returned: the connector retries it while the grants of a new cluster
      // propagate.
      throw ProvisioningException.permissionDenied(
          "GCP denied the call to Cloud Run executor '%s' (HTTP %d)".formatted(name, status), null);
    }
    if (status == 200) {
      try {
        final var result = mapper.readValue(body, ExecutionResponse.class);
        if (result != null && result.wellFormed()) {
          return result;
        }
      } catch (IOException e) {
        // Not a response: reported below.
      }
      // The wrapper answers every call with a contract response, failures included.
      return ExecutionResponse.failed(
          ErrorCode.PLATFORM_EXECUTION_FAILED,
          bounded(
              "Cloud Run executor '%s' answered without a contract response: %s"
                  .formatted(name, body)));
    }
    return switch (status) {
      case 404 -> {
        // Only the endpoint this call used: a late 404 must not drop one made ready since.
        endpoints.remove(language, endpoint);
        yield ExecutionResponse.failed(
            ErrorCode.DEPLOYMENT_MISSING, "Cloud Run executor '%s' does not exist".formatted(name));
      }
      case 413 ->
          ExecutionResponse.failed(
              ErrorCode.RESOURCE_LIMIT,
              "The script and its input are too large for Cloud Run executor '%s'".formatted(name));
      case 429 ->
          ExecutionResponse.failed(
              ErrorCode.PLATFORM_THROTTLED,
              "Cloud Run executor '%s' has no instance available (HTTP 429)".formatted(name));
      case 502, 503 ->
          ExecutionResponse.failed(
              ErrorCode.PLATFORM_UNAVAILABLE,
              bounded(
                  "Cloud Run executor '%s' is unavailable (HTTP %d): %s"
                      .formatted(name, status, body)));
      // The wrapper answers script failures, timeouts and limits with 200: a server error is the
      // platform's, such as the function timeout (504) or an exhausted function memory (500).
      default ->
          ExecutionResponse.failed(
              status >= 500 ? ErrorCode.PLATFORM_EXECUTION_FAILED : ErrorCode.PLATFORM_REJECTED,
              bounded(
                  "Cloud Run executor '%s' failed (HTTP %d): %s".formatted(name, status, body)));
    };
  }

  /**
   * An ID token for the URL, with the email claim, which the IAM check of Cloud Run needs. One
   * credential per URL, which refreshes its token before it expires.
   */
  private String idToken(String url) throws IOException {
    final var credentials =
        idTokens.computeIfAbsent(
            url,
            audience ->
                IdTokenCredentials.newBuilder()
                    .setIdTokenProvider(idTokenProvider)
                    .setTargetAudience(audience)
                    .setOptions(List.of(IdTokenProvider.Option.INCLUDE_EMAIL))
                    .build());
    credentials.refreshIfExpired();
    return credentials.getIdToken().getTokenValue();
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

  /** The retry delay of a throttled call, otherwise twice the poll interval. */
  private Duration retryAfter(ApiException e) {
    final var details = e.getErrorDetails();
    final var retryInfo = details == null ? null : details.getRetryInfo();
    if (retryInfo != null && retryInfo.hasRetryDelay()) {
      final var delay = retryInfo.getRetryDelay();
      return withJitter(Duration.ofSeconds(delay.getSeconds(), delay.getNanos()));
    }
    return withJitter(pollInterval.multipliedBy(2));
  }

  /** Up to half more, so that instances started together do not poll in step. */
  private static Duration withJitter(Duration duration) {
    final long millis = Math.max(1, duration.toMillis());
    return Duration.ofMillis(millis + ThreadLocalRandom.current().nextLong(millis / 2 + 1));
  }

  /** The failure of an operation that GCP ran and reported done. */
  private static ProvisioningException failure(String name, Status error) {
    final var code = Code.forNumber(error.getCode());
    return failure(
        name,
        code == null ? StatusCode.Code.UNKNOWN : StatusCode.Code.valueOf(code.name()),
        error.getMessage(),
        null);
  }

  /** The error code of a control-plane call that failed, as ADR-0008 maps it. */
  private static ProvisioningException failure(
      String name, StatusCode.Code code, String detail, Throwable cause) {
    final var message =
        bounded(
            "GCP rejected a call to make Cloud Run executor '%s' ready (%s): %s"
                .formatted(name, code, detail));
    return switch (code) {
      case PERMISSION_DENIED, UNAUTHENTICATED ->
          ProvisioningException.permissionDenied(message, cause);
      case RESOURCE_EXHAUSTED ->
          new ProvisioningException(ErrorCode.PLATFORM_THROTTLED, message, cause);
      case UNAVAILABLE, INTERNAL, DEADLINE_EXCEEDED, UNKNOWN, CANCELLED ->
          new ProvisioningException(ErrorCode.PLATFORM_UNAVAILABLE, message, cause);
      default ->
          new ProvisioningException(
              conflicting(code, detail)
                  ? ErrorCode.PLATFORM_UNAVAILABLE
                  : ErrorCode.PLATFORM_REJECTED,
              message,
              cause);
    };
  }

  /**
   * Another operation on the function is in progress: another instance creating or updating it. It
   * passes.
   */
  private static boolean conflicting(StatusCode.Code code, String detail) {
    return code == StatusCode.Code.ABORTED
        || code == StatusCode.Code.ALREADY_EXISTS
        || code == StatusCode.Code.FAILED_PRECONDITION
            && detail != null
            && detail.contains("in progress");
  }

  private static StatusCode.Code code(ApiException e) {
    return e.getStatusCode().getCode();
  }

  private static String stateMessages(Function function) {
    final var messages =
        function.getStateMessagesList().stream()
            .map(StateMessage::getMessage)
            .filter(message -> !message.isBlank())
            .collect(Collectors.joining("; "));
    return messages.isEmpty() ? "no reason given" : messages;
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

  /**
   * The URL of an executor, as one round of making it ready found it. Compared by identity: a
   * function created again keeps its URL, and a 404 of an earlier call removes only its own round.
   */
  private static final class Endpoint {
    private final String url;

    Endpoint(String url) {
      this.url = url;
    }

    String url() {
      return url;
    }
  }
}
