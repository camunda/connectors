/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.chatmodel.provider.openai;

import static io.camunda.connector.agenticai.aiagent.agent.AgentErrorCodes.ERROR_CODE_FAILED_MODEL_CALL;

import com.openai.client.OpenAIClient;
import com.openai.errors.BadRequestException;
import io.camunda.connector.agenticai.aiagent.chatmodel.ChatModel;
import io.camunda.connector.agenticai.aiagent.chatmodel.ChatModelRejectedException;
import io.camunda.connector.agenticai.aiagent.chatmodel.ChatRequest;
import io.camunda.connector.agenticai.aiagent.chatmodel.ChatResult;
import io.camunda.connector.agenticai.aiagent.chatmodel.ContextWindowExceededException;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.openai.family.OpenAiApiFamilyStrategy;
import io.camunda.connector.agenticai.aiagent.model.request.v2.OpenAiChatModelConfiguration;
import io.camunda.connector.api.error.ConnectorException;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * OpenAI {@link ChatModel}: delegates the actual streaming vendor call to the {@link
 * OpenAiApiFamilyStrategy} selected for the configured {@code configuration.openai().api()} family
 * (Responses or Chat Completions) by the factory, then returns the result the strategy already
 * translated to the domain {@link ChatResult}.
 *
 * <p>The {@link OpenAIClient} is built once by the factory and owned for the lifetime of this
 * instance (one agent request, across all continuation rounds); {@link #close()} closes it once.
 * {@link OpenAIClient#close()} is a plain, unchecked {@code void} method (the vendor SDK's client
 * interface does not implement {@link AutoCloseable}), so it is closed explicitly and guarded.
 */
public class OpenAiChatModel implements ChatModel {

  private static final Logger LOG = LoggerFactory.getLogger(OpenAiChatModel.class);

  // OpenAI's error codes are plain strings, not modeled as an enum by the SDK (com.openai.errors);
  // this is the only one requiring dedicated handling, since it throws a distinct domain exception
  // instead of the generic failed-model-call error code.
  private static final String OPENAI_ERROR_CODE_CONTEXT_LENGTH_EXCEEDED = "context_length_exceeded";

  private final OpenAIClient client;
  private final OpenAiChatModelConfiguration configuration;
  private final OpenAiApiFamilyStrategy strategy;

  public OpenAiChatModel(
      OpenAIClient client,
      OpenAiChatModelConfiguration configuration,
      OpenAiApiFamilyStrategy strategy) {
    this.client = client;
    this.configuration = configuration;
    this.strategy = strategy;
  }

  @Override
  public ChatResult execute(ChatRequest request) {
    try {
      return strategy.call(client, configuration, request);
    } catch (ConnectorException | ChatModelRejectedException e) {
      // already coded -- a ConnectorException thrown by e.g. OpenAiContentConverter's unsupported
      // content type, or a ChatModelRejectedException thrown by the response converter for a
      // known rejection. Let it propagate as-is rather than flattening it into a generic
      // FAILED_MODEL_CALL below.
      throw e;
    } catch (BadRequestException e) {
      // an over-length request is rejected outright with an HTTP 400, on both API families - this
      // error code is the only signal for it. No partial result exists: the request was rejected
      // before any response body could be converted.
      if (OPENAI_ERROR_CODE_CONTEXT_LENGTH_EXCEEDED.equals(e.code().orElse(null))) {
        throw new ContextWindowExceededException(failureMessage(e), e, null);
      }
      throw new ConnectorException(ERROR_CODE_FAILED_MODEL_CALL, failureMessage(e), e);
    } catch (Exception e) {
      throw new ConnectorException(ERROR_CODE_FAILED_MODEL_CALL, failureMessage(e), e);
    }
  }

  private static String failureMessage(Exception e) {
    return "Model call failed: %s"
        .formatted(
            Optional.ofNullable(e.getMessage())
                .filter(m -> !m.isBlank())
                .orElseGet(() -> e.getClass().getSimpleName()));
  }

  @Override
  public void close() {
    try {
      client.close();
    } catch (Exception e) {
      LOG.warn("Failed to close OpenAIClient", e);
    }
  }
}
