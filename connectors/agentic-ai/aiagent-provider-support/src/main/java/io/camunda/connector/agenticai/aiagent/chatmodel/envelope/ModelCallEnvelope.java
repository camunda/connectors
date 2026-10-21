/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.chatmodel.envelope;

import static io.camunda.connector.agenticai.aiagent.api.chatmodel.ChatModelErrorCodes.ERROR_CODE_FAILED_MODEL_CALL;

import io.camunda.connector.agenticai.aiagent.api.chatmodel.ChatModel;
import io.camunda.connector.agenticai.aiagent.api.chatmodel.ChatModelRejectedException;
import io.camunda.connector.agenticai.aiagent.api.chatmodel.ChatRequest;
import io.camunda.connector.agenticai.aiagent.api.chatmodel.ChatResult;
import io.camunda.connector.agenticai.aiagent.api.chatmodel.ContextWindowExceededException;
import io.camunda.connector.api.error.ConnectorException;
import java.time.Duration;
import java.util.concurrent.CompletionException;
import org.jspecify.annotations.Nullable;

/**
 * Concerns every model call has in common, applied around the {@link ChatModel} of a provider so
 * that the provider only implements the wire mapping:
 *
 * <ul>
 *   <li>measures the execution time of the call and records it in the metrics of the result
 *   <li>passes on errors that already carry a connector error code and rejections the provider
 *       recognized itself
 *   <li>maps every other failure to {@code FAILED_MODEL_CALL} with a message of one format: {@code
 *       Model call failed with HTTP <status> (<type>): <message>} for a failure the {@link
 *       SdkFailureMapper} of the provider recognizes, {@code Model call failed: <detail>} for any
 *       other
 *   <li>maps a request that exceeds the context window of the model to {@link
 *       ContextWindowExceededException}
 * </ul>
 */
public final class ModelCallEnvelope implements ChatModel {

  /** The message of the SDK exception that wraps an {@link java.io.IOException}. */
  private static final String GENERIC_SDK_FAILURE_MESSAGE = "Request failed";

  private final ChatModel delegate;
  private final SdkFailureMapper failureMapper;

  public ModelCallEnvelope(ChatModel delegate, SdkFailureMapper failureMapper) {
    this.delegate = delegate;
    this.failureMapper = failureMapper;
  }

  @Override
  public ChatResult execute(ChatRequest request) {
    final long startNanos = System.nanoTime();
    final ChatResult result;
    try {
      result = delegate.execute(request);
    } catch (Exception e) {
      throw translate(unwrap(e));
    }
    return withExecutionTime(result, Duration.ofNanos(System.nanoTime() - startNanos));
  }

  @Override
  public void close() {
    delegate.close();
  }

  /** An asynchronous SDK call reports its failure wrapped in a {@link CompletionException}. */
  private static Throwable unwrap(Exception e) {
    return e instanceof CompletionException && e.getCause() != null ? e.getCause() : e;
  }

  private RuntimeException translate(Throwable failure) {
    if (failure instanceof ConnectorException || failure instanceof ChatModelRejectedException) {
      return (RuntimeException) failure;
    }

    final var sdkFailure = failureMapper.map(failure);
    if (sdkFailure.isPresent()) {
      final var mapped = sdkFailure.get();
      if (mapped.contextWindowExceeded()) {
        return new ContextWindowExceededException(
            ContextWindowExceededException.DEFAULT_MESSAGE, failure, null);
      }
      return new ConnectorException(
          ERROR_CODE_FAILED_MODEL_CALL,
          "Model call failed with HTTP %d (%s): %s"
              .formatted(
                  mapped.statusCode(),
                  orDefault(mapped.type(), failure),
                  orDefault(mapped.message(), failure)),
          failure);
    }

    return new ConnectorException(
        ERROR_CODE_FAILED_MODEL_CALL, "Model call failed: %s".formatted(detail(failure)), failure);
  }

  /**
   * The message of the failure, unless it is blank or the generic message of an SDK exception that
   * wraps an {@link java.io.IOException}: then the message of the cause, or the class name if the
   * cause has no message either.
   */
  private static String detail(Throwable failure) {
    final var message = failure.getMessage();
    if (message != null && isInformative(message)) {
      return message;
    }
    final var cause = failure.getCause();
    if (cause != null) {
      final var causeMessage = cause.getMessage();
      return causeMessage != null && isInformative(causeMessage)
          ? causeMessage
          : cause.getClass().getSimpleName();
    }
    return message != null && !message.isBlank() ? message : failure.getClass().getSimpleName();
  }

  private static boolean isInformative(String message) {
    return !message.isBlank() && !GENERIC_SDK_FAILURE_MESSAGE.equals(message);
  }

  private static String orDefault(@Nullable String value, Throwable failure) {
    return value != null && !value.isBlank() ? value : failure.getClass().getSimpleName();
  }

  private static ChatResult withExecutionTime(ChatResult result, Duration executionTime) {
    final var metrics = result.metrics().withExecutionTime(executionTime);
    return switch (result) {
      case ChatResult.Completed completed ->
          new ChatResult.Completed(completed.assistantMessage(), metrics);
      case ChatResult.Continuation continuation ->
          new ChatResult.Continuation(continuation.assistantMessage(), metrics);
    };
  }
}
