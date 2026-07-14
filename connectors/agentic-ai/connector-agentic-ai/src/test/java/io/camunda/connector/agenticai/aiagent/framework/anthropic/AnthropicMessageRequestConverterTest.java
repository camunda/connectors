/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.framework.anthropic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.anthropic.core.JsonValue;
import com.anthropic.core.ObjectMappers;
import com.anthropic.models.beta.AnthropicBeta;
import com.anthropic.models.beta.messages.BetaMessageParam;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.agenticai.aiagent.framework.api.LlmProviderChatModelApiConfiguration;
import io.camunda.connector.agenticai.aiagent.framework.capabilities.CoreModelCapabilities;
import io.camunda.connector.agenticai.aiagent.framework.capabilities.ModelCapabilities.Modality;
import io.camunda.connector.agenticai.aiagent.memory.ConversationSnapshot;
import io.camunda.connector.agenticai.aiagent.model.AgentConfiguration;
import io.camunda.connector.agenticai.aiagent.model.AgentExecutionContext;
import io.camunda.connector.agenticai.aiagent.model.message.AssistantMessage;
import io.camunda.connector.agenticai.aiagent.model.message.SystemMessage;
import io.camunda.connector.agenticai.aiagent.model.message.ToolCallResultMessage;
import io.camunda.connector.agenticai.aiagent.model.message.UserMessage;
import io.camunda.connector.agenticai.aiagent.model.message.content.ProviderContent;
import io.camunda.connector.agenticai.aiagent.model.message.content.TextContent;
import io.camunda.connector.agenticai.aiagent.model.request.JobWorkerResponseConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.PromptConfiguration.SystemPromptConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.PromptConfiguration.UserPromptConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.ResponseConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.ResponseFormatConfiguration.JsonResponseFormatConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.ResponseFormatConfiguration.TextResponseFormatConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.chatmodel.AnthropicChatModel;
import io.camunda.connector.agenticai.aiagent.model.request.chatmodel.AnthropicChatModel.AnthropicBackend.AnthropicDirectBackend;
import io.camunda.connector.agenticai.aiagent.model.request.chatmodel.AnthropicChatModel.AnthropicConnection;
import io.camunda.connector.agenticai.aiagent.model.request.chatmodel.AnthropicChatModel.AnthropicModel;
import io.camunda.connector.agenticai.aiagent.model.request.chatmodel.AnthropicChatModel.AnthropicModel.AnthropicModelParameters;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCall;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCallResultContent;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolDefinition;
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
        parameters, skills, enableCodeExecution, enableWebSearch, null, enableWebFetch, null);
  }

  private static AnthropicChatModel model(
      @Nullable AnthropicModelParameters parameters,
      @Nullable List<String> skills,
      @Nullable Boolean enableCodeExecution,
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
            enableWebSearch,
            webSearchVersion,
            enableWebFetch,
            webFetchVersion));
  }

  private static AgentExecutionContext ctx(
      AnthropicChatModel model, @Nullable ResponseConfiguration response) {
    final var configuration =
        new AgentConfiguration(
            new LlmProviderChatModelApiConfiguration(model),
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

  private static AnthropicModelCapabilities caps() {
    return caps(null);
  }

  private static AnthropicModelCapabilities caps(@Nullable Integer maxOutputTokens) {
    return new AnthropicModelCapabilities(
        new CoreModelCapabilities(
            List.of(Modality.TEXT),
            List.of(Modality.TEXT),
            List.of(Modality.TEXT),
            null,
            maxOutputTokens),
        false,
        false,
        false,
        false);
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
    final var parameters = new AnthropicModelParameters(2048, 0.5, 0.9, 40);
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
  void emitsContainerSkillsCodeExecutionToolAndBetaHeadersWhenSkillsConfigured() {
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
        .anyMatch(tool -> tool.codeExecutionTool20250825().isPresent());

    assertThat(params.betas()).isPresent();
    assertThat(params.betas().orElseThrow())
        .contains(AnthropicBeta.SKILLS_2025_10_02, AnthropicBeta.FILES_API_2025_04_14)
        .anyMatch(beta -> "code-execution-2025-08-25".equals(beta.asString()));
  }

  @Test
  void combinesAutoAddedCodeExecutionToolWithUserConfiguredToolDefinitions() {
    // the auto-added code_execution tool is a distinct wire type (BetaCodeExecutionTool20250825)
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
  void enableCodeExecutionAddsCodeExecutionToolAndBetaHeaderWithoutSkills() {
    final var snapshot = new ConversationSnapshot(List.of(), List.of());

    final var params =
        converter.toMessageCreateParams(
            ctx(model(null, null, true, null, null), null), snapshot, caps());

    assertThat(params.container()).isEmpty();
    assertThat(params.tools().orElseThrow())
        .hasSize(1)
        .anyMatch(tool -> tool.codeExecutionTool20250825().isPresent());
    assertThat(params.betas().orElseThrow())
        .anyMatch(beta -> "code-execution-2025-08-25".equals(beta.asString()));
  }

  @Test
  void enableWebSearchAddsWebSearchToolWithoutBetaHeader() {
    final var snapshot = new ConversationSnapshot(List.of(), List.of());

    final var params =
        converter.toMessageCreateParams(
            ctx(model(null, null, null, true, null), null), snapshot, caps());

    assertThat(params.tools().orElseThrow())
        .hasSize(1)
        .anyMatch(tool -> tool.webSearchTool20250305().isPresent());
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
        .anyMatch(tool -> tool.webFetchTool20250910().isPresent());
    // web_fetch is GA: no anthropic-beta header required.
    assertThat(params.betas()).isEmpty();
  }

  @Test
  void nullWebSearchVersionDefaultsToBasicDirectVersion() {
    final var snapshot = new ConversationSnapshot(List.of(), List.of());

    final var params =
        converter.toMessageCreateParams(
            ctx(model(null, null, null, true, null, null, null), null), snapshot, caps());

    assertThat(params.tools().orElseThrow())
        .anyMatch(tool -> tool.webSearchTool20250305().isPresent());
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
  void defaultWebSearchToolSerializesToBasicDirectVersionWireShape() {
    // Serialization round-trip proving the default web_search tool's wire shape, which is also
    // what the raw-type escape hatch relies on for unknown versions (only "type" differs).
    final var snapshot = new ConversationSnapshot(List.of(), List.of());

    final var params =
        converter.toMessageCreateParams(
            ctx(model(null, null, null, true, null, null, null), null), snapshot, caps());

    final var toolNode = requestBodyAsJson(params).path("tools").get(0);
    assertThat(toolNode.path("type").asText()).isEqualTo("web_search_20250305");
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
        .anyMatch(tool -> tool.webSearchTool20250305().isPresent());
    assertThat(params.tools().orElseThrow())
        .anyMatch(tool -> tool.webFetchTool20250910().isPresent());
    assertThat(params.betas()).isEmpty();
  }

  @Test
  void skillsPlusEnabledCodeExecutionToggleYieldsExactlyOneCodeExecutionToolAndNoDuplicateBeta() {
    final var snapshot = new ConversationSnapshot(List.of(), List.of());

    final var params =
        converter.toMessageCreateParams(
            ctx(model(null, List.of("pptx"), true, null, null), null), snapshot, caps());

    assertThat(params.tools().orElseThrow())
        .filteredOn(tool -> tool.codeExecutionTool20250825().isPresent())
        .hasSize(1);

    final var codeExecutionBetaOccurrences =
        params.betas().orElseThrow().stream()
            .filter(beta -> "code-execution-2025-08-25".equals(beta.asString()))
            .count();
    assertThat(codeExecutionBetaOccurrences).isEqualTo(1);
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
}
