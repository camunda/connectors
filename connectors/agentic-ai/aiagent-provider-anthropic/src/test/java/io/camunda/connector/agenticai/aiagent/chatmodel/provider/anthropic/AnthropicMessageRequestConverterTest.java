/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.chatmodel.provider.anthropic;

import static io.camunda.connector.agenticai.aiagent.testsupport.AgentConfigurations.DEFAULT_RESPONSE;
import static org.assertj.core.api.Assertions.assertThat;

import com.anthropic.core.JsonValue;
import com.anthropic.core.ObjectMappers;
import com.anthropic.models.messages.MessageParam;
import com.anthropic.models.messages.ThinkingConfigAdaptive;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.agenticai.aiagent.api.conversation.ConversationSnapshot;
import io.camunda.connector.agenticai.aiagent.chatmodel.connection.EndpointAuthentication;
import io.camunda.connector.agenticai.aiagent.chatmodel.connection.RequestCustomizations;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.anthropic.AnthropicConfiguration.Backend;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.anthropic.AnthropicConfiguration.Display;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.anthropic.AnthropicConfiguration.Effort;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.anthropic.AnthropicConfiguration.Thinking;
import io.camunda.connector.agenticai.aiagent.model.message.AssistantMessage;
import io.camunda.connector.agenticai.aiagent.model.message.Message;
import io.camunda.connector.agenticai.aiagent.model.message.SystemMessage;
import io.camunda.connector.agenticai.aiagent.model.message.ToolCallResultMessage;
import io.camunda.connector.agenticai.aiagent.model.message.UserMessage;
import io.camunda.connector.agenticai.aiagent.model.message.content.ProviderContent;
import io.camunda.connector.agenticai.aiagent.model.message.content.ReasoningContent;
import io.camunda.connector.agenticai.aiagent.model.message.content.TextContent;
import io.camunda.connector.agenticai.aiagent.model.request.ResponseConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.ResponseFormatConfiguration.JsonResponseFormatConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.ResponseFormatConfiguration.TextResponseFormatConfiguration;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCall;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCallResultContent;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolDefinition;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

class AnthropicMessageRequestConverterTest {

  private final ObjectMapper objectMapper = new ObjectMapper();
  private final AnthropicContentConverter contentConverter =
      new AnthropicContentConverter(objectMapper);
  private final AnthropicMessageRequestConverter converter =
      new AnthropicMessageRequestConverter(contentConverter);

  private static AnthropicConfiguration model() {
    return model(null, null, false, AnthropicConfiguration.DEFAULT_MAX_TOKENS, null, null, null);
  }

  private static AnthropicConfiguration model(
      @Nullable Effort effort,
      @Nullable Thinking thinking,
      boolean promptCaching,
      int maxTokens,
      @Nullable Double temperature,
      @Nullable Double topP,
      @Nullable Integer topK) {
    return configuration(
        new Backend.Api("sk-ant-test", null, RequestCustomizations.NONE),
        effort,
        thinking,
        promptCaching,
        maxTokens,
        temperature,
        topP,
        topK);
  }

  private static AnthropicConfiguration configuration(
      Backend backend,
      @Nullable Effort effort,
      @Nullable Thinking thinking,
      boolean promptCaching,
      int maxTokens,
      @Nullable Double temperature,
      @Nullable Double topP,
      @Nullable Integer topK) {
    return new AnthropicConfiguration(
        backend,
        "claude-sonnet-4-6",
        Duration.ofMinutes(3),
        effort,
        thinking,
        promptCaching,
        maxTokens,
        temperature,
        topP,
        topK);
  }

  /** Builds a model with only the prompt-caching toggle set. */
  private static AnthropicConfiguration promptCachingModel(@Nullable Boolean enablePromptCaching) {
    return model(
        null,
        null,
        Boolean.TRUE.equals(enablePromptCaching),
        AnthropicConfiguration.DEFAULT_MAX_TOKENS,
        null,
        null,
        null);
  }

  /** Builds a model with only the thinking mechanism set. */
  private static AnthropicConfiguration thinkingModel(@Nullable Thinking thinking) {
    return model(
        null, thinking, false, AnthropicConfiguration.DEFAULT_MAX_TOKENS, null, null, null);
  }

  /** Builds a model with only the effort set. */
  private static AnthropicConfiguration effortModel(@Nullable Effort effort) {
    return model(effort, null, false, AnthropicConfiguration.DEFAULT_MAX_TOKENS, null, null, null);
  }

  /** Builds a model on the {@code custom} backend with the given additional body params. */
  private static AnthropicConfiguration customModel(@Nullable Map<String, Object> bodyProperties) {
    return customModel(null, null, bodyProperties);
  }

  /**
   * Builds a model on the {@code anthropic-api} backend with the given hidden headers, query
   * parameters, and additional body properties.
   */
  private static AnthropicConfiguration apiModel(
      @Nullable Map<String, String> headers,
      @Nullable Map<String, String> queryParameters,
      @Nullable Map<String, Object> bodyProperties) {
    return configuration(
        new Backend.Api(
            "sk-ant-test",
            null,
            RequestCustomizations.of(headers, queryParameters, bodyProperties)),
        null,
        null,
        false,
        AnthropicConfiguration.DEFAULT_MAX_TOKENS,
        null,
        null,
        null);
  }

  /**
   * Builds a model on the {@code custom} backend with the given headers, query parameters, and
   * additional body properties.
   */
  private static AnthropicConfiguration customModel(
      @Nullable Map<String, String> headers,
      @Nullable Map<String, String> queryParameters,
      @Nullable Map<String, Object> bodyProperties) {
    return configuration(
        new Backend.Custom(
            "https://example.com",
            new EndpointAuthentication.None(),
            RequestCustomizations.of(headers, queryParameters, bodyProperties)),
        null,
        null,
        false,
        AnthropicConfiguration.DEFAULT_MAX_TOKENS,
        null,
        null,
        null);
  }

  private static JsonNode requestBodyAsJson(
      com.anthropic.models.messages.MessageCreateParams params) {
    return ObjectMappers.jsonMapper().valueToTree(params._body());
  }

  @Test
  void mapsSystemPromptToTopLevelSystemAndRemainingToMessages() {
    final var snapshot =
        new ConversationSnapshot(
            List.of(
                SystemMessage.builder().content(List.of(TextContent.textContent("sys"))).build(),
                UserMessage.builder().content(List.of(TextContent.textContent("hi"))).build()),
            List.of());

    final var params = converter.toMessageCreateParams(model(), DEFAULT_RESPONSE, snapshot);

    assertThat(params.system()).isPresent();
    assertThat(params.system().orElseThrow().asString()).isEqualTo("sys");

    assertThat(params.messages()).hasSize(1);
    final var message = params.messages().get(0);
    assertThat(message.role()).isEqualTo(MessageParam.Role.USER);
    assertThat(message.content().asBlockParams()).hasSize(1);
    assertThat(message.content().asBlockParams().get(0).text().orElseThrow().text())
        .isEqualTo("hi");
  }

  @Test
  void mapsToolDefinitionsToTools() {
    final Map<String, Object> schema =
        Map.of(
            "type",
            "object",
            "properties",
            Map.of("quantity", Map.of("type", "integer")),
            "required",
            List.of("quantity"));
    final var snapshot =
        new ConversationSnapshot(
            List.of(UserMessage.builder().content(List.of(TextContent.textContent("hi"))).build()),
            List.of(
                ToolDefinition.builder()
                    .name("SuperfluxProduct")
                    .description("desc")
                    .inputSchema(schema)
                    .build()));

    final var params = converter.toMessageCreateParams(model(), DEFAULT_RESPONSE, snapshot);

    assertThat(params.tools()).isPresent();
    assertThat(params.tools().orElseThrow()).hasSize(1);

    final var tool = params.tools().orElseThrow().get(0).tool().orElseThrow();
    assertThat(tool.name()).isEqualTo("SuperfluxProduct");
    assertThat(tool.description()).contains("desc");

    final var toolNode = requestBodyAsJson(params).path("tools").get(0);
    assertThat(toolNode.path("input_schema").path("type").asText()).isEqualTo("object");
    assertThat(
            toolNode.path("input_schema").path("properties").path("quantity").path("type").asText())
        .isEqualTo("integer");
    assertThat(toolNode.path("input_schema").path("required").get(0).asText())
        .isEqualTo("quantity");
  }

  @Test
  void mapsAssistantToolCallsAndToolResults() {
    final var snapshot =
        new ConversationSnapshot(
            List.of(
                UserMessage.builder()
                    .content(List.of(TextContent.textContent("please call the tool")))
                    .build(),
                AssistantMessage.builder()
                    .toolCalls(
                        List.of(
                            ToolCall.builder()
                                .id("id")
                                .name("name")
                                .arguments(Map.of("a", 5))
                                .build()))
                    .build(),
                ToolCallResultMessage.builder()
                    .results(
                        List.of(
                            ToolCallResultContent.builder()
                                .id("id")
                                .name("name")
                                .content(List.of(TextContent.textContent("result")))
                                .build()))
                    .build()),
            List.of());

    final var params = converter.toMessageCreateParams(model(), DEFAULT_RESPONSE, snapshot);

    assertThat(params.messages()).hasSize(3);

    final var assistantMessage = params.messages().get(1);
    assertThat(assistantMessage.role()).isEqualTo(MessageParam.Role.ASSISTANT);
    final var toolUseBlock =
        assistantMessage.content().asBlockParams().stream()
            .filter(b -> b.toolUse().isPresent())
            .findFirst()
            .orElseThrow()
            .toolUse()
            .orElseThrow();
    assertThat(toolUseBlock.id()).isEqualTo("id");
    assertThat(toolUseBlock.name()).isEqualTo("name");
    assertThat(toolUseBlock.input()._additionalProperties().get("a")).isEqualTo(JsonValue.from(5));
    assertThat(toolUseBlock.caller()).isEmpty();

    final var toolResultMessage = params.messages().get(2);
    assertThat(toolResultMessage.role()).isEqualTo(MessageParam.Role.USER);
    final var toolResultBlock =
        toolResultMessage.content().asBlockParams().get(0).toolResult().orElseThrow();
    assertThat(toolResultBlock.toolUseId()).isEqualTo("id");
    assertThat(
            toolResultBlock.content().orElseThrow().asBlocks().get(0).text().orElseThrow().text())
        .isEqualTo("result");
  }

  @Test
  void roundTripsProviderContentBackToServerToolBlockParams() {
    // Same fixture shape used to prove the request-side round-trip of server-tool content blocks
    // captured by the response converter (Task 3): a code-execution server_tool_use block followed
    // by its code_execution_tool_result, referencing the same id, must be replayed byte-identically
    // as history on a subsequent request.
    final var serverToolUse =
        new ProviderContent(
            "anthropic",
            Map.of(
                "id",
                "srvtoolu_01",
                "name",
                "code_execution",
                "type",
                "server_tool_use",
                "input",
                Map.of("code", "print(1)")),
            null);
    final var codeExecutionToolResult =
        new ProviderContent(
            "anthropic",
            Map.of(
                "tool_use_id",
                "srvtoolu_01",
                "type",
                "code_execution_tool_result",
                "content",
                Map.of(
                    "type",
                    "code_execution_result",
                    "stdout",
                    "1\n",
                    "stderr",
                    "",
                    "return_code",
                    0L)),
            null);

    final var snapshot =
        new ConversationSnapshot(
            List.of(
                UserMessage.builder()
                    .content(List.of(TextContent.textContent("run some code")))
                    .build(),
                AssistantMessage.builder()
                    .content(
                        List.of(
                            TextContent.textContent("working"),
                            serverToolUse,
                            codeExecutionToolResult,
                            TextContent.textContent("done")))
                    .build()),
            List.of());

    final var params = converter.toMessageCreateParams(model(), DEFAULT_RESPONSE, snapshot);

    assertThat(params.messages()).hasSize(2);

    final var assistantMessage = params.messages().get(1);
    assertThat(assistantMessage.role()).isEqualTo(MessageParam.Role.ASSISTANT);

    final var blocks = assistantMessage.content().asBlockParams();
    assertThat(blocks).hasSize(4);

    assertThat(blocks.get(0).text().orElseThrow().text()).isEqualTo("working");

    final var serverToolUseBlock = blocks.get(1).serverToolUse().orElseThrow();
    assertThat(serverToolUseBlock.id()).isEqualTo("srvtoolu_01");
    assertThat(serverToolUseBlock.name().toString()).isEqualTo("code_execution");

    final var codeExecutionToolResultBlock = blocks.get(2).codeExecutionToolResult().orElseThrow();
    assertThat(codeExecutionToolResultBlock.toolUseId()).isEqualTo("srvtoolu_01");

    assertThat(blocks.get(3).text().orElseThrow().text()).isEqualTo("done");
  }

  @Test
  void appendsClientToolCallsAfterProviderContentBlocksRegardlessOfOriginalInterleaving() {
    // Known limitation: assistantParam() emits `content` blocks (including ProviderContent
    // server-tool blocks) before `toolCalls`, so a response that interleaves server- and
    // client-tool blocks doesn't replay in its original order; see #assistantParam.
    final var serverToolUse =
        new ProviderContent(
            "anthropic",
            Map.of(
                "id", "srvtoolu_01",
                "name", "code_execution",
                "type", "server_tool_use",
                "input", Map.of("code", "print(1)")),
            null);

    final var snapshot =
        new ConversationSnapshot(
            List.of(
                AssistantMessage.builder()
                    .content(List.of(serverToolUse))
                    .toolCalls(
                        List.of(
                            ToolCall.builder()
                                .id("toolu_1")
                                .name("get_weather")
                                .arguments(Map.of("city", "Berlin"))
                                .build()))
                    .build()),
            List.of());

    final var params = converter.toMessageCreateParams(model(), DEFAULT_RESPONSE, snapshot);

    final var blocks = params.messages().get(0).content().asBlockParams();
    assertThat(blocks).hasSize(2);
    assertThat(blocks.get(0).serverToolUse()).isPresent();
    assertThat(blocks.get(1).toolUse().orElseThrow().id()).isEqualTo("toolu_1");
  }

  @Test
  void reEmitsThinkingBlockBeforeToolUseForPureReasoningTurn() {
    // A thinking block re-emitted from ReasoningContent must precede the tool_use block(s)
    // appended from AssistantMessage#toolCalls -- assistantParam() always emits `content` before
    // `toolCalls`, so this holds by construction, but is worth pinning down explicitly since
    // Anthropic requires thinking to lead an assistant turn that also contains tool use.
    final var reasoning =
        new ReasoningContent(
            "anthropic",
            Map.of(
                "type", "thinking",
                "thinking", "Let me think it through",
                "signature", "sig-123"),
            null,
            null);

    final var snapshot =
        new ConversationSnapshot(
            List.of(
                AssistantMessage.builder()
                    .content(List.of(reasoning))
                    .toolCalls(
                        List.of(
                            ToolCall.builder()
                                .id("toolu_1")
                                .name("get_weather")
                                .arguments(Map.of("city", "Berlin"))
                                .build()))
                    .build()),
            List.of());

    final var params = converter.toMessageCreateParams(model(), DEFAULT_RESPONSE, snapshot);

    final var blocks = params.messages().get(0).content().asBlockParams();
    assertThat(blocks).hasSize(2);
    assertThat(blocks.get(0).isThinking()).isTrue();
    assertThat(blocks.get(0).asThinking().signature()).isEqualTo("sig-123");
    assertThat(blocks.get(1).toolUse().orElseThrow().id()).isEqualTo("toolu_1");
  }

  @Test
  void reEmitsNonEmptyContentForPureReasoningTurnWithNoTextOrToolCall() {
    // An assistant message whose only content is reasoning (no text, no tool call) must still
    // produce a non-empty content array -- Anthropic rejects an assistant message with an empty
    // content array.
    final var reasoning =
        new ReasoningContent(
            "anthropic",
            Map.of(
                "type", "thinking",
                "thinking", "Let me think it through",
                "signature", "sig-123"),
            null,
            null);

    final var snapshot =
        new ConversationSnapshot(
            List.of(AssistantMessage.builder().content(List.of(reasoning)).build()), List.of());

    final var params = converter.toMessageCreateParams(model(), DEFAULT_RESPONSE, snapshot);

    final var blocks = params.messages().get(0).content().asBlockParams();
    assertThat(blocks).hasSize(1);
    assertThat(blocks.get(0).isThinking()).isTrue();
  }

  // --- Full v1 parameter parity (Task 0 §6.1) -------------------------------------------------

  @Test
  @SuppressWarnings(
      "deprecation") // temperature()/topP()/topK() deprecated in anthropic-java 2.48.0
  void mapsFullV1ParameterParitySet() {
    // endpoint/apiKey/timeout are transport-layer (AnthropicChatModelFactory's concern); this
    // asserts the remaining model-parameter fields this converter is responsible for.
    final var configuration = model(null, null, false, 2048, 0.5, 0.9, 40);
    final var snapshot = new ConversationSnapshot(List.of(), List.of());

    final var params = converter.toMessageCreateParams(configuration, DEFAULT_RESPONSE, snapshot);

    assertThat(params.model().asString()).isEqualTo("claude-sonnet-4-6");
    assertThat(params.maxTokens()).isEqualTo(2048L);
    assertThat(params.temperature()).contains(0.5);
    assertThat(params.topP()).contains(0.9);
    assertThat(params.topK()).contains(40L);
  }

  @Test
  @SuppressWarnings(
      "deprecation") // temperature()/topP()/topK() deprecated in anthropic-java 2.48.0
  void usesConfiguredMaxTokensAndModelParams() {
    final var configuration = model(null, null, false, 2048, 0.5, 0.9, 40);
    final var snapshot = new ConversationSnapshot(List.of(), List.of());
    final var response =
        new ResponseConfiguration(new TextResponseFormatConfiguration(true), false);

    final var params = converter.toMessageCreateParams(configuration, response, snapshot);

    assertThat(params.maxTokens()).isEqualTo(2048L);
    assertThat(params.temperature()).contains(0.5);
    assertThat(params.topP()).contains(0.9);
    assertThat(params.topK()).contains(40L);
    // TEXT response format (parseJson or not) has no request-side effect.
    assertThat(params.outputConfig()).isEmpty();
  }

  @Test
  void treatsEmptyJsonSchemaAsNoSchema() {
    final var response =
        new ResponseConfiguration(new JsonResponseFormatConfiguration(Map.of(), "Response"), false);
    final var snapshot = new ConversationSnapshot(List.of(), List.of());

    final var params = converter.toMessageCreateParams(model(), response, snapshot);

    assertThat(params.outputConfig()).isEmpty();
  }

  @Test
  void configuresStructuredOutputFromJsonSchema() {
    final Map<String, Object> schema =
        Map.of("type", "object", "properties", Map.of("answer", Map.of("type", "string")));
    final var response =
        new ResponseConfiguration(new JsonResponseFormatConfiguration(schema, "Answer"), false);
    final var snapshot = new ConversationSnapshot(List.of(), List.of());

    final var params = converter.toMessageCreateParams(model(), response, snapshot);

    assertThat(params.outputConfig()).isPresent();

    final var outputConfigNode = requestBodyAsJson(params).path("output_config");
    assertThat(outputConfigNode.path("format").path("type").asText()).isEqualTo("json_schema");
    assertThat(outputConfigNode.path("format").path("schema").path("type").asText())
        .isEqualTo("object");
    assertThat(
            outputConfigNode
                .path("format")
                .path("schema")
                .path("properties")
                .path("answer")
                .path("type")
                .asText())
        .isEqualTo("string");
    // no schema name is ever put on the wire
    assertThat(outputConfigNode.path("format").has("name")).isFalse();
  }

  @Test
  void defaultsMaxTokensToTheDefaultConstantWhenConfigNull() {
    final var snapshot = new ConversationSnapshot(List.of(), List.of());

    final var params = converter.toMessageCreateParams(model(), DEFAULT_RESPONSE, snapshot);

    assertThat(params.maxTokens()).isEqualTo(AnthropicConfiguration.DEFAULT_MAX_TOKENS);
    assertThat(params.maxTokens()).isEqualTo(4096L);
  }

  // --- Reasoning: thinking / effort mapping ---------------------------------------------------

  @Test
  void mapsEnabledThinkingToWireThinkingConfigWithBudgetTokens() {
    final var configuration = thinkingModel(new Thinking.Enabled(2048));
    final var snapshot = new ConversationSnapshot(List.of(), List.of());

    final var params = converter.toMessageCreateParams(configuration, DEFAULT_RESPONSE, snapshot);

    assertThat(params.thinking()).isPresent();
    assertThat(params.thinking().orElseThrow().isEnabled()).isTrue();
    assertThat(params.thinking().orElseThrow().asEnabled().budgetTokens()).isEqualTo(2048L);

    final var thinkingNode = requestBodyAsJson(params).path("thinking");
    assertThat(thinkingNode.path("type").asText()).isEqualTo("enabled");
    assertThat(thinkingNode.path("budget_tokens").asLong()).isEqualTo(2048L);
  }

  @Test
  void mapsAdaptiveThinkingWithSummarizedDisplayToLowercaseWireValue() {
    final var configuration = thinkingModel(new Thinking.Adaptive(Display.SUMMARIZED));
    final var snapshot = new ConversationSnapshot(List.of(), List.of());

    final var params = converter.toMessageCreateParams(configuration, DEFAULT_RESPONSE, snapshot);

    assertThat(params.thinking().orElseThrow().isAdaptive()).isTrue();
    assertThat(params.thinking().orElseThrow().asAdaptive().display())
        .contains(ThinkingConfigAdaptive.Display.SUMMARIZED);

    final var thinkingNode = requestBodyAsJson(params).path("thinking");
    assertThat(thinkingNode.path("type").asText()).isEqualTo("adaptive");
    assertThat(thinkingNode.path("display").asText()).isEqualTo("summarized");
  }

  @Test
  void mapsAdaptiveThinkingWithOmittedDisplayToLowercaseWireValue() {
    final var configuration = thinkingModel(new Thinking.Adaptive(Display.OMITTED));
    final var snapshot = new ConversationSnapshot(List.of(), List.of());

    final var params = converter.toMessageCreateParams(configuration, DEFAULT_RESPONSE, snapshot);

    final var thinkingNode = requestBodyAsJson(params).path("thinking");
    assertThat(thinkingNode.path("display").asText()).isEqualTo("omitted");
  }

  @Test
  void mapsAdaptiveThinkingWithoutDisplayEmitsNoDisplayField() {
    final var configuration = thinkingModel(new Thinking.Adaptive(null));
    final var snapshot = new ConversationSnapshot(List.of(), List.of());

    final var params = converter.toMessageCreateParams(configuration, DEFAULT_RESPONSE, snapshot);

    assertThat(params.thinking().orElseThrow().asAdaptive().display()).isEmpty();
    assertThat(requestBodyAsJson(params).path("thinking").has("display")).isFalse();
  }

  @Test
  void mapsDisabledThinkingToWireThinkingConfig() {
    final var configuration = thinkingModel(new Thinking.Disabled());
    final var snapshot = new ConversationSnapshot(List.of(), List.of());

    final var params = converter.toMessageCreateParams(configuration, DEFAULT_RESPONSE, snapshot);

    assertThat(params.thinking().orElseThrow().isDisabled()).isTrue();
    assertThat(requestBodyAsJson(params).path("thinking").path("type").asText())
        .isEqualTo("disabled");
  }

  @Test
  void nullThinkingModeEmitsNoThinkingParam() {
    // An unset thinking mechanism maps no thinking param onto the wire request.
    final var configuration = thinkingModel(null);
    final var snapshot = new ConversationSnapshot(List.of(), List.of());

    final var params = converter.toMessageCreateParams(configuration, DEFAULT_RESPONSE, snapshot);

    assertThat(params.thinking()).isEmpty();
  }

  @Test
  void modelDefaultThinkingModeEmitsNoThinkingParam() {
    // The model default is the absence of a thinking mechanism: nothing is sent.
    final var configuration = thinkingModel(null);
    final var snapshot = new ConversationSnapshot(List.of(), List.of());

    final var params = converter.toMessageCreateParams(configuration, DEFAULT_RESPONSE, snapshot);

    assertThat(params.thinking()).isEmpty();
  }

  @Test
  void budgetTokensOnlyWithNullModeEmitsNoThinkingParam() {
    // A thinking budget never reaches the wire without a thinking mechanism to carry it.
    final var configuration = thinkingModel(null);
    final var snapshot = new ConversationSnapshot(List.of(), List.of());

    final var params = converter.toMessageCreateParams(configuration, DEFAULT_RESPONSE, snapshot);

    assertThat(params.thinking()).isEmpty();
  }

  @Test
  void modelDefaultEffortEmitsNoOutputConfig() {
    // The model default effort is the absence of an effort: nothing is sent.
    final var configuration = effortModel(null);
    final var snapshot = new ConversationSnapshot(List.of(), List.of());

    final var params = converter.toMessageCreateParams(configuration, DEFAULT_RESPONSE, snapshot);

    assertThat(params.outputConfig()).isEmpty();
  }

  @Test
  void mapsEachEffortLevelToItsLowercaseWireValue() {
    final var snapshot = new ConversationSnapshot(List.of(), List.of());

    for (final var entry :
        Map.of(
                Effort.LOW, "low",
                Effort.MEDIUM, "medium",
                Effort.HIGH, "high",
                Effort.XHIGH, "xhigh",
                Effort.MAX, "max")
            .entrySet()) {
      final var configuration = effortModel(entry.getKey());
      final var params = converter.toMessageCreateParams(configuration, DEFAULT_RESPONSE, snapshot);

      assertThat(params.outputConfig()).isPresent();
      assertThat(params.outputConfig().orElseThrow().effort().orElseThrow().asString())
          .isEqualTo(entry.getValue());
      assertThat(requestBodyAsJson(params).path("output_config").path("effort").asText())
          .isEqualTo(entry.getValue());
    }
  }

  @Test
  void effortAndJsonResponseFormatBothLandOnTheSameOutputConfigWithoutClobbering() {
    // Regression guard: MessageCreateParams.Builder#outputConfig(OutputConfig) is a plain
    // setter that replaces the whole field, so effort and the JSON schema format must be combined
    // into a single OutputConfig before being applied, or one would silently drop the other.
    final Map<String, Object> schema =
        Map.of("type", "object", "properties", Map.of("answer", Map.of("type", "string")));
    final var response =
        new ResponseConfiguration(new JsonResponseFormatConfiguration(schema, "Answer"), false);
    final var configuration = effortModel(Effort.HIGH);
    final var snapshot = new ConversationSnapshot(List.of(), List.of());

    final var params = converter.toMessageCreateParams(configuration, response, snapshot);

    final var outputConfigNode = requestBodyAsJson(params).path("output_config");
    assertThat(outputConfigNode.path("effort").asText()).isEqualTo("high");
    assertThat(outputConfigNode.path("format").path("type").asText()).isEqualTo("json_schema");
    assertThat(params.outputConfig().orElseThrow().format()).isPresent();
    assertThat(params.outputConfig().orElseThrow().effort()).isPresent();
  }

  // --- Prompt caching ---------------------------------------------------------------------------

  @Test
  void promptCachingEnabledAddsTopLevelEphemeralCacheControl() {
    final var params =
        converter.toMessageCreateParams(
            promptCachingModel(true),
            DEFAULT_RESPONSE,
            new ConversationSnapshot(List.of(), List.of()));

    final var cacheControl = requestBodyAsJson(params).path("cache_control");
    assertThat(cacheControl.isMissingNode()).as("cache_control present").isFalse();
    assertThat(cacheControl.path("type").asText()).isEqualTo("ephemeral");
  }

  @Test
  void promptCachingDisabledOrUnsetOmitsCacheControl() {
    for (final Boolean flag : new Boolean[] {null, Boolean.FALSE}) {
      final var params =
          converter.toMessageCreateParams(
              promptCachingModel(flag),
              DEFAULT_RESPONSE,
              new ConversationSnapshot(List.of(), List.of()));

      assertThat(requestBodyAsJson(params).path("cache_control").isMissingNode())
          .as("cache_control omitted when flag=%s", flag)
          .isTrue();
    }
  }

  @Test
  void promptCachingReSendsEarlierPrefixByteIdenticallyAcrossTurns() {
    // The point of automatic caching: it caches the whole prefix (system + tools + earlier
    // messages), so a cross-turn cache HIT depends on the converter re-sending that prefix
    // byte-identically and only APPENDING the new turn's messages. This asserts exactly that
    // (assuming an append-only snapshot, i.e. the message window has not yet started evicting).
    final var tools =
        List.of(
            ToolDefinition.builder()
                .name("getWeather")
                .description("desc")
                .inputSchema(Map.of("type", "object"))
                .build());
    final var system =
        SystemMessage.builder().content(List.of(TextContent.textContent("sys"))).build();
    final var turn1Messages =
        List.<Message>of(
            system,
            UserMessage.builder().content(List.of(TextContent.textContent("q1"))).build(),
            AssistantMessage.builder().content(List.of(TextContent.textContent("a1"))).build());
    final List<Message> turn2Messages = new ArrayList<>(turn1Messages);
    turn2Messages.add(
        UserMessage.builder().content(List.of(TextContent.textContent("q2"))).build());

    final var turn1 =
        converter.toMessageCreateParams(
            promptCachingModel(true),
            DEFAULT_RESPONSE,
            new ConversationSnapshot(turn1Messages, tools));
    final var turn2 =
        converter.toMessageCreateParams(
            promptCachingModel(true),
            DEFAULT_RESPONSE,
            new ConversationSnapshot(turn2Messages, tools));

    final JsonNode body1 = requestBodyAsJson(turn1);
    final JsonNode body2 = requestBodyAsJson(turn2);

    assertThat(body2.path("system")).as("system re-sent unchanged").isEqualTo(body1.path("system"));
    assertThat(body2.path("tools")).as("tools re-sent unchanged").isEqualTo(body1.path("tools"));

    final JsonNode messages1 = body1.path("messages");
    final JsonNode messages2 = body2.path("messages");
    assertThat(messages2.size()).as("turn 2 appends a message").isGreaterThan(messages1.size());
    for (int i = 0; i < messages1.size(); i++) {
      assertThat(messages2.get(i))
          .as("message[%d] re-sent byte-identically", i)
          .isEqualTo(messages1.get(i));
    }
  }

  @Test
  void promptCachingReplaysReasoningContentByteIdenticallyAfterTextExtraction() {
    // ReasoningContent stores the thinking text separately from the (stripped) payload; the
    // converter must merge it back in so the replayed thinking block -- and thus the cached
    // prefix -- is identical across turns, not just structurally equivalent.
    final var reasoning =
        new ReasoningContent(
            "anthropic",
            Map.of("type", "thinking", "signature", "sig-123"),
            "Let me think it through",
            null);
    final var turn1Messages =
        List.<Message>of(AssistantMessage.builder().content(List.of(reasoning)).build());

    final var turn1 =
        converter.toMessageCreateParams(
            promptCachingModel(true),
            DEFAULT_RESPONSE,
            new ConversationSnapshot(turn1Messages, List.of()));
    final var turn2 =
        converter.toMessageCreateParams(
            promptCachingModel(true),
            DEFAULT_RESPONSE,
            new ConversationSnapshot(turn1Messages, List.of()));

    final JsonNode messages1 = requestBodyAsJson(turn1).path("messages");
    final JsonNode messages2 = requestBodyAsJson(turn2).path("messages");
    assertThat(messages2).as("reasoning message re-sent byte-identically").isEqualTo(messages1);
    assertThat(messages1.at("/0/content/0/thinking").asText()).isEqualTo("Let me think it through");
  }

  // --- Custom backend: headers, query parameters, and request (body) parameters -----------------

  @Test
  void customBackendHeadersAreMergedAsAdditionalHeaders() {
    final var params =
        converter.toMessageCreateParams(
            customModel(Map.of("X-Custom-Header", "custom-value"), null, null),
            DEFAULT_RESPONSE,
            new ConversationSnapshot(List.of(), List.of()));

    assertThat(params._additionalHeaders().values("X-Custom-Header"))
        .containsExactly("custom-value");
  }

  @Test
  void customBackendQueryParametersAreMergedAsAdditionalQueryParameters() {
    final var params =
        converter.toMessageCreateParams(
            customModel(null, Map.of("api-version", "2026-01-01"), null),
            DEFAULT_RESPONSE,
            new ConversationSnapshot(List.of(), List.of()));

    assertThat(params._additionalQueryParams().values("api-version")).containsExactly("2026-01-01");
  }

  @Test
  void customBackendBodyPropertiesAreMergedIntoRequestBody() {
    final var params =
        converter.toMessageCreateParams(
            customModel(Map.of("custom_field", "custom_value")),
            DEFAULT_RESPONSE,
            new ConversationSnapshot(List.of(), List.of()));

    assertThat(requestBodyAsJson(params).path("custom_field").asText()).isEqualTo("custom_value");
  }

  @Test
  void directBackendNeverEmitsAdditionalBodyPropertiesEvenIfNoneConfigured() {
    final var params =
        converter.toMessageCreateParams(
            model(), DEFAULT_RESPONSE, new ConversationSnapshot(List.of(), List.of()));

    // no additional body properties beyond the standard request fields
    assertThat(requestBodyAsJson(params).has("custom_field")).isFalse();
  }

  @Test
  void customBackendWithNoBodyPropertiesAddsNothing() {
    final var params =
        converter.toMessageCreateParams(
            customModel(null), DEFAULT_RESPONSE, new ConversationSnapshot(List.of(), List.of()));

    assertThat(requestBodyAsJson(params).has("custom_field")).isFalse();
  }

  // --- Anthropic API backend: hidden headers, query parameters, and body properties -------------

  @Test
  void apiBackendHiddenHeadersAreMergedAsAdditionalHeaders() {
    final var params =
        converter.toMessageCreateParams(
            apiModel(Map.of("X-Hidden-Header", "hidden-value"), null, null),
            DEFAULT_RESPONSE,
            new ConversationSnapshot(List.of(), List.of()));

    assertThat(params._additionalHeaders().values("X-Hidden-Header"))
        .containsExactly("hidden-value");
  }

  @Test
  void apiBackendHiddenQueryParametersAreMergedAsAdditionalQueryParameters() {
    final var params =
        converter.toMessageCreateParams(
            apiModel(null, Map.of("api-version", "2026-01-01"), null),
            DEFAULT_RESPONSE,
            new ConversationSnapshot(List.of(), List.of()));

    assertThat(params._additionalQueryParams().values("api-version")).containsExactly("2026-01-01");
  }

  @Test
  void apiBackendHiddenBodyPropertiesAreMergedIntoRequestBody() {
    final var params =
        converter.toMessageCreateParams(
            apiModel(null, null, Map.of("hidden_field", "hidden_value")),
            DEFAULT_RESPONSE,
            new ConversationSnapshot(List.of(), List.of()));

    assertThat(requestBodyAsJson(params).path("hidden_field").asText()).isEqualTo("hidden_value");
  }
}
