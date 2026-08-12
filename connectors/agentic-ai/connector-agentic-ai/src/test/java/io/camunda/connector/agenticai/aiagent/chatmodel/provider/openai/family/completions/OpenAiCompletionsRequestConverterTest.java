/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.chatmodel.provider.openai.family.completions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openai.core.ObjectMappers;
import com.openai.models.ReasoningEffort;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.openai.OpenAiContentConverter;
import io.camunda.connector.agenticai.aiagent.memory.ConversationSnapshot;
import io.camunda.connector.agenticai.aiagent.model.message.AssistantMessage;
import io.camunda.connector.agenticai.aiagent.model.message.SystemMessage;
import io.camunda.connector.agenticai.aiagent.model.message.ToolCallResultMessage;
import io.camunda.connector.agenticai.aiagent.model.message.UserMessage;
import io.camunda.connector.agenticai.aiagent.model.message.content.ObjectContent;
import io.camunda.connector.agenticai.aiagent.model.message.content.TextContent;
import io.camunda.connector.agenticai.aiagent.model.request.AgentTaskResponseConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.ResponseConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.ResponseFormatConfiguration.JsonResponseFormatConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.v2.OpenAiChatModelConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.v2.OpenAiChatModelConfiguration.OpenAiApi.OpenAiCompletionsApi;
import io.camunda.connector.agenticai.aiagent.model.request.v2.OpenAiChatModelConfiguration.OpenAiApi.OpenAiCompletionsApi.CompletionsParameters;
import io.camunda.connector.agenticai.aiagent.model.request.v2.OpenAiChatModelConfiguration.OpenAiApi.OpenAiResponsesApi;
import io.camunda.connector.agenticai.aiagent.model.request.v2.OpenAiChatModelConfiguration.OpenAiApi.OpenAiResponsesApi.ResponsesParameters;
import io.camunda.connector.agenticai.aiagent.model.request.v2.OpenAiChatModelConfiguration.OpenAiBackend;
import io.camunda.connector.agenticai.aiagent.model.request.v2.OpenAiChatModelConfiguration.OpenAiBackend.OpenAiApiBackend;
import io.camunda.connector.agenticai.aiagent.model.request.v2.OpenAiChatModelConfiguration.OpenAiBackend.OpenAiApiBackend.OpenAiApiConnection;
import io.camunda.connector.agenticai.aiagent.model.request.v2.OpenAiChatModelConfiguration.OpenAiBackend.OpenAiCustomBackend;
import io.camunda.connector.agenticai.aiagent.model.request.v2.OpenAiChatModelConfiguration.OpenAiBackend.OpenAiCustomBackend.CustomBackend;
import io.camunda.connector.agenticai.aiagent.model.request.v2.OpenAiChatModelConfiguration.OpenAiConnection;
import io.camunda.connector.agenticai.aiagent.model.request.v2.OpenAiChatModelConfiguration.OpenAiEffort;
import io.camunda.connector.agenticai.aiagent.model.request.v2.OpenAiChatModelConfiguration.OpenAiModel;
import io.camunda.connector.agenticai.aiagent.model.request.v2.OpenAiCustomEndpointAuthentication.ApiKeyAuthentication;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCall;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCallResultContent;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolDefinition;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

class OpenAiCompletionsRequestConverterTest {

  private final ObjectMapper objectMapper = new ObjectMapper();
  private final OpenAiContentConverter contentConverter = new OpenAiContentConverter(objectMapper);
  private final OpenAiCompletionsRequestConverter converter =
      new OpenAiCompletionsRequestConverter(contentConverter, objectMapper);

  private static OpenAiBackend defaultBackend() {
    return new OpenAiApiBackend(
        new OpenAiApiConnection("sk-test", null, null, null, null, null, null));
  }

  private static OpenAiChatModelConfiguration model(@Nullable CompletionsParameters parameters) {
    return modelWithBackend(defaultBackend(), parameters);
  }

  private static OpenAiChatModelConfiguration modelWithBackend(
      OpenAiBackend backend, @Nullable CompletionsParameters parameters) {
    return new OpenAiChatModelConfiguration(
        new OpenAiConnection(
            new OpenAiCompletionsApi(
                parameters != null
                    ? parameters
                    : new CompletionsParameters(null, null, null, null)),
            backend,
            new OpenAiModel("gpt-4o"),
            null));
  }

  private static OpenAiChatModelConfiguration responsesFamilyModel() {
    return new OpenAiChatModelConfiguration(
        new OpenAiConnection(
            new OpenAiResponsesApi(new ResponsesParameters(null, null, null, null)),
            defaultBackend(),
            new OpenAiModel("gpt-5"),
            null));
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

    final var params = converter.toRequest(model(null), null, snapshot);

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

    final var params = converter.toRequest(model(null), null, snapshot);

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

    final var params = converter.toRequest(model(null), null, snapshot);

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
  void unwrapsObjectContentInToolResults() {
    final var snapshot =
        new ConversationSnapshot(
            List.of(
                AssistantMessage.builder()
                    .toolCalls(
                        List.of(
                            ToolCall.builder()
                                .id("call_1")
                                .name("superflux_product")
                                .arguments(Map.of("a", 5, "b", 3))
                                .build()))
                    .build(),
                ToolCallResultMessage.builder()
                    .results(
                        List.of(
                            ToolCallResultContent.builder()
                                .id("call_1")
                                .name("superflux_product")
                                .content(List.of(ObjectContent.objectContent(24)))
                                .build()))
                    .build()),
            List.of());

    final var params = converter.toRequest(model(null), null, snapshot);

    final var tool = params.messages().get(1).asTool();
    assertThat(tool.toolCallId()).isEqualTo("call_1");
    // Must be the raw unwrapped value ("24"), not the polymorphic Content envelope
    // ("{"type":"object","content":24}") - see OpenAiCompletionsRequestConverter#toTextOutput.
    assertThat(tool.content().asText()).isEqualTo("24");
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

    final var params = converter.toRequest(model(null), null, snapshot);

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

    final var params = converter.toRequest(model(null), null, snapshot);

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
    final ResponseConfiguration response =
        new AgentTaskResponseConfiguration(
            new JsonResponseFormatConfiguration(schema, "Answer"), null);
    final var snapshot = new ConversationSnapshot(List.of(), List.of());

    final var params = converter.toRequest(model(null), response, snapshot);

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
    final var parameters = new CompletionsParameters(null, OpenAiEffort.HIGH, null, null);
    final var snapshot = new ConversationSnapshot(List.of(), List.of());

    final var params = converter.toRequest(model(parameters), null, snapshot);

    assertThat(params.reasoningEffort()).hasValue(ReasoningEffort.HIGH);
    assertThat(requestBodyAsJson(params).path("reasoning_effort").asText()).isEqualTo("high");
  }

  @Test
  void omitsReasoningEffortWhenNoneConfigured() {
    final var snapshot = new ConversationSnapshot(List.of(), List.of());

    final var params =
        converter.toRequest(
            model(new CompletionsParameters(null, null, null, null)), null, snapshot);

    assertThat(params.reasoningEffort()).isEmpty();
    assertThat(requestBodyAsJson(params).has("reasoning_effort")).isFalse();
  }

  @Test
  void mapsEachEffortLevelToItsLowercaseWireValue() {
    final var snapshot = new ConversationSnapshot(List.of(), List.of());

    for (final var entry :
        Map.of(
                OpenAiEffort.MINIMAL, "minimal",
                OpenAiEffort.LOW, "low",
                OpenAiEffort.MEDIUM, "medium",
                OpenAiEffort.HIGH, "high",
                OpenAiEffort.XHIGH, "xhigh",
                OpenAiEffort.MAX, "max")
            .entrySet()) {
      final var parameters = new CompletionsParameters(null, entry.getKey(), null, null);
      final var params = converter.toRequest(model(parameters), null, snapshot);

      assertThat(params.reasoningEffort().orElseThrow().asString()).isEqualTo(entry.getValue());
      assertThat(requestBodyAsJson(params).path("reasoning_effort").asText())
          .isEqualTo(entry.getValue());
    }
  }

  @Test
  void mapsModelParameters() {
    final var parameters = new CompletionsParameters(512, null, 0.5, 0.9);
    final var snapshot = new ConversationSnapshot(List.of(), List.of());

    final var params = converter.toRequest(model(parameters), null, snapshot);

    assertThat(params.model().asString()).isEqualTo("gpt-4o");
    assertThat(params.maxCompletionTokens()).contains(512L);
    assertThat(params.temperature()).contains(0.5);
    assertThat(params.topP()).contains(0.9);
  }

  /**
   * Regression test: {@code completions} itself (not just its fields) can be {@code null} - every
   * one of its fields is optional, so real job binding produces a {@code null} object, not one with
   * all-null fields, whenever a modeler leaves every option under the family unset (caught by e2e
   * running against the real job-input binding path).
   */
  @Test
  void handlesNullCompletionsParametersWithoutError() {
    final var config =
        new OpenAiChatModelConfiguration(
            new OpenAiConnection(
                new OpenAiCompletionsApi(null), defaultBackend(), new OpenAiModel("gpt-4o"), null));
    final var snapshot = new ConversationSnapshot(List.of(), List.of());

    final var params = converter.toRequest(config, null, snapshot);

    assertThat(params.reasoningEffort()).isEmpty();
    assertThat(params.maxCompletionTokens()).isEmpty();
    assertThat(params.temperature()).isEmpty();
    assertThat(params.topP()).isEmpty();
  }

  @Test
  void alwaysRequestsUsageOnStreamingRequests() {
    final var snapshot = new ConversationSnapshot(List.of(), List.of());

    final var params = converter.toRequest(model(null), null, snapshot);

    assertThat(params.streamOptions().orElseThrow().includeUsage()).contains(true);
    assertThat(requestBodyAsJson(params).path("stream_options").path("include_usage").asBoolean())
        .isTrue();
  }

  @Test
  void alwaysDisablesServerSideStorage() {
    final var snapshot = new ConversationSnapshot(List.of(), List.of());

    final var params = converter.toRequest(model(null), null, snapshot);

    assertThat(params.store()).contains(false);
  }

  @Test
  void mergesCustomBackendBodyPropertiesIntoRequestBody() {
    final var backend =
        new OpenAiCustomBackend(
            new CustomBackend(
                "https://example.test/v1",
                null,
                null,
                Map.of("service_tier", "priority", "top_logprobs", 5),
                new ApiKeyAuthentication("test-key")));
    final var snapshot = new ConversationSnapshot(List.of(), List.of());

    final var params = converter.toRequest(modelWithBackend(backend, null), null, snapshot);

    final var body = requestBodyAsJson(params);
    assertThat(body.path("service_tier").asText()).isEqualTo("priority");
    assertThat(body.path("top_logprobs").asInt()).isEqualTo(5);
  }

  @Test
  void mergesApiBackendBodyPropertiesIntoRequestBody() {
    final var backend =
        new OpenAiApiBackend(
            new OpenAiApiConnection(
                "sk-test", null, null, null, null, null, Map.of("service_tier", "priority")));
    final var snapshot = new ConversationSnapshot(List.of(), List.of());

    final var params = converter.toRequest(modelWithBackend(backend, null), null, snapshot);

    final var body = requestBodyAsJson(params);
    assertThat(body.path("service_tier").asText()).isEqualTo("priority");
  }

  @Test
  void doesNotAddBodyPropertiesWhenNoneConfigured() {
    final var snapshot = new ConversationSnapshot(List.of(), List.of());

    final var params = converter.toRequest(model(null), null, snapshot);

    final var body = requestBodyAsJson(params);
    assertThat(body.has("service_tier")).isFalse();
    assertThat(body.has("top_logprobs")).isFalse();
  }

  @Test
  void mergesCustomBackendHeadersAndQueryParametersAsAdditional() {
    final var backend =
        new OpenAiCustomBackend(
            new CustomBackend(
                "https://example.test/v1",
                Map.of("X-Custom-Header", "header-value"),
                Map.of("api-version", "2026-01-01"),
                null,
                new ApiKeyAuthentication("test-key")));
    final var snapshot = new ConversationSnapshot(List.of(), List.of());

    final var params = converter.toRequest(modelWithBackend(backend, null), null, snapshot);

    assertThat(params._additionalHeaders().values("X-Custom-Header"))
        .containsExactly("header-value");
    assertThat(params._additionalQueryParams().values("api-version")).containsExactly("2026-01-01");
  }

  @Test
  void mergesApiBackendHiddenHeadersAndQueryParametersAsAdditional() {
    final var backend =
        new OpenAiApiBackend(
            new OpenAiApiConnection(
                "sk-test",
                null,
                null,
                null,
                Map.of("X-Hidden-Header", "hidden-value"),
                Map.of("api-version", "2026-01-01"),
                null));
    final var snapshot = new ConversationSnapshot(List.of(), List.of());

    final var params = converter.toRequest(modelWithBackend(backend, null), null, snapshot);

    assertThat(params._additionalHeaders().values("X-Hidden-Header"))
        .containsExactly("hidden-value");
    assertThat(params._additionalQueryParams().values("api-version")).containsExactly("2026-01-01");
  }

  // --- Family guard --------------------------------------------------------------------------

  @Test
  void throwsWhenConfiguredWithResponsesApiFamily() {
    final var snapshot = new ConversationSnapshot(List.of(), List.of());

    assertThatThrownBy(() -> converter.toRequest(responsesFamilyModel(), null, snapshot))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
