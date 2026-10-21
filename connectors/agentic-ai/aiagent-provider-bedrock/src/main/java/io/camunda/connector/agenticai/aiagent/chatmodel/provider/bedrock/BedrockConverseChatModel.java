/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.chatmodel.provider.bedrock;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.agenticai.aiagent.api.chatmodel.ChatModel;
import io.camunda.connector.agenticai.aiagent.api.chatmodel.ChatRequest;
import io.camunda.connector.agenticai.aiagent.api.chatmodel.ChatResult;
import io.camunda.connector.agenticai.aiagent.chatmodel.wire.LoggingSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
  private final BedrockConfiguration configuration;
  private final BedrockConverseRequestConverter requestConverter;
  private final BedrockConverseResponseConverter responseConverter;
  private final ObjectMapper objectMapper;

  public BedrockConverseChatModel(
      BedrockRuntimeAsyncClient client,
      BedrockConfiguration configuration,
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

    final BedrockConverseStreamAssembler streamAssembler =
        new BedrockConverseStreamAssembler(objectMapper);
    final ConverseStreamResponseHandler handler =
        ConverseStreamResponseHandler.builder().subscriber(streamAssembler).build();
    client.converseStream(converseStreamRequest, handler).join();

    final ConverseResponse response = streamAssembler.converseResponse();
    logTrace("response", response);
    return responseConverter.toResult(response);
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

  @Override
  public void close() {
    try {
      client.close();
    } catch (Exception e) {
      LOG.error("Failed to close BedrockRuntimeAsyncClient", e);
    }
  }
}
