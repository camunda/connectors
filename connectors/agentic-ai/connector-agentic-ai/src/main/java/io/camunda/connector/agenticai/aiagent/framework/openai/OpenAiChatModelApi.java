/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.framework.openai;

import static io.camunda.connector.agenticai.aiagent.agent.AgentErrorCodes.ERROR_CODE_FAILED_MODEL_CALL;

import com.openai.client.OpenAIClient;
import io.camunda.connector.agenticai.aiagent.framework.api.ChatModelApi;
import io.camunda.connector.agenticai.aiagent.framework.api.ChatModelRequest;
import io.camunda.connector.agenticai.aiagent.framework.api.ChatModelResult;
import io.camunda.connector.agenticai.aiagent.framework.capabilities.ModelCapabilities;
import io.camunda.connector.agenticai.aiagent.framework.openai.family.OpenAiApiFamilyStrategy;
import io.camunda.connector.api.error.ConnectorException;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Native OpenAI {@link ChatModelApi}: builds a fresh vendor {@link OpenAIClient} per call and
 * delegates the actual streaming vendor call to the configured {@link OpenAiApiFamilyStrategy}
 * (Responses or Chat Completions), then translates the result via that strategy's converters.
 *
 * <p>{@link OpenAIClient#close()} is a plain, unchecked {@code void} method (the vendor SDK's
 * client interface does not implement {@link AutoCloseable}), so it is closed explicitly in a
 * {@code finally} block, mirroring the Anthropic sibling's handling of {@code AnthropicClient}.
 */
public class OpenAiChatModelApi implements ChatModelApi {

  private static final Logger LOG = LoggerFactory.getLogger(OpenAiChatModelApi.class);

  private final OpenAiClientFactory clientFactory;
  private final OpenAiApiFamilyStrategy strategy;
  private final OpenAiModelCapabilities capabilities;

  /**
   * Whether the configured model matched a capability matrix entry (see {@link
   * io.camunda.connector.agenticai.aiagent.framework.capabilities.ModelCapabilitiesResolver#matches}),
   * as opposed to falling through to family/conservative defaults. Threaded into the request
   * converter (via the family strategy) so {@link OpenAiRequestValidator} can distinguish "declared
   * but not reasoning-capable" (validate) from "unknown/custom model" (pass-through).
   */
  private final boolean modelMatched;

  public OpenAiChatModelApi(
      OpenAiClientFactory clientFactory,
      OpenAiApiFamilyStrategy strategy,
      OpenAiModelCapabilities capabilities,
      boolean modelMatched) {
    this.clientFactory = clientFactory;
    this.strategy = strategy;
    this.capabilities = capabilities;
    this.modelMatched = modelMatched;
  }

  @Override
  public ChatModelResult call(ChatModelRequest request) {
    final OpenAIClient client = clientFactory.create();
    try {
      return strategy.call(client, request, capabilities, modelMatched);
    } catch (ConnectorException e) {
      // The family strategy builds its request params (and thus runs OpenAiRequestValidator)
      // inside strategy.call(), unlike the Anthropic sibling, which builds params outside its
      // try block. Re-throw validation/already-coded ConnectorExceptions verbatim so they are not
      // double-wrapped as a generic "Model call failed" below.
      throw e;
    } catch (Exception e) {
      final String detail =
          Optional.ofNullable(e.getMessage())
              .filter(m -> !m.isBlank())
              .orElseGet(() -> e.getClass().getSimpleName());
      throw new ConnectorException(
          ERROR_CODE_FAILED_MODEL_CALL, "Model call failed: %s".formatted(detail), e);
    } finally {
      try {
        client.close();
      } catch (Exception closeException) {
        LOG.warn("Failed to close OpenAIClient", closeException);
      }
    }
  }

  @Override
  public ModelCapabilities capabilities() {
    return capabilities;
  }
}
