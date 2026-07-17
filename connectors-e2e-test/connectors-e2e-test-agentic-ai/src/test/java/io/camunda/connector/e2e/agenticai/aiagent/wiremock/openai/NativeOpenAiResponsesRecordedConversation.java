/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information regarding copyright
 * ownership. Camunda licenses this file to you under the Apache License,
 * Version 2.0; you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.camunda.connector.e2e.agenticai.aiagent.wiremock.openai;

import static com.github.tomakehurst.wiremock.client.WireMock.findAll;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolDefinition;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Inspects the OpenAI Responses API requests the connector actually sent to WireMock ({@code POST
 * /v1/responses}), parsing the Responses-specific wire shape ({@code instructions}/{@code
 * input[]}/{@code tools[]}/{@code text.format}) — structurally different from Chat Completions'
 * {@code messages[]}/{@code tools[]}/{@code response_format}, so this mirrors {@link
 * OpenAiCompletionsRecordedConversation} rather than reusing it.
 *
 * <p>Each model call produces one recorded request. Unlike Chat Completions (whose {@code messages}
 * array already carries one assistant message object per turn, text and tool calls together), the
 * Responses {@code input[]} array splits a single assistant turn into separate items — an optional
 * {@code message} item for any plain text, followed by one {@code function_call} item per tool
 * call. {@link RecordedChatRequest#parse} regroups a message item and any {@code function_call}
 * items that immediately follow it back into a single assistant {@link RecordedMessage}, matching
 * the one-message-per-turn shape every other provider's wire format already produces and the shared
 * {@code ProviderWireFormatSmokeTests} assertions expect — this is a faithful reconstruction, not a
 * lossy simplification: {@code OpenAiResponsesRequestConverter#assistantInputItems} always emits
 * exactly this contiguous item sequence for one domain {@code AssistantMessage}.
 */
public final class NativeOpenAiResponsesRecordedConversation {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  private final List<RecordedChatRequest> requests;

  private NativeOpenAiResponsesRecordedConversation(List<RecordedChatRequest> requests) {
    this.requests = requests;
  }

  /** Reads and parses all recorded {@code POST /v1/responses} requests, oldest first. */
  public static NativeOpenAiResponsesRecordedConversation recorded() {
    return recorded(NativeOpenAiResponsesSseChatModelStubs.RESPONSES_PATH);
  }

  /** Reads and parses all recorded Responses requests at the given path, oldest first. */
  public static NativeOpenAiResponsesRecordedConversation recorded(String path) {
    final List<LoggedRequest> loggedRequests =
        new ArrayList<>(findAll(postRequestedFor(urlPathEqualTo(path))));

    // WireMock does not guarantee ordering across versions; sort chronologically
    loggedRequests.sort(Comparator.comparing(LoggedRequest::getLoggedDate));

    final List<RecordedChatRequest> parsed =
        loggedRequests.stream()
            .map(LoggedRequest::getBodyAsString)
            .map(RecordedChatRequest::parse)
            .toList();

    return new NativeOpenAiResponsesRecordedConversation(parsed);
  }

  /** Number of model calls. */
  public int modelCallCount() {
    return requests.size();
  }

  public List<RecordedChatRequest> requests() {
    return requests;
  }

  /** The most recent request. */
  public RecordedChatRequest lastRequest() {
    if (requests.isEmpty()) {
      throw new IllegalStateException("No Responses requests were recorded");
    }
    return requests.getLast();
  }

  /**
   * A parsed message reconstructed from the {@code instructions} field or the {@code input[]} array
   * of a Responses request.
   *
   * <p>{@code content} is non-null when the message carries a plain text string (the synthetic
   * system message hoisted from {@code instructions}, or a {@code function_call_output}'s {@code
   * output}). {@code contentParts} is non-empty for {@code message} items, carrying their raw
   * {@code content[]} array (e.g. {@code input_text}/{@code input_image}/{@code input_file} parts).
   */
  public record RecordedMessage(
      String role,
      String content,
      List<JsonNode> contentParts,
      String toolCallId,
      List<RecordedToolCall> toolCalls) {

    public record RecordedToolCall(String id, String name, String argumentsJson) {}
  }

  /** The parsed {@code text.format} object from a Responses request. */
  public record RecordedResponseFormat(
      String type, String schemaName, Map<String, Object> jsonSchema) {}

  /** A single parsed OpenAI Responses request body. */
  public static final class RecordedChatRequest {

    static RecordedChatRequest parse(String rawBody) {
      try {
        final var wireBody = OBJECT_MAPPER.readValue(rawBody, RequestBodyWire.class);

        final var messages = parseMessages(wireBody.instructions(), wireBody.input());
        final var toolDefinitions =
            wireBody.tools().stream()
                .filter(tool -> tool.type() == null || "function".equals(tool.type()))
                .map(ToolWire::toToolDefinition)
                .toList();
        final var responseFormat =
            Optional.ofNullable(wireBody.text())
                .map(TextWire::format)
                .map(ResponseFormatWire::toResponseFormat)
                .orElse(null);
        final var reasoningEffort =
            Optional.ofNullable(wireBody.reasoning()).map(ReasoningWire::effort).orElse(null);

        return new RecordedChatRequest(
            messages,
            toolDefinitions,
            responseFormat,
            reasoningEffort,
            wireBody.include(),
            wireBody.input());
      } catch (Exception e) {
        throw new IllegalStateException(
            "Failed to parse recorded Responses request body: " + rawBody, e);
      }
    }

    /**
     * Reconstructs {@link RecordedMessage}s from the top-level {@code instructions} string (a
     * synthetic leading system message, so the shared {@code
     * ProviderWireFormatExpectedMessage#system} assertion works the same way as every other
     * provider's fixture) and the {@code input[]} array, regrouping a {@code message} item and any
     * immediately-following {@code function_call} items into a single assistant message (see the
     * class-level Javadoc). Item kinds with no provider-neutral representation yet ({@code
     * reasoning}, {@code item_reference}, server-tool items, ...) are skipped rather than raising -
     * this parser only needs to support the shapes the four wire-format smoke scenarios exercise;
     * extending it for those kinds is Task 4's job.
     */
    private static List<RecordedMessage> parseMessages(String instructions, List<JsonNode> input) {
      final List<RecordedMessage> result = new ArrayList<>();
      if (instructions != null && !instructions.isBlank()) {
        result.add(new RecordedMessage("system", instructions, List.of(), null, List.of()));
      }

      boolean hasPendingAssistantGroup = false;
      String pendingContent = null;
      List<JsonNode> pendingParts = List.of();
      List<RecordedMessage.RecordedToolCall> pendingToolCalls = new ArrayList<>();

      for (final JsonNode item : input) {
        if (item.has("role")) {
          if (hasPendingAssistantGroup) {
            result.add(
                new RecordedMessage(
                    "assistant",
                    pendingContent,
                    pendingParts,
                    null,
                    List.copyOf(pendingToolCalls)));
            hasPendingAssistantGroup = false;
            pendingToolCalls = new ArrayList<>();
          }

          final String role = item.path("role").asText();
          final ParsedContent parsed = parseContent(item.path("content"));
          if ("assistant".equals(role)) {
            hasPendingAssistantGroup = true;
            pendingContent = parsed.content();
            pendingParts = parsed.parts();
          } else {
            result.add(
                new RecordedMessage(role, parsed.content(), parsed.parts(), null, List.of()));
          }
          continue;
        }

        final String type = item.path("type").asText();
        switch (type) {
          case "function_call" -> {
            if (!hasPendingAssistantGroup) {
              hasPendingAssistantGroup = true;
              pendingContent = null;
              pendingParts = List.of();
            }
            pendingToolCalls.add(
                new RecordedMessage.RecordedToolCall(
                    item.path("call_id").asText(),
                    item.path("name").asText(),
                    item.path("arguments").asText()));
          }
          case "function_call_output" -> {
            if (hasPendingAssistantGroup) {
              result.add(
                  new RecordedMessage(
                      "assistant",
                      pendingContent,
                      pendingParts,
                      null,
                      List.copyOf(pendingToolCalls)));
              hasPendingAssistantGroup = false;
              pendingToolCalls = new ArrayList<>();
            }
            result.add(
                new RecordedMessage(
                    "tool",
                    item.path("output").asText(),
                    List.of(),
                    item.path("call_id").asText(),
                    List.of()));
          }
          default -> {
            // reasoning / item_reference / server-tool items etc. - not modeled by this parser yet.
          }
        }
      }

      if (hasPendingAssistantGroup) {
        result.add(
            new RecordedMessage(
                "assistant", pendingContent, pendingParts, null, List.copyOf(pendingToolCalls)));
      }
      return result;
    }

    private record ParsedContent(String content, List<JsonNode> parts) {}

    private static ParsedContent parseContent(JsonNode contentNode) {
      if (contentNode == null || contentNode.isMissingNode() || contentNode.isNull()) {
        return new ParsedContent(null, List.of());
      }
      if (contentNode.isTextual()) {
        return new ParsedContent(contentNode.asText(), List.of());
      }
      if (contentNode.isArray()) {
        final List<JsonNode> parts = new ArrayList<>();
        contentNode.forEach(parts::add);
        return new ParsedContent(null, parts);
      }
      return new ParsedContent(null, List.of());
    }

    private final List<RecordedMessage> messages;
    private final List<ToolDefinition> toolDefinitions;
    private final RecordedResponseFormat responseFormat;
    private final String reasoningEffort;
    private final List<String> include;
    private final List<JsonNode> rawInput;

    private RecordedChatRequest(
        List<RecordedMessage> messages,
        List<ToolDefinition> toolDefinitions,
        RecordedResponseFormat responseFormat,
        String reasoningEffort,
        List<String> include,
        List<JsonNode> rawInput) {
      this.messages = messages;
      this.toolDefinitions = toolDefinitions;
      this.responseFormat = responseFormat;
      this.reasoningEffort = reasoningEffort;
      this.include = include;
      this.rawInput = rawInput;
    }

    /** The reconstructed messages, in conversation order. */
    public List<RecordedMessage> messages() {
      return messages;
    }

    /** Tool function names in the order they were declared. */
    public List<String> toolNames() {
      return toolDefinitions().stream().map(ToolDefinition::name).toList();
    }

    /** Tool definitions in the order they were declared. */
    public List<ToolDefinition> toolDefinitions() {
      return toolDefinitions;
    }

    /** The parsed structured-output format, or empty if the request did not include one. */
    public Optional<RecordedResponseFormat> responseFormat() {
      return Optional.ofNullable(responseFormat);
    }

    /**
     * The top-level {@code reasoning.effort} value, or empty if the request carried no {@code
     * reasoning} object at all (i.e. {@code configuration.openai.model.parameters.effort} was
     * unset).
     */
    public Optional<String> reasoningEffort() {
      return Optional.ofNullable(reasoningEffort);
    }

    /**
     * The top-level {@code include[]} array (e.g. {@code "reasoning.encrypted_content"}), empty if
     * the request carried none.
     */
    public List<String> include() {
      return include;
    }

    /**
     * The raw, unregrouped {@code input[]} array exactly as sent on the wire - unlike {@link
     * #messages()} (which regroups a {@code message}+{@code function_call} sequence back into one
     * assistant {@link RecordedMessage} and silently skips item kinds with no provider-neutral
     * representation, e.g. {@code reasoning}/server-tool items), this exposes every item verbatim
     * and in original order, so Task 4's reasoning/server-tool round-trip assertions can inspect
     * those otherwise-dropped item kinds directly by {@code type}.
     */
    public List<JsonNode> rawInputItems() {
      return rawInput;
    }

    /** Wire format for the full Responses request body. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record RequestBodyWire(
        String instructions,
        List<JsonNode> input,
        List<ToolWire> tools,
        TextWire text,
        ReasoningWire reasoning,
        List<String> include) {

      private RequestBodyWire {
        input = input == null ? Collections.emptyList() : input;
        tools = tools == null ? Collections.emptyList() : tools;
        include = include == null ? Collections.emptyList() : include;
      }
    }

    /** Wire format for the top-level {@code reasoning} object. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ReasoningWire(String effort) {}

    /** Wire format for a single entry in the {@code tools} array. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ToolWire(
        String type, String name, String description, Map<String, Object> parameters) {

      ToolDefinition toToolDefinition() {
        return ToolDefinition.builder()
            .name(name)
            .description(description)
            .inputSchema(
                parameters != null
                    ? parameters
                    : Map.of("type", "object", "properties", Map.of(), "required", List.of()))
            .build();
      }
    }

    /** Wire format for the {@code text} field. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record TextWire(ResponseFormatWire format) {}

    /** Wire format for the {@code text.format} field. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ResponseFormatWire(String type, String name, Map<String, Object> schema) {

      RecordedResponseFormat toResponseFormat() {
        return new RecordedResponseFormat(type, name, schema);
      }
    }
  }
}
