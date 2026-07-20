/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.chatmodel.provider.anthropic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.anthropic.core.JsonValue;
import com.anthropic.core.ObjectMappers;
import com.anthropic.models.beta.AnthropicBeta;
import com.anthropic.models.beta.messages.BetaMessageParam;
import com.anthropic.models.beta.messages.BetaThinkingConfigAdaptive;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.agenticai.aiagent.capabilities.CoreModelCapabilities;
import io.camunda.connector.agenticai.aiagent.capabilities.ModelCapabilities.Modality;
import io.camunda.connector.agenticai.aiagent.chatmodel.V2ChatModelApiConfiguration;
import io.camunda.connector.agenticai.aiagent.memory.ConversationSnapshot;
import io.camunda.connector.agenticai.aiagent.model.AgentConfiguration;
import io.camunda.connector.agenticai.aiagent.model.AgentExecutionContext;
import io.camunda.connector.agenticai.aiagent.model.message.AssistantMessage;
import io.camunda.connector.agenticai.aiagent.model.message.Message;
import io.camunda.connector.agenticai.aiagent.model.message.SystemMessage;
import io.camunda.connector.agenticai.aiagent.model.message.ToolCallResultMessage;
import io.camunda.connector.agenticai.aiagent.model.message.UserMessage;
import io.camunda.connector.agenticai.aiagent.model.message.content.ProviderContent;
import io.camunda.connector.agenticai.aiagent.model.message.content.ReasoningContent;
import io.camunda.connector.agenticai.aiagent.model.message.content.TextContent;
import io.camunda.connector.agenticai.aiagent.model.request.JobWorkerResponseConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.PromptConfiguration.SystemPromptConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.PromptConfiguration.UserPromptConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.ResponseConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.ResponseFormatConfiguration.JsonResponseFormatConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.ResponseFormatConfiguration.TextResponseFormatConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.v2.AnthropicChatModel;
import io.camunda.connector.agenticai.aiagent.model.request.v2.AnthropicChatModel.AnthropicBackend.AnthropicDirectBackend;
import io.camunda.connector.agenticai.aiagent.model.request.v2.AnthropicChatModel.AnthropicConnection;
import io.camunda.connector.agenticai.aiagent.model.request.v2.AnthropicChatModel.AnthropicModel;
import io.camunda.connector.agenticai.aiagent.model.request.v2.AnthropicChatModel.AnthropicModel.AnthropicModelParameters;
import io.camunda.connector.agenticai.aiagent.model.request.v2.AnthropicChatModel.AnthropicModel.AnthropicThinking;
import io.camunda.connector.agenticai.aiagent.model.request.v2.AnthropicChatModel.AnthropicModel.ThinkingDisplay;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCall;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCallResultContent;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolDefinition;
import io.camunda.connector.api.error.ConnectorException;
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

  private static AnthropicChatModel model(@Nullable AnthropicModelParameters parameters) {
    return model(parameters, null);
  }

  private static AnthropicChatModel model(
      @Nullable AnthropicModelParameters parameters, @Nullable List<String> skills) {
    return model(parameters, skills, null, null, null);
  }

  private static AnthropicChatModel model(
      @Nullable AnthropicModelParameters parameters,
      @Nullable List<String> skills,
      @Nullable Boolean enableCodeExecution,
      @Nullable Boolean enableWebSearch,
      @Nullable Boolean enableWebFetch) {
    return model(
        parameters, skills, enableCodeExecution, null, enableWebSearch, null, enableWebFetch, null);
  }

  private static AnthropicChatModel model(
      @Nullable AnthropicModelParameters parameters,
      @Nullable List<String> skills,
      @Nullable Boolean enableCodeExecution,
      @Nullable Boolean enableWebSearch,
      @Nullable String webSearchVersion,
      @Nullable Boolean enableWebFetch,
      @Nullable String webFetchVersion) {
    return model(
        parameters,
        skills,
        enableCodeExecution,
        null,
        enableWebSearch,
        webSearchVersion,
        enableWebFetch,
        webFetchVersion);
  }

  private static AnthropicChatModel model(
      @Nullable AnthropicModelParameters parameters,
      @Nullable List<String> skills,
      @Nullable Boolean enableCodeExecution,
      @Nullable String codeExecutionVersion,
      @Nullable Boolean enableWebSearch,
      @Nullable String webSearchVersion,
      @Nullable Boolean enableWebFetch,
      @Nullable String webFetchVersion) {
    return new AnthropicChatModel(
        new AnthropicConnection(
            new AnthropicDirectBackend(null, "sk-ant-test"),
            new AnthropicModel("claude-sonnet-4-6", parameters),
            null,
            null,
            skills,
            enableCodeExecution,
            codeExecutionVersion,
            enableWebSearch,
            webSearchVersion,
            enableWebFetch,
            webFetchVersion,
            null));
  }

  /** Builds a model with only the prompt-caching toggle set (all tool toggles unset). */
  private static AnthropicChatModel promptCachingModel(@Nullable Boolean enablePromptCaching) {
    return new AnthropicChatModel(
        new AnthropicConnection(
            new AnthropicDirectBackend(null, "sk-ant-test"),
            new AnthropicModel("claude-sonnet-4-6", null),
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            enablePromptCaching));
  }

  private static AgentExecutionContext ctx(
      AnthropicChatModel model, @Nullable ResponseConfiguration response) {
    final var configuration =
        new AgentConfiguration(
            new V2ChatModelApiConfiguration(model),
            model.model(),
            model.providerType(),
            new SystemPromptConfiguration("system prompt"),
            new UserPromptConfiguration("user prompt", null),
            null,
            null,
            null,
            response);

    final var executionContext = mock(AgentExecutionContext.class);
    when(executionContext.configuration()).thenReturn(configuration);
    return executionContext;
  }

  private static JsonNode requestBodyAsJson(
      com.anthropic.models.beta.messages.MessageCreateParams params) {
    return ObjectMappers.jsonMapper().valueToTree(params._body());
  }

  /** Extracts each tool's wire {@code type} string from the serialized request body. */
  private static List<String> toolTypes(
      com.anthropic.models.beta.messages.MessageCreateParams params) {
    final var toolsNode = requestBodyAsJson(params).path("tools");
    final List<String> types = new java.util.ArrayList<>();
    toolsNode.forEach(node -> types.add(node.path("type").asText()));
    return types;
  }

  private static AnthropicModelCapabilities caps() {
    return caps(null);
  }

  private static AnthropicModelCapabilities caps(@Nullable Integer maxOutputTokens) {
    return caps(maxOutputTokens, null);
  }

  private static AnthropicModelCapabilities caps(
      @Nullable Integer maxOutputTokens, @Nullable AnthropicReasoningCapabilities reasoning) {
    return new AnthropicModelCapabilities(
        new CoreModelCapabilities(
            List.of(Modality.TEXT),
            List.of(Modality.TEXT),
            List.of(Modality.TEXT),
            null,
            maxOutputTokens),
        reasoning);
  }

  private static AnthropicModelCapabilities capsWithReasoning(
      List<ThinkingMode> thinkingModes, List<AnthropicEffort> effortLevels) {
    return caps(null, new AnthropicReasoningCapabilities(thinkingModes, effortLevels));
  }

  @Test
  void mapsSystemPromptToTopLevelSystemAndRemainingToMessages() {
    final var snapshot =
        new ConversationSnapshot(
            List.of(
                SystemMessage.builder().content(List.of(TextContent.textContent("sys"))).build(),
                UserMessage.builder().content(List.of(TextContent.textContent("hi"))).build()),
            List.of());

    final var params = converter.toMessageCreateParams(ctx(model(null), null), snapshot, caps());

    assertThat(params.system()).isPresent();
    assertThat(params.system().orElseThrow().asString()).isEqualTo("sys");

    assertThat(params.messages()).hasSize(1);
    final var message = params.messages().get(0);
    assertThat(message.role()).isEqualTo(BetaMessageParam.Role.USER);
    assertThat(message.content().asBetaContentBlockParams()).hasSize(1);
    assertThat(message.content().asBetaContentBlockParams().get(0).text().orElseThrow().text())
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

    final var params = converter.toMessageCreateParams(ctx(model(null), null), snapshot, caps());

    assertThat(params.tools()).isPresent();
    assertThat(params.tools().orElseThrow()).hasSize(1);

    final var tool = params.tools().orElseThrow().get(0).betaTool().orElseThrow();
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

    final var params = converter.toMessageCreateParams(ctx(model(null), null), snapshot, caps());

    assertThat(params.messages()).hasSize(3);

    final var assistantMessage = params.messages().get(1);
    assertThat(assistantMessage.role()).isEqualTo(BetaMessageParam.Role.ASSISTANT);
    final var toolUseBlock =
        assistantMessage.content().asBetaContentBlockParams().stream()
            .filter(b -> b.toolUse().isPresent())
            .findFirst()
            .orElseThrow()
            .toolUse()
            .orElseThrow();
    assertThat(toolUseBlock.id()).isEqualTo("id");
    assertThat(toolUseBlock.name()).isEqualTo("name");
    assertThat(toolUseBlock.input()._additionalProperties().get("a")).isEqualTo(JsonValue.from(5));

    final var toolResultMessage = params.messages().get(2);
    assertThat(toolResultMessage.role()).isEqualTo(BetaMessageParam.Role.USER);
    final var toolResultBlock =
        toolResultMessage.content().asBetaContentBlockParams().get(0).toolResult().orElseThrow();
    assertThat(toolResultBlock.toolUseId()).isEqualTo("id");
    assertThat(
            toolResultBlock.content().orElseThrow().asBlocks().get(0).text().orElseThrow().text())
        .isEqualTo("result");
  }

  @Test
  void roundTripsProviderContentBackToServerToolBlockParams() {
    // Same fixture shape as AnthropicMessageResponseConverterTest
    // .mapsServerToolBlocksToProviderContentPreservingOrder (Task 2): a code-execution
    // server_tool_use block followed by its code_execution_tool_result, referencing the same id.
    // This test proves the request-side round-trip: the response converter's ProviderContent
    // capture, when replayed as history on a subsequent request, must reproduce the identical
    // native content blocks (id and order preserved) -- load-bearing for pause_turn continuations
    // and code-execution container continuity.
    final var serverToolUse =
        new ProviderContent(
            "anthropic",
            "server_tool_use",
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
            "code_execution_tool_result",
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

    final var params = converter.toMessageCreateParams(ctx(model(null), null), snapshot, caps());

    assertThat(params.messages()).hasSize(2);

    final var assistantMessage = params.messages().get(1);
    assertThat(assistantMessage.role()).isEqualTo(BetaMessageParam.Role.ASSISTANT);

    final var blocks = assistantMessage.content().asBetaContentBlockParams();
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
    // Documents a known limitation: assistantParam() always emits `content` blocks (including any
    // ProviderContent server-tool blocks, in their original order) BEFORE appending `toolCalls` as
    // trailing tool_use blocks (see AnthropicMessageRequestConverter#assistantParam). This mirrors
    // the domain model split -- ProviderContent lives in `content`, client tool calls live in the
    // separate `toolCalls` list -- and is order-preserving *within* each of those two groups, but
    // NOT globally: a real Anthropic response that interleaves a client tool_use BETWEEN two server
    // blocks (e.g. server_tool_use, client tool_use, code_execution_tool_result) cannot be
    // reconstructed with that exact interleaving on the request side, since the domain model has
    // already split them into two ordered lists that don't record their relative position.
    //
    // This is a deliberate simplification, not a bug: Anthropic's own documented patterns for
    // code execution / Skills do not interleave a client tool_use in the middle of a server-tool
    // pair, so grouping (server blocks together, then client tool_use blocks) reproduces every
    // known real scenario. Revisit only if a genuine interleaving scenario is identified.
    final var serverToolUse =
        new ProviderContent(
            "anthropic",
            "server_tool_use",
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

    final var params = converter.toMessageCreateParams(ctx(model(null), null), snapshot, caps());

    final var blocks = params.messages().get(0).content().asBetaContentBlockParams();
    assertThat(blocks).hasSize(2);
    assertThat(blocks.get(0).serverToolUse()).isPresent();
    assertThat(blocks.get(1).toolUse().orElseThrow().id()).isEqualTo("toolu_1");
  }

  @Test
  void reEmitsThinkingBlockBeforeToolUseForPureReasoningTurn() {
    // Spec §9 item 4 / §1d: a thinking block re-emitted from ReasoningContent must precede the
    // tool_use block(s) appended from AssistantMessage#toolCalls -- assistantParam() always
    // emits `content` before `toolCalls`, so this holds by construction, but is worth pinning
    // down explicitly for the reasoning case since Anthropic requires thinking to lead an
    // assistant turn that also contains tool use.
    final var reasoning =
        new ReasoningContent(
            Map.of(
                "type", "thinking",
                "thinking", "Let me think it through",
                "signature", "sig-123"),
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

    final var params = converter.toMessageCreateParams(ctx(model(null), null), snapshot, caps());

    final var blocks = params.messages().get(0).content().asBetaContentBlockParams();
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
            Map.of(
                "type", "thinking",
                "thinking", "Let me think it through",
                "signature", "sig-123"),
            null);

    final var snapshot =
        new ConversationSnapshot(
            List.of(AssistantMessage.builder().content(List.of(reasoning)).build()), List.of());

    final var params = converter.toMessageCreateParams(ctx(model(null), null), snapshot, caps());

    final var blocks = params.messages().get(0).content().asBetaContentBlockParams();
    assertThat(blocks).hasSize(1);
    assertThat(blocks.get(0).isThinking()).isTrue();
  }

  @Test
  void defaultsMaxTokensFromCapabilitiesWhenConfigNull() {
    final var snapshot = new ConversationSnapshot(List.of(), List.of());

    final var params =
        converter.toMessageCreateParams(ctx(model(null), null), snapshot, caps(8192));

    assertThat(params.maxTokens()).isEqualTo(8192L);
  }

  @Test
  @SuppressWarnings(
      "deprecation") // temperature()/topP()/topK() deprecated in anthropic-java 2.48.0
  void usesConfiguredMaxTokensAndModelParams() {
    final var parameters = new AnthropicModelParameters(2048, 0.5, 0.9, 40, null, null, null);
    final var snapshot = new ConversationSnapshot(List.of(), List.of());
    final var response =
        new JobWorkerResponseConfiguration(new TextResponseFormatConfiguration(true), null, null);

    final var params =
        converter.toMessageCreateParams(ctx(model(parameters), response), snapshot, caps(8192));

    assertThat(params.maxTokens()).isEqualTo(2048L);
    assertThat(params.temperature()).contains(0.5);
    assertThat(params.topP()).contains(0.9);
    assertThat(params.topK()).contains(40L);
    // TEXT response format (parseJson or not) has no request-side effect.
    assertThat(params.outputConfig()).isEmpty();
  }

  @Test
  void configuresStructuredOutputFromJsonSchema() {
    final Map<String, Object> schema =
        Map.of("type", "object", "properties", Map.of("answer", Map.of("type", "string")));
    final var response =
        new JobWorkerResponseConfiguration(
            new JsonResponseFormatConfiguration(schema, "Answer"), null, null);
    final var snapshot = new ConversationSnapshot(List.of(), List.of());

    final var params =
        converter.toMessageCreateParams(ctx(model(null), response), snapshot, caps());

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
  void omitsMaxTokensFallbackConstantWhenBothNull() {
    final var snapshot = new ConversationSnapshot(List.of(), List.of());

    final var params = converter.toMessageCreateParams(ctx(model(null), null), snapshot, caps());

    assertThat(params.maxTokens()).isEqualTo(AnthropicMessageRequestConverter.DEFAULT_MAX_TOKENS);
    assertThat(params.maxTokens()).isEqualTo(4096L);
  }

  @Test
  void emitsNoContainerToolOrBetasWhenSkillsAreEmpty() {
    final var snapshot = new ConversationSnapshot(List.of(), List.of());

    final var params =
        converter.toMessageCreateParams(ctx(model(null, List.of()), null), snapshot, caps());

    assertThat(params.container()).isEmpty();
    assertThat(params.betas()).isEmpty();
    assertThat(params.tools()).isEmpty();
  }

  @Test
  void emitsNoContainerToolOrBetasWhenSkillsAreNull() {
    final var snapshot = new ConversationSnapshot(List.of(), List.of());

    final var params =
        converter.toMessageCreateParams(ctx(model(null, null), null), snapshot, caps());

    assertThat(params.container()).isEmpty();
    assertThat(params.betas()).isEmpty();
    assertThat(params.tools()).isEmpty();
  }

  @Test
  void emitsContainerSkillsCodeExecutionToolAndSkillBetaHeadersWhenSkillsConfigured() {
    final var snapshot = new ConversationSnapshot(List.of(), List.of());

    final var params =
        converter.toMessageCreateParams(
            ctx(model(null, List.of("pptx", "custom:my-skill:v2")), null), snapshot, caps());

    final var containerSkills =
        params.container().orElseThrow().asBetaContainerParams().skills().orElseThrow();
    assertThat(containerSkills).hasSize(2);
    assertThat(containerSkills.get(0).type().asString()).isEqualTo("anthropic");
    assertThat(containerSkills.get(0).skillId()).isEqualTo("pptx");
    assertThat(containerSkills.get(0).version()).contains("latest");
    assertThat(containerSkills.get(1).type().asString()).isEqualTo("custom");
    assertThat(containerSkills.get(1).skillId()).isEqualTo("my-skill");
    assertThat(containerSkills.get(1).version()).contains("v2");

    assertThat(params.tools()).isPresent();
    assertThat(params.tools().orElseThrow())
        .anyMatch(tool -> tool.codeExecutionTool20260521().isPresent());

    assertThat(params.betas()).isPresent();
    assertThat(params.betas().orElseThrow())
        .containsExactlyInAnyOrder(
            AnthropicBeta.SKILLS_2025_10_02, AnthropicBeta.FILES_API_2025_04_14);
  }

  @Test
  void combinesAutoAddedCodeExecutionToolWithUserConfiguredToolDefinitions() {
    // the auto-added code_execution tool is a distinct wire type (BetaCodeExecutionTool20260521)
    // from user-configured ToolDefinitions (BetaTool); both coexist on the wire regardless of
    // name overlap.
    final var snapshot =
        new ConversationSnapshot(
            List.of(),
            List.of(
                ToolDefinition.builder()
                    .name("code_execution")
                    .inputSchema(Map.of("type", "object"))
                    .build()));

    final var params =
        converter.toMessageCreateParams(ctx(model(null, List.of("pptx")), null), snapshot, caps());

    assertThat(params.tools().orElseThrow()).hasSize(2);
  }

  @Test
  void allToggleFalseAndNoSkillsEmitsNothingNew() {
    final var snapshot = new ConversationSnapshot(List.of(), List.of());

    final var params =
        converter.toMessageCreateParams(
            ctx(model(null, null, false, false, false), null), snapshot, caps());

    assertThat(params.container()).isEmpty();
    assertThat(params.betas()).isEmpty();
    assertThat(params.tools()).isEmpty();
  }

  @Test
  void enableCodeExecutionAddsLatestCodeExecutionToolWithoutBetaHeader() {
    final var snapshot = new ConversationSnapshot(List.of(), List.of());

    final var params =
        converter.toMessageCreateParams(
            ctx(model(null, null, true, null, null), null), snapshot, caps());

    assertThat(params.container()).isEmpty();
    assertThat(params.tools().orElseThrow())
        .hasSize(1)
        .anyMatch(tool -> tool.codeExecutionTool20260521().isPresent());
    assertThat(params.betas()).isEmpty();
  }

  @Test
  void enableWebSearchAddsWebSearchToolWithoutBetaHeader() {
    final var snapshot = new ConversationSnapshot(List.of(), List.of());

    final var params =
        converter.toMessageCreateParams(
            ctx(model(null, null, null, true, null), null), snapshot, caps());

    assertThat(params.tools().orElseThrow())
        .hasSize(1)
        .anyMatch(tool -> tool.webSearchTool20260318().isPresent());
    // web_search is GA: no anthropic-beta header required.
    assertThat(params.betas()).isEmpty();
  }

  @Test
  void enableWebFetchAddsWebFetchToolWithoutBetaHeader() {
    final var snapshot = new ConversationSnapshot(List.of(), List.of());

    final var params =
        converter.toMessageCreateParams(
            ctx(model(null, null, null, null, true), null), snapshot, caps());

    assertThat(params.tools().orElseThrow())
        .hasSize(1)
        .anyMatch(tool -> tool.webFetchTool20260318().isPresent());
    // web_fetch is GA: no anthropic-beta header required.
    assertThat(params.betas()).isEmpty();
  }

  @Test
  void nullWebSearchVersionDefaultsToLatestDynamicVersion() {
    final var snapshot = new ConversationSnapshot(List.of(), List.of());

    final var params =
        converter.toMessageCreateParams(
            ctx(model(null, null, null, true, null, null, null), null), snapshot, caps());

    assertThat(params.tools().orElseThrow())
        .anyMatch(tool -> tool.webSearchTool20260318().isPresent());
  }

  @Test
  void webSearchVersionOverrideSelectsRequestedTypedTool() {
    final var snapshot = new ConversationSnapshot(List.of(), List.of());

    final var params =
        converter.toMessageCreateParams(
            ctx(model(null, null, null, true, "web_search_20260318", null, null), null),
            snapshot,
            caps());

    assertThat(params.tools().orElseThrow())
        .anyMatch(tool -> tool.webSearchTool20260318().isPresent());
  }

  @Test
  void webFetchVersionOverrideSelectsRequestedTypedTool() {
    final var snapshot = new ConversationSnapshot(List.of(), List.of());

    final var params =
        converter.toMessageCreateParams(
            ctx(model(null, null, null, null, null, true, "web_fetch_20260318"), null),
            snapshot,
            caps());

    assertThat(params.tools().orElseThrow())
        .anyMatch(tool -> tool.webFetchTool20260318().isPresent());
  }

  @Test
  void unknownWebSearchVersionFallsBackToRawTypeOnBasicTool() {
    final var snapshot = new ConversationSnapshot(List.of(), List.of());

    final var params =
        converter.toMessageCreateParams(
            ctx(model(null, null, null, true, "web_search_29990101", null, null), null),
            snapshot,
            caps());

    final var toolNode = requestBodyAsJson(params).path("tools").get(0);
    assertThat(toolNode.path("type").asText()).isEqualTo("web_search_29990101");
    assertThat(toolNode.path("name").asText()).isEqualTo("web_search");
  }

  @Test
  void unknownWebFetchVersionFallsBackToRawTypeOnBasicTool() {
    final var snapshot = new ConversationSnapshot(List.of(), List.of());

    final var params =
        converter.toMessageCreateParams(
            ctx(model(null, null, null, null, null, true, "web_fetch_29990101"), null),
            snapshot,
            caps());

    final var toolNode = requestBodyAsJson(params).path("tools").get(0);
    assertThat(toolNode.path("type").asText()).isEqualTo("web_fetch_29990101");
    assertThat(toolNode.path("name").asText()).isEqualTo("web_fetch");
  }

  @Test
  void defaultWebSearchToolSerializesToLatestDynamicVersionWireShape() {
    // Serialization round-trip proving the default web_search tool's wire shape.
    final var snapshot = new ConversationSnapshot(List.of(), List.of());

    final var params =
        converter.toMessageCreateParams(
            ctx(model(null, null, null, true, null, null, null), null), snapshot, caps());

    final var toolNode = requestBodyAsJson(params).path("tools").get(0);
    assertThat(toolNode.path("type").asText()).isEqualTo("web_search_20260318");
    assertThat(toolNode.path("name").asText()).isEqualTo("web_search");
  }

  @Test
  void enableWebSearchAndWebFetchTogetherAddsBothTools() {
    final var snapshot = new ConversationSnapshot(List.of(), List.of());

    final var params =
        converter.toMessageCreateParams(
            ctx(model(null, null, null, true, true), null), snapshot, caps());

    assertThat(params.tools().orElseThrow()).hasSize(2);
    assertThat(params.tools().orElseThrow())
        .anyMatch(tool -> tool.webSearchTool20260318().isPresent());
    assertThat(params.tools().orElseThrow())
        .anyMatch(tool -> tool.webFetchTool20260318().isPresent());
    assertThat(params.betas()).isEmpty();
  }

  @Test
  void skillsPlusEnabledCodeExecutionToggleYieldsExactlyOneCodeExecutionToolAndNoDuplicateBeta() {
    final var snapshot = new ConversationSnapshot(List.of(), List.of());

    final var params =
        converter.toMessageCreateParams(
            ctx(model(null, List.of("pptx"), true, null, null), null), snapshot, caps());

    assertThat(params.tools().orElseThrow())
        .filteredOn(tool -> tool.codeExecutionTool20260521().isPresent())
        .hasSize(1);

    assertThat(params.betas().orElseThrow())
        .containsExactlyInAnyOrder(
            AnthropicBeta.SKILLS_2025_10_02, AnthropicBeta.FILES_API_2025_04_14);
  }

  @Test
  void failsLoudlyWhenMoreThanEightSkillsAreConfigured() {
    final var snapshot = new ConversationSnapshot(List.of(), List.of());
    final var skills = List.of("s1", "s2", "s3", "s4", "s5", "s6", "s7", "s8", "s9");

    assertThatThrownBy(
            () -> converter.toMessageCreateParams(ctx(model(null, skills), null), snapshot, caps()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("8");
  }

  @Test
  void defaultCodeExecutionVersionIsLatestGaWithoutBetaHeader() {
    final var snapshot = new ConversationSnapshot(List.of(), List.of());

    final var params =
        converter.toMessageCreateParams(
            ctx(model(null, null, true, null, null, null, null, null), null), snapshot, caps());

    assertThat(toolTypes(params)).contains("code_execution_20260521");
    assertThat(params.betas()).isEmpty();
  }

  @Test
  void legacyCodeExecutionVersionAddsBetaHeader() {
    final var snapshot = new ConversationSnapshot(List.of(), List.of());

    final var params =
        converter.toMessageCreateParams(
            ctx(model(null, null, true, "code_execution_20250522", null, null, null, null), null),
            snapshot,
            caps());

    assertThat(toolTypes(params)).contains("code_execution_20250522");
    assertThat(params.betas().orElseThrow())
        .anyMatch(beta -> "code-execution-2025-05-22".equals(beta.asString()));
  }

  @Test
  void gaCodeExecutionVersionOverridesSelectRequestedTypedToolWithoutBetaHeader() {
    final var snapshot = new ConversationSnapshot(List.of(), List.of());

    for (final String version :
        List.of("code_execution_20250825", "code_execution_20260120", "code_execution_20260521")) {
      final var params =
          converter.toMessageCreateParams(
              ctx(model(null, null, true, version, null, null, null, null), null),
              snapshot,
              caps());

      assertThat(toolTypes(params)).as(version).contains(version);
      assertThat(params.betas()).as(version).isEmpty();
    }
  }

  @Test
  void unknownCodeExecutionVersionFallsBackToRawTypeOnLatestTool() {
    final var snapshot = new ConversationSnapshot(List.of(), List.of());

    final var params =
        converter.toMessageCreateParams(
            ctx(model(null, null, true, "code_execution_29991231", null, null, null, null), null),
            snapshot,
            caps());

    assertThat(toolTypes(params)).contains("code_execution_29991231");
    assertThat(params.betas()).isEmpty();
  }

  @Test
  void skillsUseConfiguredCodeExecutionVersion() {
    final var snapshot = new ConversationSnapshot(List.of(), List.of());

    final var params =
        converter.toMessageCreateParams(
            ctx(
                model(
                    null, List.of("pptx"), null, "code_execution_20260120", null, null, null, null),
                null),
            snapshot,
            caps());

    assertThat(toolTypes(params)).contains("code_execution_20260120");
    assertThat(params.betas().orElseThrow())
        .contains(AnthropicBeta.SKILLS_2025_10_02, AnthropicBeta.FILES_API_2025_04_14)
        .noneMatch(beta -> "code-execution-2025-05-22".equals(beta.asString()));
  }

  @Test
  void defaultCodeExecutionToolSerializesToLatestWireShape() {
    // Serialization round-trip proving the default code_execution tool's wire shape (only "type"
    // differs for the raw escape hatch).
    final var snapshot = new ConversationSnapshot(List.of(), List.of());

    final var params =
        converter.toMessageCreateParams(
            ctx(model(null, null, true, null, null), null), snapshot, caps());

    final var toolNode = requestBodyAsJson(params).path("tools").get(0);
    assertThat(toolNode.path("type").asText()).isEqualTo("code_execution_20260521");
    assertThat(toolNode.path("name").asText()).isEqualTo("code_execution");
  }

  // --- Reasoning: thinking / effort mapping (Task 3) --------------------------------------

  private static AnthropicModelParameters thinkingParams(@Nullable AnthropicThinking thinking) {
    return new AnthropicModelParameters(null, null, null, null, null, null, thinking);
  }

  private static AnthropicModelParameters effortParams(
      @Nullable AnthropicEffort effort, @Nullable String customEffort) {
    return new AnthropicModelParameters(null, null, null, null, effort, customEffort, null);
  }

  @Test
  void mapsEnabledThinkingToWireThinkingConfigWithBudgetTokens() {
    final var parameters = thinkingParams(new AnthropicThinking(ThinkingMode.ENABLED, 2048, null));
    final var snapshot = new ConversationSnapshot(List.of(), List.of());
    final var caps = capsWithReasoning(List.of(ThinkingMode.ENABLED), List.of());

    final var params =
        converter.toMessageCreateParams(ctx(model(parameters), null), snapshot, caps);

    assertThat(params.thinking()).isPresent();
    assertThat(params.thinking().orElseThrow().isEnabled()).isTrue();
    assertThat(params.thinking().orElseThrow().asEnabled().budgetTokens()).isEqualTo(2048L);

    final var thinkingNode = requestBodyAsJson(params).path("thinking");
    assertThat(thinkingNode.path("type").asText()).isEqualTo("enabled");
    assertThat(thinkingNode.path("budget_tokens").asLong()).isEqualTo(2048L);
  }

  @Test
  void mapsAdaptiveThinkingWithSummarizedDisplayToLowercaseWireValue() {
    final var parameters =
        thinkingParams(
            new AnthropicThinking(ThinkingMode.ADAPTIVE, null, ThinkingDisplay.SUMMARIZED));
    final var snapshot = new ConversationSnapshot(List.of(), List.of());
    final var caps = capsWithReasoning(List.of(ThinkingMode.ADAPTIVE), List.of());

    final var params =
        converter.toMessageCreateParams(ctx(model(parameters), null), snapshot, caps);

    assertThat(params.thinking().orElseThrow().isAdaptive()).isTrue();
    assertThat(params.thinking().orElseThrow().asAdaptive().display())
        .contains(BetaThinkingConfigAdaptive.Display.SUMMARIZED);

    final var thinkingNode = requestBodyAsJson(params).path("thinking");
    assertThat(thinkingNode.path("type").asText()).isEqualTo("adaptive");
    assertThat(thinkingNode.path("display").asText()).isEqualTo("summarized");
  }

  @Test
  void mapsAdaptiveThinkingWithOmittedDisplayToLowercaseWireValue() {
    final var parameters =
        thinkingParams(new AnthropicThinking(ThinkingMode.ADAPTIVE, null, ThinkingDisplay.OMITTED));
    final var snapshot = new ConversationSnapshot(List.of(), List.of());
    final var caps = capsWithReasoning(List.of(ThinkingMode.ADAPTIVE), List.of());

    final var params =
        converter.toMessageCreateParams(ctx(model(parameters), null), snapshot, caps);

    final var thinkingNode = requestBodyAsJson(params).path("thinking");
    assertThat(thinkingNode.path("display").asText()).isEqualTo("omitted");
  }

  @Test
  void mapsAdaptiveThinkingWithoutDisplayEmitsNoDisplayField() {
    final var parameters = thinkingParams(new AnthropicThinking(ThinkingMode.ADAPTIVE, null, null));
    final var snapshot = new ConversationSnapshot(List.of(), List.of());
    final var caps = capsWithReasoning(List.of(ThinkingMode.ADAPTIVE), List.of());

    final var params =
        converter.toMessageCreateParams(ctx(model(parameters), null), snapshot, caps);

    assertThat(params.thinking().orElseThrow().asAdaptive().display()).isEmpty();
    assertThat(requestBodyAsJson(params).path("thinking").has("display")).isFalse();
  }

  @Test
  void mapsDisabledThinkingToWireThinkingConfig() {
    final var parameters = thinkingParams(new AnthropicThinking(ThinkingMode.DISABLED, null, null));
    final var snapshot = new ConversationSnapshot(List.of(), List.of());
    final var caps = capsWithReasoning(List.of(ThinkingMode.DISABLED), List.of());

    final var params =
        converter.toMessageCreateParams(ctx(model(parameters), null), snapshot, caps);

    assertThat(params.thinking().orElseThrow().isDisabled()).isTrue();
    assertThat(requestBodyAsJson(params).path("thinking").path("type").asText())
        .isEqualTo("disabled");
  }

  @Test
  void nullThinkingModeEmitsNoThinkingParamEvenWithoutReasoningCapabilities() {
    // A `thinking` object with a null `mode` (modeler left the dropdown blank) is unset: no
    // validation is triggered (even though caps() here declares no reasoning at all) and no
    // thinking param is mapped onto the wire request.
    final var parameters = thinkingParams(new AnthropicThinking(null, null, null));
    final var snapshot = new ConversationSnapshot(List.of(), List.of());

    final var params =
        converter.toMessageCreateParams(ctx(model(parameters), null), snapshot, caps());

    assertThat(params.thinking()).isEmpty();
  }

  @Test
  void budgetTokensOnlyWithNullModeEmitsNoThinkingParam() {
    // Task 2 review note: {thinking:{budgetTokens:...}} with a null mode must still emit no
    // thinking param (mode null means unset, regardless of any other field being populated).
    final var parameters = thinkingParams(new AnthropicThinking(null, 4096, null));
    final var snapshot = new ConversationSnapshot(List.of(), List.of());

    final var params =
        converter.toMessageCreateParams(ctx(model(parameters), null), snapshot, caps());

    assertThat(params.thinking()).isEmpty();
  }

  @Test
  void mapsEachEffortLevelToItsLowercaseWireValue() {
    final var snapshot = new ConversationSnapshot(List.of(), List.of());
    final var caps =
        capsWithReasoning(
            List.of(),
            List.of(
                AnthropicEffort.LOW,
                AnthropicEffort.MEDIUM,
                AnthropicEffort.HIGH,
                AnthropicEffort.XHIGH,
                AnthropicEffort.MAX));

    for (final var entry :
        Map.of(
                AnthropicEffort.LOW, "low",
                AnthropicEffort.MEDIUM, "medium",
                AnthropicEffort.HIGH, "high",
                AnthropicEffort.XHIGH, "xhigh",
                AnthropicEffort.MAX, "max")
            .entrySet()) {
      final var parameters = effortParams(entry.getKey(), null);
      final var params =
          converter.toMessageCreateParams(ctx(model(parameters), null), snapshot, caps);

      assertThat(params.outputConfig()).isPresent();
      assertThat(params.outputConfig().orElseThrow().effort().orElseThrow().asString())
          .isEqualTo(entry.getValue());
      assertThat(requestBodyAsJson(params).path("output_config").path("effort").asText())
          .isEqualTo(entry.getValue());
    }
  }

  @Test
  void customEffortSendsFreeTextValueVerbatim() {
    final var parameters = effortParams(AnthropicEffort.CUSTOM, "extra-verbose");
    final var snapshot = new ConversationSnapshot(List.of(), List.of());
    final var caps = capsWithReasoning(List.of(), List.of());

    final var params =
        converter.toMessageCreateParams(ctx(model(parameters), null), snapshot, caps);

    assertThat(params.outputConfig().orElseThrow().effort().orElseThrow().asString())
        .isEqualTo("extra-verbose");
    assertThat(requestBodyAsJson(params).path("output_config").path("effort").asText())
        .isEqualTo("extra-verbose");
  }

  @Test
  void effortAndJsonResponseFormatBothLandOnTheSameOutputConfigWithoutClobbering() {
    // Regression guard: MessageCreateParams.Builder#outputConfig(BetaOutputConfig) is a plain
    // setter that replaces the whole field, so effort and the JSON schema format must be combined
    // into a single BetaOutputConfig before being applied, or one would silently drop the other.
    final Map<String, Object> schema =
        Map.of("type", "object", "properties", Map.of("answer", Map.of("type", "string")));
    final var response =
        new JobWorkerResponseConfiguration(
            new JsonResponseFormatConfiguration(schema, "Answer"), null, null);
    final var parameters = effortParams(AnthropicEffort.HIGH, null);
    final var snapshot = new ConversationSnapshot(List.of(), List.of());
    final var caps = capsWithReasoning(List.of(), List.of(AnthropicEffort.HIGH));

    final var params =
        converter.toMessageCreateParams(ctx(model(parameters), response), snapshot, caps);

    final var outputConfigNode = requestBodyAsJson(params).path("output_config");
    assertThat(outputConfigNode.path("effort").asText()).isEqualTo("high");
    assertThat(outputConfigNode.path("format").path("type").asText()).isEqualTo("json_schema");
    assertThat(params.outputConfig().orElseThrow().format()).isPresent();
    assertThat(params.outputConfig().orElseThrow().effort()).isPresent();
  }

  @Test
  void failsFastWhenThinkingModeIsUnsupportedByTheMatchedModel() {
    final var parameters = thinkingParams(new AnthropicThinking(ThinkingMode.ENABLED, 2048, null));
    final var snapshot = new ConversationSnapshot(List.of(), List.of());
    final var caps = capsWithReasoning(List.of(ThinkingMode.ADAPTIVE), List.of());

    assertThatThrownBy(
            () -> converter.toMessageCreateParams(ctx(model(parameters), null), snapshot, caps))
        .isInstanceOf(ConnectorException.class);
  }

  @Test
  void failsFastWhenEffortIsSetButMatchedModelDeclaresNoReasoning() {
    final var parameters = effortParams(AnthropicEffort.HIGH, null);
    final var snapshot = new ConversationSnapshot(List.of(), List.of());

    assertThatThrownBy(
            () -> converter.toMessageCreateParams(ctx(model(parameters), null), snapshot, caps()))
        .isInstanceOf(ConnectorException.class);
  }

  @Test
  void unmatchedModelBypassesValidationAndStillMapsSuppliedThinkingConfig() {
    // An unmatched/custom model bypasses reasoning validation entirely (spec §6), but the wire
    // mapping itself is unconditional: whatever thinking config was supplied is still sent.
    final var parameters = thinkingParams(new AnthropicThinking(ThinkingMode.ENABLED, 2048, null));
    final var snapshot = new ConversationSnapshot(List.of(), List.of());
    // reasoning == null and an unsupported mode would normally fail rule 1/2 for a matched model.
    final var caps = caps();

    final var params =
        converter.toMessageCreateParams(ctx(model(parameters), null), snapshot, caps, false);

    assertThat(params.thinking().orElseThrow().isEnabled()).isTrue();
  }

  // --- Prompt caching (spike) ----------------------------------------------------------------

  @Test
  void promptCachingEnabledAddsTopLevelEphemeralCacheControl() {
    final var params =
        converter.toMessageCreateParams(
            ctx(promptCachingModel(true), null),
            new ConversationSnapshot(List.of(), List.of()),
            caps());

    final var cacheControl = requestBodyAsJson(params).path("cache_control");
    assertThat(cacheControl.isMissingNode()).as("cache_control present").isFalse();
    assertThat(cacheControl.path("type").asText()).isEqualTo("ephemeral");
  }

  @Test
  void promptCachingDisabledOrUnsetOmitsCacheControl() {
    for (final Boolean flag : new Boolean[] {null, Boolean.FALSE}) {
      final var params =
          converter.toMessageCreateParams(
              ctx(promptCachingModel(flag), null),
              new ConversationSnapshot(List.of(), List.of()),
              caps());

      assertThat(requestBodyAsJson(params).path("cache_control").isMissingNode())
          .as("cache_control omitted when flag=%s", flag)
          .isTrue();
    }
  }

  @Test
  void promptCachingReSendsEarlierPrefixByteIdenticallyAcrossTurns() {
    // The point of the spike: automatic caching caches the whole prefix (system + tools + earlier
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
    final List<Message> turn2Messages = new java.util.ArrayList<>(turn1Messages);
    turn2Messages.add(
        UserMessage.builder().content(List.of(TextContent.textContent("q2"))).build());

    final var turn1 =
        converter.toMessageCreateParams(
            ctx(promptCachingModel(true), null),
            new ConversationSnapshot(turn1Messages, tools),
            caps());
    final var turn2 =
        converter.toMessageCreateParams(
            ctx(promptCachingModel(true), null),
            new ConversationSnapshot(turn2Messages, tools),
            caps());

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
}
