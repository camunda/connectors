/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.framework.openai.family.completions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openai.core.ObjectMappers;
import com.openai.models.ReasoningEffort;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
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
import io.camunda.connector.agenticai.aiagent.model.message.content.TextContent;
import io.camunda.connector.agenticai.aiagent.model.request.JobWorkerResponseConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.PromptConfiguration.SystemPromptConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.PromptConfiguration.UserPromptConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.ResponseConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.ResponseFormatConfiguration.JsonResponseFormatConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.chatmodel.OpenAiApiFamily;
import io.camunda.connector.agenticai.aiagent.model.request.chatmodel.OpenAiChatModel;
import io.camunda.connector.agenticai.aiagent.model.request.chatmodel.OpenAiChatModel.CompatibleAuthentication.CompatibleApiKeyAuthentication;
import io.camunda.connector.agenticai.aiagent.model.request.chatmodel.OpenAiChatModel.OpenAiBackend;
import io.camunda.connector.agenticai.aiagent.model.request.chatmodel.OpenAiChatModel.OpenAiBackend.OpenAiCompatibleBackend;
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

class OpenAiCompletionsRequestConverterTest {

  private final ObjectMapper objectMapper = new ObjectMapper();
  private final OpenAiContentConverter contentConverter = new OpenAiContentConverter(objectMapper);
  private final OpenAiCompletionsRequestConverter converter =
      new OpenAiCompletionsRequestConverter(contentConverter, objectMapper);

  private static OpenAiChatModel model(@Nullable OpenAiModelParameters parameters) {
    return model(parameters, null, null);
  }

  private static OpenAiModelParameters parameters(@Nullable OpenAiEffort effort) {
    return new OpenAiModelParameters(null, null, null, effort);
  }

  private static OpenAiChatModel model(
      @Nullable OpenAiModelParameters parameters,
      @Nullable Boolean enableWebSearch,
      @Nullable Boolean enableCodeInterpreter) {
    return new OpenAiChatModel(
        new OpenAiConnection(
            OpenAiApiFamily.COMPLETIONS,
            new OpenAiDirectBackend("sk-test", null, null),
            new OpenAiModel("gpt-4o", parameters),
            enableWebSearch,
            enableCodeInterpreter,
            null,
            null));
  }

  private static OpenAiChatModel modelWithBackend(OpenAiBackend backend) {
    return new OpenAiChatModel(
        new OpenAiConnection(
            OpenAiApiFamily.COMPLETIONS,
            backend,
            new OpenAiModel("gpt-4o", null),
            null,
            null,
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

  private static JsonNode requestBodyAsJson(ChatCompletionCreateParams params) {
    return ObjectMappers.jsonMapper().valueToTree(params._body());
  }

  @Test
  void mapsSystemMessageToSystemMessage() {
    final var snapshot =
        new ConversationSnapshot(
            List.of(
                SystemMessage.builder().content(List.of(TextContent.textContent("sys"))).build(),
                UserMessage.builder().content(List.of(TextContent.textContent("hi"))).build()),
            List.of());

    final var params =
        converter.toChatCompletionCreateParams(ctx(model(null), null), snapshot, caps());

    assertThat(params.messages()).hasSize(2);
    final var system = params.messages().get(0).asSystem();
    assertThat(system.content().asText()).isEqualTo("sys");
  }

  @Test
  void mapsUserMessageTextToUserMessage() {
    final var snapshot =
        new ConversationSnapshot(
            List.of(UserMessage.builder().content(List.of(TextContent.textContent("hi"))).build()),
            List.of());

    final var params =
        converter.toChatCompletionCreateParams(ctx(model(null), null), snapshot, caps());

    assertThat(params.messages()).hasSize(1);
    final var user = params.messages().get(0).asUser();
    final var parts = user.content().asArrayOfContentParts();
    assertThat(parts).hasSize(1);
    assertThat(parts.get(0).text().orElseThrow().text()).isEqualTo("hi");
  }

  @Test
  void mapsAssistantToolCallAndToolResultToAssistantAndToolMessages() {
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

    final var params =
        converter.toChatCompletionCreateParams(ctx(model(null), null), snapshot, caps());

    assertThat(params.messages()).hasSize(2);

    final var assistant = params.messages().get(0).asAssistant();
    assertThat(assistant.toolCalls()).isPresent();
    final var toolCall = assistant.toolCalls().orElseThrow().get(0).asFunction();
    assertThat(toolCall.id()).isEqualTo("call_1");
    assertThat(toolCall.function().name()).isEqualTo("get_weather");
    assertThat(toolCall.function().arguments(Map.class)).containsEntry("city", "Berlin");

    final var tool = params.messages().get(1).asTool();
    assertThat(tool.toolCallId()).isEqualTo("call_1");
    assertThat(tool.content().asText()).isEqualTo("sunny");
  }

  @Test
  void replaysAssistantTextContentAsAssistantMessageContent() {
    final var snapshot =
        new ConversationSnapshot(
            List.of(
                AssistantMessage.builder()
                    .content(List.of(TextContent.textContent("here's the answer")))
                    .build()),
            List.of());

    final var params =
        converter.toChatCompletionCreateParams(ctx(model(null), null), snapshot, caps());

    assertThat(params.messages()).hasSize(1);
    final var assistant = params.messages().get(0).asAssistant();
    assertThat(assistant.content().orElseThrow().asText()).isEqualTo("here's the answer");
    assertThat(assistant.toolCalls()).isEmpty();
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

    final var params =
        converter.toChatCompletionCreateParams(ctx(model(null), null), snapshot, caps());

    assertThat(params.tools()).isPresent();
    final var tool = params.tools().orElseThrow().get(0).function().orElseThrow();
    assertThat(tool.function().name()).isEqualTo("SuperfluxProduct");
    assertThat(tool.function().description()).contains("desc");

    final var toolNode = requestBodyAsJson(params).path("tools").get(0);
    assertThat(toolNode.path("function").path("parameters").path("type").asText())
        .isEqualTo("object");
    assertThat(
            toolNode
                .path("function")
                .path("parameters")
                .path("properties")
                .path("quantity")
                .path("type")
                .asText())
        .isEqualTo("integer");
    assertThat(toolNode.path("function").path("parameters").path("required").get(0).asText())
        .isEqualTo("quantity");
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
        converter.toChatCompletionCreateParams(ctx(model(null), response), snapshot, caps());

    assertThat(params.responseFormat()).isPresent();

    final var formatNode = requestBodyAsJson(params).path("response_format");
    assertThat(formatNode.path("type").asText()).isEqualTo("json_schema");
    assertThat(formatNode.path("json_schema").path("name").asText()).isEqualTo("Answer");
    assertThat(formatNode.path("json_schema").path("strict").asBoolean()).isTrue();
    assertThat(formatNode.path("json_schema").path("schema").path("type").asText())
        .isEqualTo("object");
    assertThat(
            formatNode
                .path("json_schema")
                .path("schema")
                .path("properties")
                .path("answer")
                .path("type")
                .asText())
        .isEqualTo("string");
  }

  @Test
  void mapsConfiguredEffortToReasoningEffort() {
    final var caps = caps(new OpenAiReasoningCapabilities(List.of(OpenAiEffort.HIGH)));
    final var snapshot = new ConversationSnapshot(List.of(), List.of());

    final var params =
        converter.toChatCompletionCreateParams(
            ctx(model(parameters(OpenAiEffort.HIGH)), null), snapshot, caps);

    assertThat(params.reasoningEffort()).hasValue(ReasoningEffort.HIGH);
    assertThat(requestBodyAsJson(params).path("reasoning_effort").asText()).isEqualTo("high");
  }

  @Test
  void omitsReasoningEffortWhenNoneConfigured() {
    final var snapshot = new ConversationSnapshot(List.of(), List.of());

    final var params =
        converter.toChatCompletionCreateParams(
            ctx(model(parameters(null)), null), snapshot, caps());

    assertThat(params.reasoningEffort()).isEmpty();
    assertThat(requestBodyAsJson(params).has("reasoning_effort")).isFalse();
  }

  @Test
  void throwsWhenValidatorRejectsUnsupportedEffort() {
    // caps() declares no reasoning capability -> effort configured but unsupported, so the
    // validator rejects the request before any mapping happens.
    final var parameters = new OpenAiModelParameters(null, null, null, OpenAiEffort.HIGH);
    final var snapshot = new ConversationSnapshot(List.of(), List.of());

    assertThatThrownBy(
            () ->
                converter.toChatCompletionCreateParams(
                    ctx(model(parameters), null), snapshot, caps()))
        .isInstanceOf(ConnectorException.class);
  }

  @Test
  void validatorRejectsServerToolsOnCompletions() {
    final var snapshot = new ConversationSnapshot(List.of(), List.of());

    assertThatThrownBy(
            () ->
                converter.toChatCompletionCreateParams(
                    ctx(model(null, true, null), null), snapshot, caps()))
        .isInstanceOf(ConnectorException.class);
  }

  @Test
  void mapsModelParameters() {
    final var parameters = new OpenAiModelParameters(512, 0.5, 0.9, null);
    final var snapshot = new ConversationSnapshot(List.of(), List.of());

    final var params =
        converter.toChatCompletionCreateParams(ctx(model(parameters), null), snapshot, caps());

    assertThat(params.model().asString()).isEqualTo("gpt-4o");
    assertThat(params.maxCompletionTokens()).contains(512L);
    assertThat(params.temperature()).contains(0.5);
    assertThat(params.topP()).contains(0.9);
  }

  @Test
  void requestsUsageInStreamOptionsSinceCompletionsCallsAreAlwaysStreamed() {
    final var snapshot = new ConversationSnapshot(List.of(), List.of());

    final var params =
        converter.toChatCompletionCreateParams(ctx(model(null), null), snapshot, caps());

    assertThat(params.streamOptions().orElseThrow().includeUsage()).contains(true);
    assertThat(requestBodyAsJson(params).path("stream_options").path("include_usage").asBoolean())
        .isTrue();
  }

  @Test
  void mergesCompatibleBackendRequestParametersIntoRequestBody() {
    final var backend =
        new OpenAiCompatibleBackend(
            "https://example.test/v1",
            null,
            null,
            Map.of("service_tier", "priority", "top_logprobs", 5),
            new CompatibleApiKeyAuthentication("k"));
    final var snapshot = new ConversationSnapshot(List.of(), List.of());

    final var params =
        converter.toChatCompletionCreateParams(
            ctx(modelWithBackend(backend), null), snapshot, caps());

    final var body = requestBodyAsJson(params);
    assertThat(body.path("service_tier").asText()).isEqualTo("priority");
    assertThat(body.path("top_logprobs").asInt()).isEqualTo(5);
  }

  @Test
  void doesNotAddRequestParametersForDirectBackend() {
    final var snapshot = new ConversationSnapshot(List.of(), List.of());

    final var params =
        converter.toChatCompletionCreateParams(ctx(model(null), null), snapshot, caps());

    final var body = requestBodyAsJson(params);
    assertThat(body.has("service_tier")).isFalse();
    assertThat(body.has("top_logprobs")).isFalse();
  }
}
