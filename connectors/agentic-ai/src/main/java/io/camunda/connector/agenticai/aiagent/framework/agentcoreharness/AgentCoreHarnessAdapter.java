/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.framework.agentcoreharness;

import static io.camunda.connector.agenticai.aiagent.agent.AgentErrorCodes.ERROR_CODE_FAILED_MODEL_CALL;

import io.camunda.connector.agenticai.aiagent.framework.AiFrameworkAdapter;
import io.camunda.connector.agenticai.aiagent.memory.runtime.RuntimeMemory;
import io.camunda.connector.agenticai.aiagent.model.AgentContext;
import io.camunda.connector.agenticai.aiagent.model.AgentExecutionContext;
import io.camunda.connector.agenticai.aiagent.model.AgentMetrics;
import io.camunda.connector.agenticai.model.message.AssistantMessage;
import io.camunda.connector.agenticai.model.message.content.TextContent;
import io.camunda.connector.agenticai.model.tool.ToolCall;
import io.camunda.connector.api.error.ConnectorException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.bedrockagentcore.BedrockAgentCoreAsyncClient;
import software.amazon.awssdk.services.bedrockagentcore.model.HarnessContentBlock;
import software.amazon.awssdk.services.bedrockagentcore.model.InvokeHarnessRequest;
import software.amazon.awssdk.services.bedrockagentcore.model.InvokeHarnessResponseHandler;

/**
 * AI Framework adapter for AWS Bedrock AgentCore Harness.
 *
 * <p>This adapter calls the InvokeHarness API with inline_function tools, allowing Harness to
 * return tool calls back to Camunda for BPMN element activation.
 */
public class AgentCoreHarnessAdapter
    implements AiFrameworkAdapter<AgentCoreHarnessAiFrameworkChatResponse> {

  private static final Logger LOGGER = LoggerFactory.getLogger(AgentCoreHarnessAdapter.class);

  private static final String SESSION_ID_PROPERTY = "harnessSessionId";

  private final BedrockAgentCoreAsyncClient client;
  private final HarnessMessageConverter messageConverter;
  private final HarnessToolConverter toolConverter;
  private final String harnessArn;

  public AgentCoreHarnessAdapter(
      BedrockAgentCoreAsyncClient client,
      HarnessMessageConverter messageConverter,
      HarnessToolConverter toolConverter,
      String harnessArn) {
    this.client = client;
    this.messageConverter = messageConverter;
    this.toolConverter = toolConverter;
    this.harnessArn = harnessArn;
  }

  @Override
  public AgentCoreHarnessAiFrameworkChatResponse executeChatRequest(
      AgentExecutionContext executionContext,
      AgentContext agentContext,
      RuntimeMemory runtimeMemory) {

    var messages = runtimeMemory.filteredMessages();
    var systemPrompt = messageConverter.extractSystemPrompt(messages);
    var harnessMessages = messageConverter.toHarnessMessages(messages);
    var harnessTools = toolConverter.toHarnessTools(agentContext.toolDefinitions());

    // Get or generate session ID for conversation continuity
    String sessionId = getOrCreateSessionId(agentContext);

    var requestBuilder =
        InvokeHarnessRequest.builder()
            .harnessArn(harnessArn)
            .runtimeSessionId(sessionId)
            .messages(harnessMessages)
            .tools(harnessTools);

    if (!systemPrompt.isEmpty()) {
      requestBuilder.systemPrompt(systemPrompt);
    }

    var request = requestBuilder.build();

    LOGGER.debug(
        "Invoking Harness {} with {} messages and {} tools",
        harnessArn,
        harnessMessages.size(),
        harnessTools.size());

    // Process streaming response
    var responseData = invokeHarness(request);

    // Build assistant message from response
    var assistantMessage = buildAssistantMessage(responseData);

    LOGGER.debug(
        "Received response with {} tool calls",
        assistantMessage.toolCalls() != null ? assistantMessage.toolCalls().size() : 0);

    // Update metrics
    var updatedAgentContext =
        agentContext
            .withMetrics(
                agentContext
                    .metrics()
                    .incrementModelCalls(1)
                    .incrementTokenUsage(
                        AgentMetrics.TokenUsage.builder()
                            .inputTokenCount(responseData.inputTokens())
                            .outputTokenCount(responseData.outputTokens())
                            .build()))
            .withProperty(SESSION_ID_PROPERTY, sessionId);

    return new AgentCoreHarnessAiFrameworkChatResponse(
        updatedAgentContext, assistantMessage, sessionId);
  }

  private String getOrCreateSessionId(AgentContext agentContext) {
    return Optional.ofNullable(agentContext.properties())
        .map(props -> props.get(SESSION_ID_PROPERTY))
        .map(Object::toString)
        .filter(StringUtils::isNotBlank)
        .orElseGet(() -> java.util.UUID.randomUUID().toString());
  }

  private HarnessResponseData invokeHarness(InvokeHarnessRequest request) {
    var responseData = new HarnessResponseData();
    var future = new CompletableFuture<Void>();

    var handler =
        InvokeHarnessResponseHandler.builder()
            .onEventStream(
                publisher ->
                    publisher.subscribe(
                        event -> {
                          event.accept(
                              InvokeHarnessResponseHandler.Visitor.builder()
                                  .onContentBlockStart(
                                      e -> {
                                        if (e.start() != null && e.start().toolUse() != null) {
                                          responseData.startToolUse(e.start().toolUse());
                                        }
                                      })
                                  .onContentBlockDelta(
                                      e -> {
                                        if (e.delta() != null) {
                                          if (e.delta().text() != null) {
                                            responseData.appendText(e.delta().text());
                                          }
                                          if (e.delta().toolUse() != null
                                              && e.delta().toolUse().input() != null) {
                                            responseData.appendToolInput(
                                                e.delta().toolUse().input());
                                          }
                                        }
                                      })
                                  .onContentBlockStop(e -> responseData.finishCurrentToolUse())
                                  .onMetadata(
                                      e -> {
                                        if (e.usage() != null) {
                                          responseData.setTokenUsage(
                                              e.usage().inputTokens(), e.usage().outputTokens());
                                        }
                                      })
                                  .build());
                        }))
            .onError(future::completeExceptionally)
            .onComplete(() -> future.complete(null))
            .build();

    try {
      client.invokeHarness(request, handler).join();
      future.join();
    } catch (Exception e) {
      var message =
          Optional.ofNullable(e.getMessage())
              .filter(StringUtils::isNotBlank)
              .orElseGet(() -> e.getClass().getSimpleName());

      throw new ConnectorException(
          ERROR_CODE_FAILED_MODEL_CALL, "Harness invocation failed: %s".formatted(message), e);
    }

    return responseData;
  }

  private AssistantMessage buildAssistantMessage(HarnessResponseData responseData) {
    var builder = AssistantMessage.builder();

    if (StringUtils.isNotBlank(responseData.getText())) {
      builder.content(List.of(TextContent.textContent(responseData.getText())));
    }

    if (!responseData.getToolCalls().isEmpty()) {
      builder.toolCalls(responseData.getToolCalls());
    }

    return builder.build();
  }

  /** Accumulates data from streaming Harness response events. */
  private class HarnessResponseData {
    private final StringBuilder textBuilder = new StringBuilder();
    private final List<ToolCall> toolCalls = new ArrayList<>();
    private final AtomicReference<String> currentToolUseId = new AtomicReference<>();
    private final AtomicReference<String> currentToolName = new AtomicReference<>();
    private final StringBuilder currentToolInputBuilder = new StringBuilder();
    private int inputTokens = 0;
    private int outputTokens = 0;

    void appendText(String text) {
      textBuilder.append(text);
    }

    void startToolUse(HarnessContentBlock.Builder toolUseBuilder) {
      // This is called with partial tool use info at start
    }

    void startToolUse(
        software.amazon.awssdk.services.bedrockagentcore.model.HarnessToolUseBlockStart toolUse) {
      currentToolUseId.set(toolUse.toolUseId());
      currentToolName.set(toolUse.name());
      currentToolInputBuilder.setLength(0);
    }

    void appendToolInput(String input) {
      currentToolInputBuilder.append(input);
    }

    void finishCurrentToolUse() {
      if (currentToolUseId.get() != null && currentToolName.get() != null) {
        var inputJson = currentToolInputBuilder.toString();
        var arguments = parseToolInput(inputJson);

        toolCalls.add(
            ToolCall.builder()
                .id(currentToolUseId.get())
                .name(currentToolName.get())
                .arguments(arguments)
                .build());

        currentToolUseId.set(null);
        currentToolName.set(null);
        currentToolInputBuilder.setLength(0);
      }
    }

    @SuppressWarnings("unchecked")
    private java.util.Map<String, Object> parseToolInput(String inputJson) {
      if (StringUtils.isBlank(inputJson)) {
        return java.util.Map.of();
      }
      try {
        return new com.fasterxml.jackson.databind.ObjectMapper()
            .readValue(inputJson, java.util.Map.class);
      } catch (Exception e) {
        LOGGER.warn("Failed to parse tool input JSON: {}", inputJson, e);
        return java.util.Map.of();
      }
    }

    void setTokenUsage(Integer input, Integer output) {
      this.inputTokens = input != null ? input : 0;
      this.outputTokens = output != null ? output : 0;
    }

    String getText() {
      return textBuilder.toString();
    }

    List<ToolCall> getToolCalls() {
      return toolCalls;
    }

    int inputTokens() {
      return inputTokens;
    }

    int outputTokens() {
      return outputTokens;
    }
  }
}
