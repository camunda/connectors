/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.chatmodel.provider.openai.family.responses;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openai.core.ObjectMappers;
import com.openai.models.ReasoningEffort;
import com.openai.models.responses.EasyInputMessage;
import com.openai.models.responses.ResponseCreateParams;
import com.openai.models.responses.ResponseIncludable;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.openai.OpenAiContentConverter;
import io.camunda.connector.agenticai.aiagent.memory.ConversationSnapshot;
import io.camunda.connector.agenticai.aiagent.model.message.AssistantMessage;
import io.camunda.connector.agenticai.aiagent.model.message.SystemMessage;
import io.camunda.connector.agenticai.aiagent.model.message.ToolCallResultMessage;
import io.camunda.connector.agenticai.aiagent.model.message.UserMessage;
import io.camunda.connector.agenticai.aiagent.model.message.content.DocumentContent;
import io.camunda.connector.agenticai.aiagent.model.message.content.ObjectContent;
import io.camunda.connector.agenticai.aiagent.model.message.content.ProviderContent;
import io.camunda.connector.agenticai.aiagent.model.message.content.ReasoningContent;
import io.camunda.connector.agenticai.aiagent.model.message.content.TextContent;
import io.camunda.connector.agenticai.aiagent.model.request.AgentTaskResponseConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.ResponseFormatConfiguration.JsonResponseFormatConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.v2.OpenAiChatModelConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.v2.OpenAiChatModelConfiguration.OpenAiApi.OpenAiCompletionsApi;
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
import io.camunda.connector.agenticai.aiagent.model.request.v2.shared.CustomEndpointAuthentication.NoAuthentication;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCall;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCallResultContent;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolDefinition;
import io.camunda.connector.api.document.Document;
import io.camunda.connector.api.document.DocumentMetadata;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

class OpenAiResponsesRequestConverterTest {

  private final ObjectMapper objectMapper = new ObjectMapper();
  private final OpenAiContentConverter contentConverter = new OpenAiContentConverter(objectMapper);
  private final OpenAiResponsesRequestConverter converter =
      new OpenAiResponsesRequestConverter(contentConverter, objectMapper);

  private static OpenAiBackend defaultBackend() {
    return new OpenAiApiBackend(
        new OpenAiApiConnection("sk-test", null, null, null, null, null, null));
  }

  private static OpenAiChatModelConfiguration model(@Nullable ResponsesParameters parameters) {
    return modelWithBackend(defaultBackend(), parameters);
  }

  private static OpenAiChatModelConfiguration modelWithBackend(
      OpenAiBackend backend, @Nullable ResponsesParameters parameters) {
    return new OpenAiChatModelConfiguration(
        new OpenAiConnection(
            new OpenAiResponsesApi(
                parameters != null ? parameters : new ResponsesParameters(null, null, null, null)),
            backend,
            new OpenAiModel("gpt-5"),
            null));
  }

  private static OpenAiChatModelConfiguration completionsFamilyModel() {
    return new OpenAiChatModelConfiguration(
        new OpenAiConnection(
            new OpenAiCompletionsApi(
                new OpenAiCompletionsApi.CompletionsParameters(null, null, null, null)),
            defaultBackend(),
            new OpenAiModel("gpt-5"),
            null));
  }

  private static JsonNode requestBodyAsJson(ResponseCreateParams params) {
    return ObjectMappers.jsonMapper().valueToTree(params._body());
  }

  private List<Map<String, Object>> rawInputItems(ResponseCreateParams params) {
    return objectMapper.convertValue(
        requestBodyAsJson(params).path("input"), new TypeReference<>() {});
  }

  @Test
  void mapsSystemMessageToInstructions() {
    final var snapshot =
        new ConversationSnapshot(
            List.of(
                SystemMessage.builder().content(List.of(TextContent.textContent("sys"))).build(),
                UserMessage.builder().content(List.of(TextContent.textContent("hi"))).build()),
            List.of());

    final var params = converter.toRequest(model(null), null, snapshot);

    assertThat(params.instructions()).contains("sys");
  }

  @Test
  void mapsUserMessageTextToInputItem() {
    final var snapshot =
        new ConversationSnapshot(
            List.of(UserMessage.builder().content(List.of(TextContent.textContent("hi"))).build()),
            List.of());

    final var params = converter.toRequest(model(null), null, snapshot);

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

    final var params = converter.toRequest(model(null), null, snapshot);

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
  void unwrapsObjectContentToolResultToRawValue() {
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

    final var items = params.input().orElseThrow().asResponse();
    final var functionCallOutput = items.get(1).functionCallOutput().orElseThrow();
    // Must be the raw unwrapped value ("24"), not the polymorphic Content envelope
    // ("{"type":"object","content":24}") - see OpenAiResponsesRequestConverter#toTextOutput.
    assertThat(functionCallOutput.output().asString()).isEqualTo("24");
  }

  @Test
  void emitsNativeFileItemsForToolResultDocuments() {
    final var document = mock(Document.class);
    final var metadata = mock(DocumentMetadata.class);
    when(document.metadata()).thenReturn(metadata);
    when(metadata.getContentType()).thenReturn("application/pdf");
    when(metadata.getFileName()).thenReturn("report.pdf");
    when(document.asBase64()).thenReturn("UERGQ09OVEVOVA==");

    final var snapshot =
        new ConversationSnapshot(
            List.of(
                ToolCallResultMessage.builder()
                    .results(
                        List.of(
                            ToolCallResultContent.builder()
                                .id("call_1")
                                .name("fetch_report")
                                .content(
                                    List.of(
                                        TextContent.textContent("here is the report"),
                                        new DocumentContent(document, null)))
                                .build()))
                    .build()),
            List.of());

    final var params = converter.toRequest(model(null), null, snapshot);

    final var items = params.input().orElseThrow().asResponse();
    assertThat(items).hasSize(1);

    final var output = items.get(0).functionCallOutput().orElseThrow().output();
    final var outputItems = output.asResponseFunctionCallOutputItemList();
    assertThat(outputItems).hasSize(2);
    assertThat(outputItems.get(0).isInputText()).isTrue();
    assertThat(outputItems.get(0).asInputText().text()).isEqualTo("here is the report");
    assertThat(outputItems.get(1).isInputFile()).isTrue();
    final var file = outputItems.get(1).asInputFile();
    assertThat(file.filename()).hasValue("report.pdf");
    assertThat(file.fileData()).hasValue("data:application/pdf;base64,UERGQ09OVEVOVA==");
  }

  @Test
  void replaysAssistantTextContentAsAssistantMessageInputItem() {
    final var snapshot =
        new ConversationSnapshot(
            List.of(
                AssistantMessage.builder()
                    .content(List.of(TextContent.textContent("here's the answer")))
                    .build()),
            List.of());

    final var params = converter.toRequest(model(null), null, snapshot);

    final var items = params.input().orElseThrow().asResponse();
    assertThat(items).hasSize(1);

    final var easy = items.get(0).easyInputMessage().orElseThrow();
    assertThat(easy.role()).isEqualTo(EasyInputMessage.Role.ASSISTANT);

    final var parts = easy.content().asResponseInputMessageContentList();
    assertThat(parts).hasSize(1);
    assertThat(parts.get(0).inputText().orElseThrow().text()).isEqualTo("here's the answer");
  }

  @Test
  void replaysAssistantTextContentAndToolCallAsSeparateInputItems() {
    final var snapshot =
        new ConversationSnapshot(
            List.of(
                AssistantMessage.builder()
                    .content(List.of(TextContent.textContent("let me check that")))
                    .toolCalls(
                        List.of(
                            ToolCall.builder()
                                .id("call_1")
                                .name("get_weather")
                                .arguments(Map.of("city", "Berlin"))
                                .build()))
                    .build()),
            List.of());

    final var params = converter.toRequest(model(null), null, snapshot);

    final var items = params.input().orElseThrow().asResponse();
    assertThat(items).hasSize(2);

    final var easy = items.get(0).easyInputMessage().orElseThrow();
    assertThat(easy.role()).isEqualTo(EasyInputMessage.Role.ASSISTANT);

    final var functionCall = items.get(1).functionCall().orElseThrow();
    assertThat(functionCall.callId()).isEqualTo("call_1");
  }

  @Test
  void replaysAssistantTurnInReasoningThenContentThenToolCallOrder() {
    // Assistant-turn replay order must match the order the model produced these items in:
    // reasoning/provider-content first, then plain content, then client tool calls.
    final var reasoningPayload =
        Map.<String, Object>of("type", "reasoning", "id", "rs_1", "summary", List.of());
    final var reasoning = new ReasoningContent("openai", reasoningPayload, null, Map.of());

    final var snapshot =
        new ConversationSnapshot(
            List.of(
                AssistantMessage.builder()
                    .content(List.of(reasoning, TextContent.textContent("here's the answer")))
                    .toolCalls(
                        List.of(
                            ToolCall.builder()
                                .id("call_1")
                                .name("get_weather")
                                .arguments(Map.of("city", "Berlin"))
                                .build()))
                    .build()),
            List.of());

    final var params = converter.toRequest(model(null), null, snapshot);

    final var items = params.input().orElseThrow().asResponse();
    assertThat(items).hasSize(3);
    assertThat(items.get(0).reasoning()).isPresent();
    assertThat(items.get(1).easyInputMessage()).isPresent();
    assertThat(items.get(2).functionCall()).isPresent();
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
        new AgentTaskResponseConfiguration(
            new JsonResponseFormatConfiguration(schema, "Answer"), null);
    final var snapshot = new ConversationSnapshot(List.of(), List.of());

    final var params = converter.toRequest(model(null), response, snapshot);

    assertThat(params.text()).isPresent();

    final var textNode = requestBodyAsJson(params).path("text").path("format");
    assertThat(textNode.path("type").asText()).isEqualTo("json_schema");
    assertThat(textNode.path("name").asText()).isEqualTo("Answer");
    assertThat(textNode.path("strict").asBoolean()).isTrue();
    assertThat(textNode.path("schema").path("type").asText()).isEqualTo("object");
    assertThat(textNode.path("schema").path("properties").path("answer").path("type").asText())
        .isEqualTo("string");
  }

  // --- Reasoning / effort ------------------------------------------------------------------

  @Test
  void requestsEncryptedReasoningAndDisablesServerSideStoreWhenEffortSet() {
    final var parameters = new ResponsesParameters(null, OpenAiEffort.HIGH, null, null);
    final var snapshot = new ConversationSnapshot(List.of(), List.of());

    final var params = converter.toRequest(model(parameters), null, snapshot);

    assertThat(params.store()).contains(false);
    assertThat(params.include().orElseThrow())
        .contains(ResponseIncludable.REASONING_ENCRYPTED_CONTENT);
    assertThat(params.reasoning().orElseThrow().effort()).contains(ReasoningEffort.HIGH);
  }

  @Test
  void omitsReasoningEntirelyWhenEffortUnset() {
    final var snapshot = new ConversationSnapshot(List.of(), List.of());

    final var params = converter.toRequest(model(null), null, snapshot);

    assertThat(params.reasoning()).isEmpty();
    assertThat(params.store()).isEmpty();
    assertThat(params.include()).isEmpty();
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
      final var parameters = new ResponsesParameters(null, entry.getKey(), null, null);
      final var params = converter.toRequest(model(parameters), null, snapshot);

      assertThat(params.reasoning().orElseThrow().effort().orElseThrow().asString())
          .isEqualTo(entry.getValue());
      assertThat(requestBodyAsJson(params).path("reasoning").path("effort").asText())
          .isEqualTo(entry.getValue());
    }
  }

  // --- Reasoning / provider-content replay --------------------------------------------------

  @Test
  void replaysReasoningContentByteIdentically() {
    final var payload =
        Map.<String, Object>of(
            "type", "reasoning", "id", "rs_1", "encrypted_content", "abc123", "summary", List.of());

    final var snapshot =
        new ConversationSnapshot(
            List.of(
                AssistantMessage.builder()
                    .content(List.of(new ReasoningContent("openai", payload, null, Map.of())))
                    .build()),
            List.of());

    final var params = converter.toRequest(model(null), null, snapshot);

    assertThat(rawInputItems(params)).contains(payload);
  }

  @Test
  void replaysProviderContentPayloadAsInputItem() {
    final var providerContent =
        new ProviderContent("openai", Map.of("type", "item_reference", "id", "ref_1"), null);
    final var snapshot =
        new ConversationSnapshot(
            List.of(AssistantMessage.builder().content(List.of(providerContent)).build()),
            List.of());

    final var params = converter.toRequest(model(null), null, snapshot);

    final var items = params.input().orElseThrow().asResponse();
    assertThat(items).hasSize(1);
    assertThat(items.get(0).itemReference().orElseThrow().id()).isEqualTo("ref_1");
  }

  // --- Backend request parameters ------------------------------------------------------------

  @Test
  void mergesCustomBackendRequestParametersIntoRequestBody() {
    final var backend =
        new OpenAiCustomBackend(
            new CustomBackend(
                "https://example.test/v1",
                null,
                null,
                Map.of("service_tier", "priority", "top_logprobs", 5),
                new NoAuthentication()));
    final var snapshot = new ConversationSnapshot(List.of(), List.of());

    final var params = converter.toRequest(modelWithBackend(backend, null), null, snapshot);

    final var body = requestBodyAsJson(params);
    assertThat(body.path("service_tier").asText()).isEqualTo("priority");
    assertThat(body.path("top_logprobs").asInt()).isEqualTo(5);
  }

  @Test
  void mergesApiBackendRequestParametersIntoRequestBody() {
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
  void doesNotAddRequestParametersWhenNoneConfigured() {
    final var snapshot = new ConversationSnapshot(List.of(), List.of());

    final var params = converter.toRequest(model(null), null, snapshot);

    final var body = requestBodyAsJson(params);
    assertThat(body.has("service_tier")).isFalse();
    assertThat(body.has("top_logprobs")).isFalse();
  }

  // --- Family guard --------------------------------------------------------------------------

  @Test
  void throwsWhenConfiguredWithCompletionsApiFamily() {
    final var snapshot = new ConversationSnapshot(List.of(), List.of());

    assertThatThrownBy(() -> converter.toRequest(completionsFamilyModel(), null, snapshot))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
