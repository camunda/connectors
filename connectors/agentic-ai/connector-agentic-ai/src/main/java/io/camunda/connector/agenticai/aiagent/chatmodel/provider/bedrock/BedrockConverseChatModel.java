/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.chatmodel.provider.bedrock;

import static io.camunda.connector.agenticai.aiagent.agent.AgentErrorCodes.ERROR_CODE_FAILED_MODEL_CALL;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.agenticai.aiagent.chatmodel.ChatModel;
import io.camunda.connector.agenticai.aiagent.chatmodel.ChatModelRejectedException;
import io.camunda.connector.agenticai.aiagent.chatmodel.ChatRequest;
import io.camunda.connector.agenticai.aiagent.chatmodel.ChatResult;
import io.camunda.connector.agenticai.aiagent.model.request.v2.BedrockConverseChatModelConfiguration;
import io.camunda.connector.agenticai.aiagent.util.LoggingSupport;
import io.camunda.connector.api.error.ConnectorException;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.core.SdkPojo;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeAsyncClient;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseResponse;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseStreamRequest;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseStreamResponseHandler;

/**
 * Bedrock {@link ChatModel}: drives the AWS SDK's async {@code converseStream} operation for every
 * call (the synchronous {@code BedrockRuntimeClient} has no streaming operation, so {@code
 * converseStream} is used uniformly, then assembled via {@link BedrockConverseStreamAssembler} into
 * the same {@link ConverseResponse} shape the non-streaming {@code converse} operation would have
 * returned), then delegates to {@link BedrockConverseRequestConverter} and {@link
 * BedrockConverseResponseConverter} to translate to/from the domain model.
 *
 * <p>A fresh {@link BedrockConverseStreamAssembler} is created for every {@link
 * #execute(ChatRequest)} call, since it accumulates mutable per-call stream state. The {@link
 * BedrockRuntimeAsyncClient}, by contrast, is built once by the factory and owned for the lifetime
 * of this instance (one agent request, across all continuation rounds); {@link #close()} closes it
 * once.
 */
public class BedrockConverseChatModel implements ChatModel {

  private static final Logger LOG = LoggerFactory.getLogger(BedrockConverseChatModel.class);

  private final BedrockRuntimeAsyncClient client;
  private final BedrockConverseChatModelConfiguration configuration;
  private final BedrockConverseRequestConverter requestConverter;
  private final BedrockConverseResponseConverter responseConverter;
  private final ObjectMapper objectMapper;

  public BedrockConverseChatModel(
      BedrockRuntimeAsyncClient client,
      BedrockConverseChatModelConfiguration configuration,
      BedrockConverseRequestConverter requestConverter,
      BedrockConverseResponseConverter responseConverter,
      ObjectMapper objectMapper) {
    this.client = client;
    this.configuration = configuration;
    this.requestConverter = requestConverter;
    this.responseConverter = responseConverter;
    this.objectMapper = objectMapper;
  }

  @Override
  public ChatResult execute(ChatRequest request) {
    final ConverseStreamRequest converseStreamRequest =
        requestConverter.toConverseStreamRequest(
            configuration,
            request.executionContext().configuration().response(),
            request.snapshot());
    logTrace("request", converseStreamRequest);

    final long startNanos = System.nanoTime();
    try {
      final BedrockConverseStreamAssembler streamAssembler =
          new BedrockConverseStreamAssembler(objectMapper);
      final ConverseStreamResponseHandler handler =
          ConverseStreamResponseHandler.builder().subscriber(streamAssembler).build();
      client.converseStream(converseStreamRequest, handler).join();

      final ConverseResponse response = streamAssembler.converseResponse();
      logTrace("response", response);
      final Duration executionTime = Duration.ofNanos(System.nanoTime() - startNanos);
      return responseConverter.toResult(response, executionTime);
    } catch (CompletionException e) {
      // CompletableFuture#join() always wraps the underlying failure in a CompletionException; the
      // real cause (typically an AwsServiceException) is unwrapped here so it can be translated the
      // same way a synchronously-thrown AwsServiceException would be below.
      throw toConnectorException(e.getCause() != null ? e.getCause() : e);
    } catch (AwsServiceException e) {
      throw toConnectorException(e);
    } catch (ChatModelRejectedException e) {
      // thrown directly by the response converter when it recognizes a known rejection - let it
      // propagate as-is rather than flattening it into a generic FAILED_MODEL_CALL below.
      throw e;
    } catch (Exception e) {
      throw toConnectorException(e);
    }
  }

  // Generic Jackson introspection can't see the SDK's builder-only accessors, so route the POJO
  // through the SdkPojo->Map codec first, else it silently logs as "{}".
  private void logTrace(String label, SdkPojo pojo) {
    if (LOG.isTraceEnabled()) {
      LOG.trace(
          "Bedrock Converse API {}: {}",
          label,
          LoggingSupport.toJson(objectMapper, BedrockConverseSdkPojoCodec.capture(pojo)));
    }
  }

  private ConnectorException toConnectorException(Throwable cause) {
    if (cause instanceof AwsServiceException awsServiceException) {
      final String errorCode =
          Optional.ofNullable(awsServiceException.awsErrorDetails())
              .map(details -> details.errorCode())
              .orElseGet(() -> awsServiceException.getClass().getSimpleName());
      return new ConnectorException(
          ERROR_CODE_FAILED_MODEL_CALL,
          "Model call failed with HTTP %d (%s): %s"
              .formatted(
                  awsServiceException.statusCode(), errorCode, awsServiceException.getMessage()),
          awsServiceException);
    }

    final String detail =
        Optional.ofNullable(cause.getMessage())
            .filter(m -> !m.isBlank())
            .orElseGet(() -> cause.getClass().getSimpleName());
    return new ConnectorException(
        ERROR_CODE_FAILED_MODEL_CALL, "Model call failed: %s".formatted(detail), cause);
  }

  @Override
  public void close() {
    try {
      client.close();
    } catch (Exception e) {
      LOG.warn("Failed to close BedrockRuntimeAsyncClient", e);
    }
  }
}
