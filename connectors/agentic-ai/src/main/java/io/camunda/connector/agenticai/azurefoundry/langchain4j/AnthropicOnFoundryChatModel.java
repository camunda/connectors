/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.azurefoundry.langchain4j;

import com.anthropic.client.AnthropicClient;
import com.anthropic.errors.AnthropicException;
import com.anthropic.errors.AnthropicIoException;
import com.anthropic.errors.BadRequestException;
import com.anthropic.errors.InternalServerException;
import com.anthropic.errors.NotFoundException;
import com.anthropic.errors.PermissionDeniedException;
import com.anthropic.errors.RateLimitException;
import com.anthropic.errors.UnauthorizedException;
import com.anthropic.errors.UnprocessableEntityException;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import io.camunda.connector.agenticai.aiagent.model.request.provider.AzureFoundryProviderConfiguration.AzureAiFoundryModel.AnthropicModel;
import io.camunda.connector.api.error.ConnectorException;
import io.camunda.connector.api.error.ConnectorInputException;
import java.util.Collections;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * langchain4j {@link ChatModel} adapter that wraps an Anthropic SDK {@link AnthropicClient}.
 *
 * <p>Orchestrates the chat call: delegates request translation to {@link
 * AnthropicOnFoundryRequestMapper}, forwards the call to the Anthropic API, delegates response
 * translation to {@link AnthropicOnFoundryResponseMapper}, and translates Anthropic exceptions into
 * connector exceptions.
 *
 * <p>Scope: core chat (system + user/assistant messages), tool use (tool_use + tool_result
 * round-trip), stop-reason mapping, and token usage. Vision, prompt caching, extended thinking, and
 * streaming are deferred to a later milestone.
 */
public class AnthropicOnFoundryChatModel implements ChatModel {

  private static final Logger LOG = LoggerFactory.getLogger(AnthropicOnFoundryChatModel.class);

  private final AnthropicClient client;
  private final AnthropicModel modelConfig;
  private final AnthropicOnFoundryRequestMapper requestMapper;
  private final AnthropicOnFoundryResponseMapper responseMapper;

  public AnthropicOnFoundryChatModel(AnthropicClient client, AnthropicModel modelConfig) {
    this.client = client;
    this.modelConfig = modelConfig;
    this.requestMapper = new AnthropicOnFoundryRequestMapper(modelConfig);
    this.responseMapper = new AnthropicOnFoundryResponseMapper();
  }

  public AnthropicModel modelConfig() {
    return modelConfig;
  }

  @Override
  public ChatResponse doChat(ChatRequest request) {
    LOG.debug(
        "Foundry Anthropic chat: model={} messages={} tools={}",
        modelConfig.deploymentName(),
        request.messages().size(),
        request.toolSpecifications() == null ? 0 : request.toolSpecifications().size());
    try {
      MessageCreateParams params = requestMapper.toMessageCreateParams(request);
      Message response = client.messages().create(params);
      LOG.debug(
          "Foundry Anthropic response: stopReason={} inputTokens={} outputTokens={}",
          response.stopReason().map(Object::toString).orElse("UNKNOWN"),
          response.usage().inputTokens(),
          response.usage().outputTokens());
      return responseMapper.toChatResponse(response);
    } catch (BadRequestException
        | UnauthorizedException
        | PermissionDeniedException
        | NotFoundException
        | UnprocessableEntityException ex) {
      throw new ConnectorInputException(ex);
    } catch (RateLimitException | InternalServerException ex) {
      throw new ConnectorException(String.valueOf(ex.statusCode()), ex.getMessage(), ex);
    } catch (AnthropicIoException ex) {
      throw new ConnectorException("TRANSPORT_ERROR", ex.getMessage(), ex);
    } catch (AnthropicException ex) {
      throw new ConnectorException("ANTHROPIC_ERROR", ex.getMessage(), ex);
    }
  }

  @Override
  public java.util.Set<dev.langchain4j.model.chat.Capability> supportedCapabilities() {
    return Collections.emptySet();
  }
}
