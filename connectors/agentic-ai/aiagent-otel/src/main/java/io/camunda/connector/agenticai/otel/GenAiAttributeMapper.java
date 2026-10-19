/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.otel;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.camunda.connector.agenticai.aiagent.api.lifecycle.AgentJobInfo;
import io.camunda.connector.agenticai.aiagent.model.AgentMetrics.TokenUsage;
import io.camunda.connector.agenticai.aiagent.model.chatmodel.ChatModelConfiguration;
import io.camunda.connector.agenticai.aiagent.model.message.AssistantMessage;
import io.camunda.connector.agenticai.aiagent.model.message.ContentMessage;
import io.camunda.connector.agenticai.aiagent.model.message.Message;
import io.camunda.connector.agenticai.aiagent.model.message.StopReason;
import io.camunda.connector.agenticai.aiagent.model.message.StopReason.KnownStopReason;
import io.camunda.connector.agenticai.aiagent.model.message.StopReason.UnknownStopReason;
import io.camunda.connector.agenticai.aiagent.model.message.SystemMessage;
import io.camunda.connector.agenticai.aiagent.model.message.ToolCallResultMessage;
import io.camunda.connector.agenticai.aiagent.model.message.UserMessage;
import io.camunda.connector.agenticai.aiagent.model.message.content.Content;
import io.camunda.connector.agenticai.aiagent.model.message.content.DocumentContent;
import io.camunda.connector.agenticai.aiagent.model.message.content.ObjectContent;
import io.camunda.connector.agenticai.aiagent.model.message.content.ProviderContent;
import io.camunda.connector.agenticai.aiagent.model.message.content.ReasoningContent;
import io.camunda.connector.agenticai.aiagent.model.message.content.TextContent;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCall;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCallResultContent;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolDefinition;
import io.camunda.connector.api.document.Document;
import io.camunda.connector.api.document.DocumentReference.CamundaDocumentReference;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.jspecify.annotations.Nullable;

/**
 * Turns the data of the lifecycle events into span attributes. It is the only class that knows how
 * the provider-neutral AI Agent model maps onto the GenAI semantic conventions. Content attributes
 * (messages, instructions, tool definitions, tool arguments and results) are produced only when
 * capture is requested.
 */
public final class GenAiAttributeMapper {

  private static final ObjectMapper JSON = new ObjectMapper();
  private static final JsonNodeFactory NODES = JSON.getNodeFactory();

  private GenAiAttributeMapper() {}

  /** Identity attributes carried by every span, including the session of the conversation. */
  public static Attributes identityAttributes(
      AgentJobInfo job, @Nullable Long agentInstanceKey, String sessionId) {
    final AttributesBuilder builder = Attributes.builder();
    builder.put(GenAiAttributes.SESSION_ID, sessionId);
    builder.put(GenAiAttributes.CONVERSATION_ID, sessionId);
    builder.put(GenAiAttributes.PROCESS_INSTANCE_KEY, Long.toString(job.processInstanceKey()));
    builder.put(GenAiAttributes.ELEMENT_INSTANCE_KEY, Long.toString(job.elementInstanceKey()));
    builder.put(GenAiAttributes.ELEMENT_ID, job.elementId());
    builder.put(GenAiAttributes.BPMN_PROCESS_ID, job.bpmnProcessId());
    builder.put(GenAiAttributes.TENANT_ID, job.tenantId());
    if (agentInstanceKey != null) {
      builder.put(GenAiAttributes.AGENT_INSTANCE_KEY, Long.toString(agentInstanceKey));
    }
    return builder.build();
  }

  /** The well-known {@code gen_ai.provider.name} of a chat model configuration. */
  public static String providerName(ChatModelConfiguration chatModel) {
    final String provider = chatModel.provider();
    final String descriptive = chatModel.descriptiveProvider().toLowerCase(Locale.ROOT);
    return switch (provider) {
      case "anthropic" -> "anthropic";
      case "openai", "openaiCompatible" ->
          descriptive.endsWith("/foundry") ? "azure.ai.openai" : "openai";
      case "bedrock", "aws-bedrock", "aws" -> "aws.bedrock";
      case "google-gemini" -> descriptive.contains("vertex") ? "gcp.vertex_ai" : "gcp.gemini";
      case "google-vertex-ai" -> "gcp.vertex_ai";
      case "mistral" -> "mistral_ai";
      case "azureOpenAi" -> "azure.ai.openai";
      default -> provider;
    };
  }

  /** The normalized {@code gen_ai.response.finish_reasons} value of a stop reason. */
  public static String finishReason(StopReason stopReason) {
    return switch (stopReason) {
      case KnownStopReason known ->
          switch (known) {
            case STOP -> "stop";
            case LENGTH -> "length";
            case TOOL_USE -> "tool_call";
          };
      case UnknownStopReason unknown -> unknown.value();
    };
  }

  /**
   * Token usage following the semantic conventions: the input total includes the cached tokens, the
   * cache and reasoning counts are reported as subsets. The model reports new input tokens apart
   * from its cache read and cache creation buckets.
   */
  public static Attributes usageAttributes(TokenUsage usage) {
    final long inputTotal =
        (long) usage.inputTokenCount()
            + usage.cacheReadTokenCount()
            + usage.cacheCreationTokenCount();
    final AttributesBuilder builder = Attributes.builder();
    builder.put(GenAiAttributes.USAGE_INPUT_TOKENS, inputTotal);
    builder.put(GenAiAttributes.USAGE_OUTPUT_TOKENS, (long) usage.outputTokenCount());
    if (usage.cacheReadTokenCount() > 0) {
      builder.put(
          GenAiAttributes.USAGE_CACHE_READ_INPUT_TOKENS, (long) usage.cacheReadTokenCount());
    }
    if (usage.cacheCreationTokenCount() > 0) {
      builder.put(
          GenAiAttributes.USAGE_CACHE_CREATION_INPUT_TOKENS,
          (long) usage.cacheCreationTokenCount());
    }
    if (usage.reasoningTokenCount() > 0) {
      builder.put(
          GenAiAttributes.USAGE_REASONING_OUTPUT_TOKENS, (long) usage.reasoningTokenCount());
    }
    return builder.build();
  }

  /** Request-side attributes of a {@code chat} span. */
  public static Attributes chatRequestAttributes(
      ChatModelConfiguration chatModel,
      List<Message> messages,
      List<ToolDefinition> toolDefinitions,
      boolean captureContent) {
    final AttributesBuilder builder = Attributes.builder();
    builder.put(GenAiAttributes.OPERATION_NAME, GenAiAttributes.OPERATION_CHAT);
    builder.put(GenAiAttributes.PROVIDER_NAME, providerName(chatModel));
    builder.put(GenAiAttributes.REQUEST_MODEL, chatModel.model());
    final var parameters = chatModel.requestParameters();
    if (parameters.maxTokens() != null) {
      builder.put(GenAiAttributes.REQUEST_MAX_TOKENS, parameters.maxTokens());
    }
    if (parameters.temperature() != null) {
      builder.put(GenAiAttributes.REQUEST_TEMPERATURE, parameters.temperature());
    }
    if (parameters.topP() != null) {
      builder.put(GenAiAttributes.REQUEST_TOP_P, parameters.topP());
    }
    if (parameters.topK() != null) {
      builder.put(GenAiAttributes.REQUEST_TOP_K, parameters.topK().doubleValue());
    }
    if (captureContent) {
      final ArrayNode instructions = NODES.arrayNode();
      final List<Message> conversation = new ArrayList<>();
      for (final Message message : messages) {
        if (message instanceof SystemMessage system) {
          addTextParts(instructions, system.content());
        } else {
          conversation.add(message);
        }
      }
      if (!instructions.isEmpty()) {
        builder.put(GenAiAttributes.SYSTEM_INSTRUCTIONS, instructions.toString());
      }
      builder.put(GenAiAttributes.INPUT_MESSAGES, messagesJson(conversation));
      if (!toolDefinitions.isEmpty()) {
        builder.put(GenAiAttributes.TOOL_DEFINITIONS, toolDefinitionsJson(toolDefinitions));
      }
    }
    return builder.build();
  }

  /** Response-side attributes of a {@code chat} span. */
  public static Attributes chatResponseAttributes(
      AssistantMessage message, TokenUsage usage, boolean captureContent) {
    final AttributesBuilder builder = Attributes.builder();
    if (message.modelId() != null) {
      builder.put(GenAiAttributes.RESPONSE_MODEL, message.modelId());
    }
    if (message.messageId() != null) {
      builder.put(GenAiAttributes.RESPONSE_ID, message.messageId());
    }
    final StopReason stopReason = message.stopReason();
    if (stopReason != null) {
      builder.put(GenAiAttributes.RESPONSE_FINISH_REASONS, List.of(finishReason(stopReason)));
    }
    builder.putAll(usageAttributes(usage));
    if (captureContent) {
      final ObjectNode output = assistantMessageNode(message);
      if (stopReason != null) {
        output.put("finish_reason", finishReason(stopReason));
      }
      builder.put(GenAiAttributes.OUTPUT_MESSAGES, NODES.arrayNode().add(output).toString());
    }
    return builder.build();
  }

  /** Attributes of an {@code execute_tool} span that identify the tool call. */
  public static Attributes toolIdentityAttributes(
      @Nullable ToolCall call, ToolCallResultContent result) {
    final AttributesBuilder builder = Attributes.builder();
    builder.put(GenAiAttributes.OPERATION_NAME, GenAiAttributes.OPERATION_EXECUTE_TOOL);
    builder.put(GenAiAttributes.TOOL_TYPE, GenAiAttributes.TOOL_TYPE_FUNCTION);
    final String name = call != null ? call.name() : result.name();
    if (name != null) {
      builder.put(GenAiAttributes.TOOL_NAME, name);
    }
    final String id = call != null ? call.id() : result.id();
    if (id != null) {
      builder.put(GenAiAttributes.TOOL_CALL_ID, id);
    }
    if (result.elementId() != null) {
      builder.put(GenAiAttributes.ELEMENT_ID, result.elementId());
    }
    return builder.build();
  }

  /** Arguments and result of an {@code execute_tool} span, only with content capture. */
  public static Attributes toolContentAttributes(
      @Nullable ToolCall call, ToolCallResultContent result, boolean captureContent) {
    if (!captureContent) {
      return Attributes.empty();
    }
    final AttributesBuilder builder = Attributes.builder();
    if (call != null) {
      builder.put(GenAiAttributes.TOOL_CALL_ARGUMENTS, toJson(call.arguments()).toString());
    }
    builder.put(GenAiAttributes.TOOL_CALL_RESULT, toolResultNode(result.content()).toString());
    return builder.build();
  }

  /** The text of the last user message, which triggers a run. Null when there is none. */
  public static @Nullable String runInput(List<Message> inputMessages) {
    for (int i = inputMessages.size() - 1; i >= 0; i--) {
      if (inputMessages.get(i) instanceof UserMessage user) {
        final StringBuilder text = new StringBuilder();
        for (final Content content : user.content()) {
          if (content instanceof TextContent textContent) {
            if (!text.isEmpty()) {
              text.append('\n');
            }
            text.append(textContent.text());
          }
        }
        return text.isEmpty() ? null : text.toString();
      }
    }
    return null;
  }

  // --- JSON in the shape of the semantic conventions' message schema ---

  private static String messagesJson(List<Message> messages) {
    final ArrayNode array = NODES.arrayNode();
    for (final Message message : messages) {
      switch (message) {
        case UserMessage user -> array.add(contentMessageNode("user", user));
        case AssistantMessage assistant -> array.add(assistantMessageNode(assistant));
        case SystemMessage system -> array.add(contentMessageNode("system", system));
        case ToolCallResultMessage toolResults -> array.add(toolMessageNode(toolResults));
        default -> {}
      }
    }
    return array.toString();
  }

  private static ObjectNode contentMessageNode(String role, ContentMessage message) {
    final ObjectNode node = NODES.objectNode().put("role", role);
    final ArrayNode parts = node.putArray("parts");
    for (final Content content : message.content()) {
      addPart(parts, content);
    }
    return node;
  }

  private static ObjectNode assistantMessageNode(AssistantMessage message) {
    final ObjectNode node = contentMessageNode("assistant", message);
    final ArrayNode parts = (ArrayNode) node.get("parts");
    if (message.hasToolCalls()) {
      for (final ToolCall call : message.toolCalls()) {
        final ObjectNode part =
            NODES
                .objectNode()
                .put("type", "tool_call")
                .put("id", call.id())
                .put("name", call.name());
        part.set("arguments", toJson(call.arguments()));
        parts.add(part);
      }
    }
    return node;
  }

  private static ObjectNode toolMessageNode(ToolCallResultMessage message) {
    final ObjectNode node = NODES.objectNode().put("role", "tool");
    final ArrayNode parts = node.putArray("parts");
    for (final ToolCallResultContent result : message.results()) {
      final ObjectNode part = NODES.objectNode().put("type", "tool_call_response");
      if (result.id() != null) {
        part.put("id", result.id());
      }
      part.set("response", toolResultNode(result.content()));
      parts.add(part);
    }
    return node;
  }

  private static String toolDefinitionsJson(List<ToolDefinition> toolDefinitions) {
    final ArrayNode array = NODES.arrayNode();
    for (final ToolDefinition definition : toolDefinitions) {
      final ObjectNode node =
          NODES.objectNode().put("type", "function").put("name", definition.name());
      if (definition.description() != null) {
        node.put("description", definition.description());
      }
      node.set("parameters", toJson(definition.inputSchema()));
      array.add(node);
    }
    return array.toString();
  }

  private static void addTextParts(ArrayNode parts, List<Content> contents) {
    for (final Content content : contents) {
      addPart(parts, content);
    }
  }

  private static void addPart(ArrayNode parts, Content content) {
    switch (content) {
      case TextContent text -> parts.add(textPart(text.text()));
      case ReasoningContent reasoning -> {
        if (reasoning.text() != null) {
          parts.add(NODES.objectNode().put("type", "reasoning").put("content", reasoning.text()));
        }
      }
      case DocumentContent document -> parts.add(textPart(documentReference(document.document())));
      case ObjectContent object -> parts.add(textPart(toJson(object.content()).toString()));
      case ProviderContent provider ->
          parts.add(
              textPart(
                  provider.text() != null
                      ? provider.text()
                      : "[provider content: " + provider.provider() + "]"));
    }
  }

  /** A tool result as a JSON value: the single content item, or an array of several. */
  private static JsonNode toolResultNode(List<Content> contents) {
    final List<JsonNode> nodes = new ArrayList<>();
    for (final Content content : contents) {
      switch (content) {
        case TextContent text -> nodes.add(NODES.textNode(text.text()));
        case ObjectContent object -> nodes.add(toJson(object.content()));
        case DocumentContent document ->
            nodes.add(NODES.textNode(documentReference(document.document())));
        case ReasoningContent ignored -> nodes.add(NODES.textNode("[reasoning]"));
        case ProviderContent provider ->
            nodes.add(NODES.textNode("[provider content: " + provider.provider() + "]"));
      }
    }
    if (nodes.size() == 1) {
      return nodes.get(0);
    }
    return NODES.arrayNode().addAll(nodes);
  }

  private static ObjectNode textPart(String text) {
    return NODES.objectNode().put("type", "text").put("content", text);
  }

  /** A reference to a Camunda document with its metadata, never its bytes. */
  private static String documentReference(Document document) {
    final List<String> details = new ArrayList<>();
    if (document.reference() instanceof CamundaDocumentReference reference) {
      details.add("id=" + reference.getDocumentId());
    }
    final var metadata = document.metadata();
    if (metadata != null) {
      if (metadata.getFileName() != null) {
        details.add("name=" + metadata.getFileName());
      }
      if (metadata.getContentType() != null) {
        details.add("contentType=" + metadata.getContentType());
      }
    }
    return "[document " + String.join(" ", details) + "]";
  }

  private static JsonNode toJson(@Nullable Object value) {
    try {
      return JSON.valueToTree(value);
    } catch (RuntimeException e) {
      return NODES.textNode(String.valueOf(value));
    }
  }
}
