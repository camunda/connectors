/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.framework.openai.family.responses;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openai.core.ObjectMappers;
import com.openai.models.responses.EasyInputMessage;
import com.openai.models.responses.ResponseCreateParams;
import io.camunda.connector.agenticai.aiagent.framework.api.LlmProviderChatModelApiConfiguration;
import io.camunda.connector.agenticai.aiagent.framework.capabilities.CoreModelCapabilities;
import io.camunda.connector.agenticai.aiagent.framework.capabilities.ModelCapabilities.Modality;
import io.camunda.connector.agenticai.aiagent.framework.openai.OpenAiContentConverter;
import io.camunda.connector.agenticai.aiagent.framework.openai.OpenAiModelCapabilities;
import io.camunda.connector.agenticai.aiagent.framework.openai.OpenAiReasoningCapabilities;
import io.camunda.connector.agenticai.aiagent.memory.ConversationSnapshot;
import io.camunda.connector.agenticai.aiagent.model.AgentConfiguration;
import io.camunda.connector.agenticai.aiagent.model.AgentExecutionContext;
import io.camunda.connector.agenticai.aiagent.model.message.AssistantMessage;
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
import io.camunda.connector.agenticai.aiagent.model.request.chatmodel.OpenAiApiFamily;
import io.camunda.connector.agenticai.aiagent.model.request.chatmodel.OpenAiChatModel;
import io.camunda.connector.agenticai.aiagent.model.request.chatmodel.OpenAiChatModel.OpenAiBackend.OpenAiDirectBackend;
import io.camunda.connector.agenticai.aiagent.model.request.chatmodel.OpenAiChatModel.OpenAiConnection;
import io.camunda.connector.agenticai.aiagent.model.request.chatmodel.OpenAiChatModel.OpenAiModel;
import io.camunda.connector.agenticai.aiagent.model.request.chatmodel.OpenAiChatModel.OpenAiModel.OpenAiModelParameters;
import io.camunda.connector.agenticai.aiagent.model.request.chatmodel.OpenAiEffort;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCall;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCallResultContent;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolDefinition;
import io.camunda.connector.api.error.ConnectorException;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

class OpenAiResponsesRequestConverterTest {

  private final ObjectMapper objectMapper = new ObjectMapper();
  private final OpenAiContentConverter contentConverter = new OpenAiContentConverter(objectMapper);
  private final OpenAiResponsesRequestConverter converter =
      new OpenAiResponsesRequestConverter(contentConverter, objectMapper);

  private static OpenAiChatModel model(@Nullable OpenAiModelParameters parameters) {
    return model(parameters, null, null);
  }

  private static OpenAiChatModel model(
      @Nullable OpenAiModelParameters parameters,
      @Nullable Boolean enableWebSearch,
      @Nullable Boolean enableCodeInterpreter) {
    return new OpenAiChatModel(
        new OpenAiConnection(
            OpenAiApiFamily.RESPONSES,
            new OpenAiDirectBackend("sk-test", null, null),
            new OpenAiModel("gpt-5", parameters),
            enableWebSearch,
            enableCodeInterpreter,
            null,
            null));
  }

  private static AgentExecutionContext ctx(
      OpenAiChatModel model, @Nullable ResponseConfiguration response) {
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

  private static OpenAiModelCapabilities caps() {
    return caps(null);
  }

  private static OpenAiModelCapabilities caps(@Nullable OpenAiReasoningCapabilities reasoning) {
    return new OpenAiModelCapabilities(
        new CoreModelCapabilities(
            List.of(Modality.TEXT), List.of(Modality.TEXT), List.of(Modality.TEXT), null, null),
        reasoning);
  }

  private static JsonNode requestBodyAsJson(ResponseCreateParams params) {
    return ObjectMappers.jsonMapper().valueToTree(params._body());
  }

  @Test
  void mapsSystemMessageToInstructions() {
    final var snapshot =
        new ConversationSnapshot(
            List.of(
                SystemMessage.builder().content(List.of(TextContent.textContent("sys"))).build(),
                UserMessage.builder().content(List.of(TextContent.textContent("hi"))).build()),
            List.of());

    final var params = converter.toResponseCreateParams(ctx(model(null), null), snapshot, caps());

    assertThat(params.instructions()).contains("sys");
  }

  @Test
  void mapsUserMessageTextToInputItem() {
    final var snapshot =
        new ConversationSnapshot(
            List.of(UserMessage.builder().content(List.of(TextContent.textContent("hi"))).build()),
            List.of());

    final var params = converter.toResponseCreateParams(ctx(model(null), null), snapshot, caps());

    final var items = params.input().orElseThrow().asResponse();
    assertThat(items).hasSize(1);

    final var easy = items.get(0).easyInputMessage().orElseThrow();
    assertThat(easy.role()).isEqualTo(EasyInputMessage.Role.USER);

    final var parts = easy.content().asResponseInputMessageContentList();
    assertThat(parts).hasSize(1);
    assertThat(parts.get(0).inputText().orElseThrow().text()).isEqualTo("hi");
  }

  @Test
  void mapsAssistantToolCallAndToolResultToFunctionCallAndOutput() {
    final var snapshot =
        new ConversationSnapshot(
            List.of(
                AssistantMessage.builder()
                    .toolCalls(
                        List.of(
                            ToolCall.builder()
                                .id("call_1")
                                .name("get_weather")
                                .arguments(Map.of("city", "Berlin"))
                                .build()))
                    .build(),
                ToolCallResultMessage.builder()
                    .results(
                        List.of(
                            ToolCallResultContent.builder()
                                .id("call_1")
                                .name("get_weather")
                                .content(List.of(TextContent.textContent("sunny")))
                                .build()))
                    .build()),
            List.of());

    final var params = converter.toResponseCreateParams(ctx(model(null), null), snapshot, caps());

    final var items = params.input().orElseThrow().asResponse();
    assertThat(items).hasSize(2);

    final var functionCall = items.get(0).functionCall().orElseThrow();
    assertThat(functionCall.callId()).isEqualTo("call_1");
    assertThat(functionCall.name()).isEqualTo("get_weather");
    assertThat(functionCall.arguments(Map.class)).containsEntry("city", "Berlin");

    final var functionCallOutput = items.get(1).functionCallOutput().orElseThrow();
    assertThat(functionCallOutput.callId()).isEqualTo("call_1");
    assertThat(functionCallOutput.output().asString()).isEqualTo("sunny");
  }

  @Test
  void mapsToolDefinitionsToFunctionTools() {
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
            List.of(),
            List.of(
                ToolDefinition.builder()
                    .name("SuperfluxProduct")
                    .description("desc")
                    .inputSchema(schema)
                    .build()));

    final var params = converter.toResponseCreateParams(ctx(model(null), null), snapshot, caps());

    assertThat(params.tools()).isPresent();
    final var tool = params.tools().orElseThrow().get(0).function().orElseThrow();
    assertThat(tool.name()).isEqualTo("SuperfluxProduct");
    assertThat(tool.description()).contains("desc");

    final var toolNode = requestBodyAsJson(params).path("tools").get(0);
    assertThat(toolNode.path("parameters").path("type").asText()).isEqualTo("object");
    assertThat(
            toolNode.path("parameters").path("properties").path("quantity").path("type").asText())
        .isEqualTo("integer");
    assertThat(toolNode.path("parameters").path("required").get(0).asText()).isEqualTo("quantity");
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
        converter.toResponseCreateParams(ctx(model(null), response), snapshot, caps());

    assertThat(params.text()).isPresent();

    final var textNode = requestBodyAsJson(params).path("text").path("format");
    assertThat(textNode.path("type").asText()).isEqualTo("json_schema");
    assertThat(textNode.path("name").asText()).isEqualTo("Answer");
    assertThat(textNode.path("strict").asBoolean()).isTrue();
    assertThat(textNode.path("schema").path("type").asText()).isEqualTo("object");
    assertThat(textNode.path("schema").path("properties").path("answer").path("type").asText())
        .isEqualTo("string");
  }

  @Test
  void mapsEffortToReasoningIncludeEncryptedContentAndDisablesStore() {
    final var parameters = new OpenAiModelParameters(null, null, null, OpenAiEffort.HIGH);
    final var caps = caps(new OpenAiReasoningCapabilities(List.of(OpenAiEffort.HIGH)));
    final var snapshot = new ConversationSnapshot(List.of(), List.of());

    final var params =
        converter.toResponseCreateParams(ctx(model(parameters), null), snapshot, caps);

    assertThat(params.reasoning()).isPresent();
    assertThat(params.reasoning().orElseThrow().effort().orElseThrow().asString())
        .isEqualTo("high");
    assertThat(params.include().orElseThrow())
        .contains(com.openai.models.responses.ResponseIncludable.REASONING_ENCRYPTED_CONTENT);
    assertThat(params.store()).contains(false);

    final var reasoningNode = requestBodyAsJson(params).path("reasoning");
    assertThat(reasoningNode.path("effort").asText()).isEqualTo("high");
  }

  @Test
  void throwsWhenValidatorRejectsUnsupportedEffort() {
    final var parameters = new OpenAiModelParameters(null, null, null, OpenAiEffort.HIGH);
    // caps() declares no reasoning capability at all -> effort configured but unsupported.
    final var snapshot = new ConversationSnapshot(List.of(), List.of());

    assertThatThrownBy(
            () -> converter.toResponseCreateParams(ctx(model(parameters), null), snapshot, caps()))
        .isInstanceOf(ConnectorException.class);
  }

  @Test
  void replaysReasoningContentProviderPayloadAsInputItem() {
    final var reasoning =
        new ReasoningContent(
            "Let me think it through",
            Map.of("type", "reasoning", "id", "rs_123", "summary", List.of()),
            null);
    final var snapshot =
        new ConversationSnapshot(
            List.of(AssistantMessage.builder().content(List.of(reasoning)).build()), List.of());

    final var params = converter.toResponseCreateParams(ctx(model(null), null), snapshot, caps());

    final var items = params.input().orElseThrow().asResponse();
    assertThat(items).hasSize(1);
    assertThat(items.get(0).reasoning().orElseThrow().id()).isEqualTo("rs_123");
  }

  @Test
  void skipsReasoningContentWithNullProviderPayload() {
    final var reasoning = new ReasoningContent("Let me think it through", null, null);
    final var snapshot =
        new ConversationSnapshot(
            List.of(AssistantMessage.builder().content(List.of(reasoning)).build()), List.of());

    final var params = converter.toResponseCreateParams(ctx(model(null), null), snapshot, caps());

    assertThat(params.input().orElseThrow().asResponse()).isEmpty();
  }

  @Test
  void replaysProviderContentPayloadAsInputItem() {
    final var providerContent =
        new ProviderContent(
            "openai", "item_reference", Map.of("type", "item_reference", "id", "ref_1"), null);
    final var snapshot =
        new ConversationSnapshot(
            List.of(AssistantMessage.builder().content(List.of(providerContent)).build()),
            List.of());

    final var params = converter.toResponseCreateParams(ctx(model(null), null), snapshot, caps());

    final var items = params.input().orElseThrow().asResponse();
    assertThat(items).hasSize(1);
    assertThat(items.get(0).itemReference().orElseThrow().id()).isEqualTo("ref_1");
  }

  @Test
  void enablesWebSearchAndCodeInterpreterServerTools() {
    final var snapshot = new ConversationSnapshot(List.of(), List.of());

    final var params =
        converter.toResponseCreateParams(ctx(model(null, true, true), null), snapshot, caps());

    assertThat(params.tools().orElseThrow()).hasSize(2);
    assertThat(params.tools().orElseThrow()).anyMatch(tool -> tool.webSearch().isPresent());
    assertThat(params.tools().orElseThrow()).anyMatch(tool -> tool.codeInterpreter().isPresent());

    final var toolTypes = new java.util.ArrayList<String>();
    requestBodyAsJson(params)
        .path("tools")
        .forEach(node -> toolTypes.add(node.path("type").asText()));
    assertThat(toolTypes).containsExactlyInAnyOrder("web_search", "code_interpreter");
  }
}
