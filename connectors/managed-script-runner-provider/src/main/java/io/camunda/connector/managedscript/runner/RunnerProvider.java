/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.managedscript.runner;

import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.jackson.ConnectorsObjectMapperSupplier;
import io.camunda.connector.managedscript.api.ErrorCode;
import io.camunda.connector.managedscript.api.ExecutionRequest;
import io.camunda.connector.managedscript.api.ExecutionResponse;
import io.camunda.connector.managedscript.api.ManagedCodeProvider;
import io.camunda.connector.managedscript.api.ScriptLanguage;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Runs managed scripts on the sandboxed runner of the cluster, over the runner API: {@code POST
 * /v1/executions} with the execution request, its language and the runtime of that language.
 *
 * <p>Each call sends the projected service account token of the runtime, read again from its file,
 * because the kubelet replaces the token before it expires. The token never goes into a response.
 */
public final class RunnerProvider implements ManagedCodeProvider {

  static final String NAME = "runner";

  /**
   * The largest answer that the provider reads. The runner bounds the script output to 1 MB and the
   * logs to 64 KiB, so a larger answer does not come from a working runner.
   */
  static final int MAX_RESPONSE_BYTES = 4 * 1024 * 1024;

  private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);

  /**
   * Time for an answer on top of the timeout of the script: Knative holds a call in its activator
   * while it starts a runner pod from zero.
   */
  private static final Duration ANSWER_MARGIN = Duration.ofSeconds(30);

  private final URI executions;
  private final Path tokenPath;
  private final Map<String, String> runtimes;
  private final Duration answerMargin;
  private final ObjectMapper objectMapper = ConnectorsObjectMapperSupplier.getCopy();

  // A redirect would send the token to the host that the answer names.
  private final HttpClient client =
      HttpClient.newBuilder()
          .version(HttpClient.Version.HTTP_1_1)
          .followRedirects(HttpClient.Redirect.NEVER)
          .connectTimeout(CONNECT_TIMEOUT)
          .build();

  RunnerProvider(RunnerProviderProperties properties) {
    this(properties, ANSWER_MARGIN);
  }

  RunnerProvider(RunnerProviderProperties properties, Duration answerMargin) {
    this.executions = URI.create(properties.url().replaceAll("/+$", "") + "/v1/executions");
    this.tokenPath = Path.of(properties.tokenPath());
    this.runtimes = properties.runtimes();
    this.answerMargin = answerMargin;
  }

  @Override
  public String name() {
    return NAME;
  }

  /** The sandbox operator creates the runner, so there is nothing to make ready. */
  @Override
  public void ensureExecutors() {}

  /**
   * Maps each answer of the runner to a response: {@code 200} to the execution response that it
   * holds; {@code 400}, {@code 401} and {@code 403} to {@link ErrorCode#PLATFORM_REJECTED}, which
   * leaves no retries; {@code 429} to {@link ErrorCode#PLATFORM_THROTTLED}; no answer and {@code
   * 5xx} to {@link ErrorCode#PLATFORM_UNAVAILABLE}; any other answer to {@link
   * ErrorCode#PLATFORM_ERROR}.
   */
  @Override
  public ExecutionResponse execute(ScriptLanguage language, ExecutionRequest request) {
    final var runtime = runtimes.get(language.language());
    if (runtime == null) {
      return ExecutionResponse.failed(
          ErrorCode.PLATFORM_REJECTED,
          "No runtime is configured for %s scripts".formatted(language.language()));
    }
    final String token;
    try {
      token = Files.readString(tokenPath).strip();
    } catch (IOException e) {
      return ExecutionResponse.failed(
          ErrorCode.PLATFORM_ERROR, "The token for the runner cannot be read");
    }
    if (token.isEmpty()) {
      return ExecutionResponse.failed(
          ErrorCode.PLATFORM_ERROR, "The token for the runner is empty");
    }
    try {
      return call(language, runtime, request, token);
    } catch (HttpTimeoutException e) {
      return ExecutionResponse.failed(
          ErrorCode.PLATFORM_UNAVAILABLE, "The runner did not answer in time");
    } catch (IOException e) {
      return ExecutionResponse.failed(
          ErrorCode.PLATFORM_UNAVAILABLE,
          "The runner is not available: " + e.getClass().getSimpleName());
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return ExecutionResponse.failed(
          ErrorCode.PLATFORM_INTERRUPTED, "The call to the runner was interrupted");
    }
  }

  private ExecutionResponse call(
      ScriptLanguage language, String runtime, ExecutionRequest request, String token)
      throws IOException, InterruptedException {
    final Map<String, Object> body = new LinkedHashMap<>();
    body.put("language", language.language());
    body.put("runtime", runtime);
    body.putAll(request.payload());
    final var httpRequest =
        HttpRequest.newBuilder(executions)
            .timeout(Duration.ofMillis(request.timeoutMs()).plus(answerMargin))
            .header("Authorization", "Bearer " + token)
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
            .POST(HttpRequest.BodyPublishers.ofByteArray(objectMapper.writeValueAsBytes(body)))
            .build();
    final var response = client.send(httpRequest, HttpResponse.BodyHandlers.ofInputStream());
    try (InputStream answer = response.body()) {
      final int status = response.statusCode();
      return switch (status) {
        case 200 -> executionResponse(answer);
        case 400, 401, 403 ->
            ExecutionResponse.failed(
                ErrorCode.PLATFORM_REJECTED, "The runner rejected the call: HTTP " + status);
        case 429 ->
            ExecutionResponse.failed(
                ErrorCode.PLATFORM_THROTTLED, "The runner is at capacity: HTTP " + status);
        default ->
            status >= 500
                ? ExecutionResponse.failed(
                    ErrorCode.PLATFORM_UNAVAILABLE, "The runner is not available: HTTP " + status)
                : ExecutionResponse.failed(
                    ErrorCode.PLATFORM_ERROR, "Unexpected answer of the runner: HTTP " + status);
      };
    }
  }

  private ExecutionResponse executionResponse(InputStream answer) throws IOException {
    final var bytes = answer.readNBytes(MAX_RESPONSE_BYTES + 1);
    if (bytes.length > MAX_RESPONSE_BYTES) {
      return ExecutionResponse.failed(
          ErrorCode.INVALID_RESULT,
          "The answer of the runner is larger than %d bytes".formatted(MAX_RESPONSE_BYTES));
    }
    try {
      return objectMapper.readValue(bytes, ExecutionResponse.class);
    } catch (JacksonException e) {
      return ExecutionResponse.failed(
          ErrorCode.INVALID_RESULT, "The answer of the runner is not an execution response");
    }
  }
}
