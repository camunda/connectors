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
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

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

  /** The failure codes that a script can cause: the codes of the child shims, and a timeout. */
  private static final Set<String> SCRIPT_FAILURE_CODES =
      Set.of(
          ErrorCode.SCRIPT_ERROR.name(),
          ErrorCode.INVALID_RESULT.name(),
          ErrorCode.RESOURCE_LIMIT.name(),
          ErrorCode.TIMEOUT.name());

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

  // A redirect or a proxy of the JVM would get the token and the script input in plain HTTP.
  private final HttpClient client =
      HttpClient.newBuilder()
          .version(HttpClient.Version.HTTP_1_1)
          .followRedirects(HttpClient.Redirect.NEVER)
          .proxy(HttpClient.Builder.NO_PROXY)
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
   * In a {@code 200} answer, a failure code that a script cannot cause becomes {@link
   * ErrorCode#INVALID_RESULT}. No answer within the timeout of the script and a margin becomes
   * {@link ErrorCode#PLATFORM_UNAVAILABLE}.
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
    final byte[] body;
    try {
      body = objectMapper.writeValueAsBytes(requestBody(language, runtime, request));
    } catch (JacksonException e) {
      return ExecutionResponse.failed(
          ErrorCode.INVALID_INPUT, "The script input cannot be sent to the runner");
    }
    final var timeout = Duration.ofMillis(request.timeoutMs()).plus(answerMargin);
    final HttpRequest httpRequest;
    try {
      httpRequest =
          HttpRequest.newBuilder(executions)
              .timeout(timeout)
              .header("Authorization", "Bearer " + token)
              .header("Content-Type", "application/json")
              .header("Accept", "application/json")
              .POST(HttpRequest.BodyPublishers.ofByteArray(body))
              .build();
    } catch (IllegalArgumentException e) {
      // The JDK quotes the header, and with it the token, in the message of this exception.
      return ExecutionResponse.failed(
          ErrorCode.PLATFORM_ERROR, "The token for the runner is not a valid header value");
    }
    return call(httpRequest, timeout);
  }

  private static Map<String, Object> requestBody(
      ScriptLanguage language, String runtime, ExecutionRequest request) {
    final Map<String, Object> body = new LinkedHashMap<>();
    body.put("language", language.language());
    body.put("runtime", runtime);
    body.putAll(request.payload());
    return body;
  }

  private ExecutionResponse call(HttpRequest httpRequest, Duration timeout) {
    // The timeout of the request ends when the headers arrive, so it does not bound the body.
    final var answer =
        client.sendAsync(
            httpRequest,
            info ->
                info.statusCode() == 200
                    ? new BoundedBody(MAX_RESPONSE_BYTES)
                    : HttpResponse.BodySubscribers.<byte[]>replacing(null));
    final HttpResponse<byte[]> response;
    try {
      response = answer.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
    } catch (TimeoutException e) {
      answer.cancel(true);
      return ExecutionResponse.failed(
          ErrorCode.PLATFORM_UNAVAILABLE, "The runner did not answer in time");
    } catch (InterruptedException e) {
      answer.cancel(true);
      Thread.currentThread().interrupt();
      return ExecutionResponse.failed(
          ErrorCode.PLATFORM_INTERRUPTED, "The call to the runner was interrupted");
    } catch (ExecutionException e) {
      return switch (e.getCause()) {
        case AnswerTooLarge tooLarge ->
            ExecutionResponse.failed(
                ErrorCode.INVALID_RESULT,
                "The answer of the runner is larger than %d bytes".formatted(MAX_RESPONSE_BYTES));
        case HttpTimeoutException timedOut ->
            ExecutionResponse.failed(
                ErrorCode.PLATFORM_UNAVAILABLE, "The runner did not answer in time");
        case IOException failed ->
            ExecutionResponse.failed(
                ErrorCode.PLATFORM_UNAVAILABLE,
                "The runner is not available: " + failed.getClass().getSimpleName());
        case null, default ->
            ExecutionResponse.failed(ErrorCode.PLATFORM_ERROR, "The call to the runner failed");
      };
    }
    final int status = response.statusCode();
    return switch (status) {
      case 200 -> executionResponse(response.body());
      case 400, 401, 403 ->
          ExecutionResponse.failed(
              ErrorCode.PLATFORM_REJECTED, "The runner rejected the call: HTTP " + status);
      // Kourier answers 404 when the Knative Service is gone, as for a suspended runner.
      case 404 ->
          ExecutionResponse.failed(
              ErrorCode.PLATFORM_UNAVAILABLE, "The runner is not available: HTTP " + status);
      case 413 ->
          ExecutionResponse.failed(
              ErrorCode.INVALID_INPUT, "The runner rejected the size of the call: HTTP " + status);
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

  private ExecutionResponse executionResponse(byte[] answer) {
    final ExecutionResponse response;
    try {
      response = objectMapper.readValue(answer, ExecutionResponse.class);
    } catch (IOException e) {
      return ExecutionResponse.failed(
          ErrorCode.INVALID_RESULT, "The answer of the runner is not an execution response");
    }
    if (response == null
        || !ExecutionResponse.FAILED.equals(response.outcome())
        || response.error() == null
        || (response.error().code() != null
            && SCRIPT_FAILURE_CODES.contains(response.error().code()))) {
      return response;
    }
    // A runner that passes on a code that the script wrote must not choose the retries of the job.
    return new ExecutionResponse(
        response.contractVersion(),
        ExecutionResponse.FAILED,
        null,
        new ExecutionResponse.Error(
            ErrorCode.INVALID_RESULT.name(),
            "The runner reported the code '%s', which a script cannot cause"
                .formatted(response.error().code()),
            null,
            ErrorCode.INVALID_RESULT.retryable(),
            false),
        response.logs(),
        response.logsTruncated());
  }

  /** Collects a body up to a size, and fails with {@link AnswerTooLarge} above it. */
  private static final class BoundedBody implements HttpResponse.BodySubscriber<byte[]> {

    private final int limit;
    private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    private final CompletableFuture<byte[]> result = new CompletableFuture<>();
    private Flow.Subscription subscription;

    BoundedBody(int limit) {
      this.limit = limit;
    }

    @Override
    public void onSubscribe(Flow.Subscription subscription) {
      this.subscription = subscription;
      subscription.request(Long.MAX_VALUE);
    }

    @Override
    public void onNext(List<ByteBuffer> buffers) {
      for (var buffer : buffers) {
        if (result.isDone()) {
          return;
        }
        if (bytes.size() + buffer.remaining() > limit) {
          subscription.cancel();
          result.completeExceptionally(new AnswerTooLarge());
          return;
        }
        final var chunk = new byte[buffer.remaining()];
        buffer.get(chunk);
        bytes.writeBytes(chunk);
      }
    }

    @Override
    public void onError(Throwable throwable) {
      result.completeExceptionally(throwable);
    }

    @Override
    public void onComplete() {
      result.complete(bytes.toByteArray());
    }

    @Override
    public CompletionStage<byte[]> getBody() {
      return result;
    }
  }

  private static final class AnswerTooLarge extends RuntimeException {

    AnswerTooLarge() {
      super(null, null, false, false);
    }
  }
}
