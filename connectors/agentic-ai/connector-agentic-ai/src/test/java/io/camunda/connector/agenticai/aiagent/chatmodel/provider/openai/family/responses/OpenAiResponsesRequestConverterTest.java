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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openai.core.ObjectMappers;
import com.openai.models.responses.EasyInputMessage;
import com.openai.models.responses.ResponseCreateParams;
import io.camunda.connector.agenticai.aiagent.capabilities.CoreModelCapabilities;
import io.camunda.connector.agenticai.aiagent.capabilities.ModelCapabilities.Modality;
import io.camunda.connector.agenticai.aiagent.chatmodel.V2ChatModelApiConfiguration;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.openai.OpenAiContentConverter;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.openai.OpenAiModelCapabilities;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.openai.OpenAiReasoningCapabilities;
import io.camunda.connector.agenticai.aiagent.memory.ConversationSnapshot;
import io.camunda.connector.agenticai.aiagent.model.AgentConfiguration;
import io.camunda.connector.agenticai.aiagent.model.AgentExecutionContext;
import io.camunda.connector.agenticai.aiagent.model.message.AssistantMessage;
import io.camunda.connector.agenticai.aiagent.model.message.SystemMessage;
import io.camunda.connector.agenticai.aiagent.model.message.ToolCallResultMessage;
import io.camunda.connector.agenticai.aiagent.model.message.UserMessage;
import io.camunda.connector.agenticai.aiagent.model.message.content.DocumentContent;
import io.camunda.connector.agenticai.aiagent.model.message.content.ObjectContent;
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
import io.camunda.connector.api.document.Document;
import io.camunda.connector.api.document.DocumentMetadata;
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

  private static OpenAiChatModel modelWithBackend(OpenAiBackend backend) {
    return new OpenAiChatModel(
        new OpenAiConnection(
            OpenAiApiFamily.RESPONSES,
            backend,
            new OpenAiModel("gpt-5", null),
            null,
            null,
            null,
            null));
  }

  private static AgentExecutionContext ctx(
      OpenAiChatModel model, @Nullable ResponseConfiguration response) {
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

    final var params = converter.toResponseCreateParams(ctx(model(null), null), snapshot, caps());

    final var items = params.input().orElseThrow().asResponse();
    assertThat(items).hasSize(2);

    final var functionCallOutput = items.get(1).functionCallOutput().orElseThrow();
    assertThat(functionCallOutput.callId()).isEqualTo("call_1");
    // Must be the raw unwrapped value ("24"), not the polymorphic Content envelope
    // ("{"type":"object","content":24}") - see OpenAiResponsesRequestConverter#toTextOutput.
    assertThat(functionCallOutput.output().asString()).isEqualTo("24");
  }

  @Test
  void mapsToolResultDocumentToNativeFunctionCallOutputItem() {
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

    final var params = converter.toResponseCreateParams(ctx(model(null), null), snapshot, caps());

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

    final var params = converter.toResponseCreateParams(ctx(model(null), null), snapshot, caps());

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

    final var params = converter.toResponseCreateParams(ctx(model(null), null), snapshot, caps());

    final var items = params.input().orElseThrow().asResponse();
    assertThat(items).hasSize(2);

    final var easy = items.get(0).easyInputMessage().orElseThrow();
    assertThat(easy.role()).isEqualTo(EasyInputMessage.Role.ASSISTANT);
    assertThat(
            easy.content()
                .asResponseInputMessageContentList()
                .get(0)
                .inputText()
                .orElseThrow()
                .text())
        .isEqualTo("let me check that");

    final var functionCall = items.get(1).functionCall().orElseThrow();
    assertThat(functionCall.callId()).isEqualTo("call_1");
    assertThat(functionCall.name()).isEqualTo("get_weather");
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
            Map.of("type", "reasoning", "id", "rs_123", "summary", List.of()), null);
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
    final var reasoning = new ReasoningContent(null, null);
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
        converter.toResponseCreateParams(ctx(modelWithBackend(backend), null), snapshot, caps());

    final var body = requestBodyAsJson(params);
    assertThat(body.path("service_tier").asText()).isEqualTo("priority");
    assertThat(body.path("top_logprobs").asInt()).isEqualTo(5);
  }

  @Test
  void doesNotAddRequestParametersForDirectBackend() {
    final var snapshot = new ConversationSnapshot(List.of(), List.of());

    final var params = converter.toResponseCreateParams(ctx(model(null), null), snapshot, caps());

    final var body = requestBodyAsJson(params);
    assertThat(body.has("service_tier")).isFalse();
    assertThat(body.has("top_logprobs")).isFalse();
  }
}
