/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.chatmodel.provider.gemini;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.connector.agenticai.aiagent.memory.ConversationSnapshot;
import io.camunda.connector.agenticai.aiagent.model.message.AssistantMessage;
import io.camunda.connector.agenticai.aiagent.model.message.SystemMessage;
import io.camunda.connector.agenticai.aiagent.model.message.ToolCallResultMessage;
import io.camunda.connector.agenticai.aiagent.model.message.UserMessage;
import io.camunda.connector.agenticai.aiagent.model.message.content.DocumentContent;
import io.camunda.connector.agenticai.aiagent.model.message.content.TextContent;
import io.camunda.connector.agenticai.aiagent.model.request.AgentTaskResponseConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.ResponseFormatConfiguration.JsonResponseFormatConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.ResponseFormatConfiguration.TextResponseFormatConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.v2.GeminiChatModelConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.v2.GeminiChatModelConfiguration.GeminiBackend.GeminiApiBackend;
import io.camunda.connector.agenticai.aiagent.model.request.v2.GeminiChatModelConfiguration.GeminiBackend.GeminiApiBackend.GoogleGeminiApi;
import io.camunda.connector.agenticai.aiagent.model.request.v2.GeminiChatModelConfiguration.GeminiConnection;
import io.camunda.connector.agenticai.aiagent.model.request.v2.GeminiChatModelConfiguration.GeminiModel;
import io.camunda.connector.agenticai.aiagent.model.request.v2.GeminiChatModelConfiguration.GeminiModel.GeminiModelParameters;
import io.camunda.connector.agenticai.aiagent.model.request.v2.GeminiChatModelConfiguration.GeminiModel.GeminiThinking;
import io.camunda.connector.agenticai.aiagent.model.request.v2.GeminiChatModelConfiguration.GeminiModel.GeminiThinkingLevel;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCall;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCallResult;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCallResultContent;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolDefinition;
import io.camunda.connector.agenticai.testutil.TestObjectMapperSupplier;
import io.camunda.connector.api.document.Document;
import io.camunda.connector.api.document.DocumentMetadata;
import io.camunda.connector.api.error.ConnectorException;
import io.camunda.connector.document.jackson.DocumentReferenceModel.ExternalDocumentReferenceModel;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class GeminiContentRequestConverterTest {

  private final GeminiContentConverter contentConverter =
      new GeminiContentConverter(TestObjectMapperSupplier.INSTANCE);
  private final GeminiContentRequestConverter converter =
      new GeminiContentRequestConverter(contentConverter);

  private static GeminiChatModelConfiguration model(@Nullable GeminiModelParameters parameters) {
    return new GeminiChatModelConfiguration(
        new GeminiConnection(
            new GeminiApiBackend(new GoogleGeminiApi("gm-test", null)),
            new GeminiModel("gemini-3-pro-preview", parameters),
            null));
  }

  private static Document mockDocument(String contentType, byte[] bytes) {
    final var document = Mockito.mock(Document.class);
    final var metadata = Mockito.mock(DocumentMetadata.class);
    Mockito.when(document.metadata()).thenReturn(metadata);
    Mockito.when(metadata.getContentType()).thenReturn(contentType);
    Mockito.when(document.asByteArray()).thenReturn(bytes);
    // stubbed so a tool-result document (flattened to a JSON reference) can serialize this mock
    // via JacksonModuleDocumentSerializer, which dispatches on Document#reference()
    Mockito.when(document.reference())
        .thenReturn(new ExternalDocumentReferenceModel("https://example.com/document", "document"));
    return document;
  }

  // --- Model parameters -------------------------------------------------------------------------

  @Test
  void mapsModelParameters() {
    final var parameters = new GeminiModelParameters(1024, 0.5, 0.9, 40, null);
    final var snapshot = new ConversationSnapshot(List.of(), List.of());

    final var config = converter.toGenerateContentConfig(model(parameters), null, snapshot);

    assertThat(config.maxOutputTokens()).contains(1024);
    assertThat(config.temperature()).contains(0.5f);
    assertThat(config.topP()).contains(0.9f);
    assertThat(config.topK()).contains(40f);
  }

  @Test
  void omitsUnsetModelParameters() {
    final var snapshot = new ConversationSnapshot(List.of(), List.of());

    final var config = converter.toGenerateContentConfig(model(null), null, snapshot);

    assertThat(config.maxOutputTokens()).isEmpty();
    assertThat(config.temperature()).isEmpty();
    assertThat(config.topP()).isEmpty();
    assertThat(config.topK()).isEmpty();
    assertThat(config.thinkingConfig()).isEmpty();
  }

  // --- Thinking
  // -----------------------------------------------------------------------------------

  @Test
  void mapsThinkingBudgetOnly() {
    final var parameters =
        new GeminiModelParameters(null, null, null, null, new GeminiThinking(true, 2048, null));
    final var snapshot = new ConversationSnapshot(List.of(), List.of());

    final var config = converter.toGenerateContentConfig(model(parameters), null, snapshot);

    assertThat(config.thinkingConfig()).isPresent();
    assertThat(config.thinkingConfig().orElseThrow().thinkingBudget()).contains(2048);
    assertThat(config.thinkingConfig().orElseThrow().thinkingLevel()).isEmpty();
    // without includeThoughts, thinking would be billed but never returned
    assertThat(config.thinkingConfig().orElseThrow().includeThoughts()).contains(true);
  }

  @Test
  void mapsThinkingLevelOnly() {
    final var parameters =
        new GeminiModelParameters(
            null, null, null, null, new GeminiThinking(true, null, GeminiThinkingLevel.HIGH));
    final var snapshot = new ConversationSnapshot(List.of(), List.of());

    final var config = converter.toGenerateContentConfig(model(parameters), null, snapshot);

    assertThat(config.thinkingConfig()).isPresent();
    assertThat(config.thinkingConfig().orElseThrow().thinkingBudget()).isEmpty();
    assertThat(config.thinkingConfig().orElseThrow().thinkingLevel().orElseThrow().toString())
        .isEqualToIgnoringCase("high");
    assertThat(config.thinkingConfig().orElseThrow().includeThoughts()).contains(true);
  }

  @Test
  void mapsThinkingLevelMinimal() {
    final var parameters =
        new GeminiModelParameters(
            null, null, null, null, new GeminiThinking(true, null, GeminiThinkingLevel.MINIMAL));
    final var snapshot = new ConversationSnapshot(List.of(), List.of());

    final var config = converter.toGenerateContentConfig(model(parameters), null, snapshot);

    assertThat(config.thinkingConfig().orElseThrow().thinkingLevel().orElseThrow().toString())
        .isEqualToIgnoringCase("minimal");
  }

  @Test
  void mapsUnconfiguredThinkingLevelToExplicitModelDefault() {
    final var parameters =
        new GeminiModelParameters(null, null, null, null, new GeminiThinking(true, null, null));
    final var snapshot = new ConversationSnapshot(List.of(), List.of());

    final var config = converter.toGenerateContentConfig(model(parameters), null, snapshot);

    assertThat(config.thinkingConfig()).isPresent();
    assertThat(config.thinkingConfig().orElseThrow().thinkingBudget()).isEmpty();
    assertThat(config.thinkingConfig().orElseThrow().thinkingLevel().orElseThrow().toString())
        .isEqualToIgnoringCase("THINKING_LEVEL_UNSPECIFIED");
    assertThat(config.thinkingConfig().orElseThrow().includeThoughts()).contains(true);
  }

  @Test
  void bothThinkingBudgetAndLevelSetThrows() {
    // Defensive check: the config record's own @AssertFalse should already prevent this, but the
    // converter guards it too rather than silently picking one (constructed directly here,
    // bypassing bean validation, to exercise that defense).
    final var parameters =
        new GeminiModelParameters(
            null, null, null, null, new GeminiThinking(true, 2048, GeminiThinkingLevel.HIGH));
    final var snapshot = new ConversationSnapshot(List.of(), List.of());

    assertThatThrownBy(() -> converter.toGenerateContentConfig(model(parameters), null, snapshot))
        .isInstanceOf(ConnectorException.class);
  }

  @Test
  void budgetWithModelDefaultLevelDoesNotThrow() {
    final var parameters =
        new GeminiModelParameters(
            null,
            null,
            null,
            null,
            new GeminiThinking(true, 2048, GeminiThinkingLevel.MODEL_DEFAULT));
    final var snapshot = new ConversationSnapshot(List.of(), List.of());

    final var config = converter.toGenerateContentConfig(model(parameters), null, snapshot);

    assertThat(config.thinkingConfig().orElseThrow().thinkingBudget()).contains(2048);
  }

  @Test
  void noThinkingConfiguredEmitsNoThinkingConfig() {
    final var parameters = new GeminiModelParameters(null, null, null, null, null);
    final var snapshot = new ConversationSnapshot(List.of(), List.of());

    final var config = converter.toGenerateContentConfig(model(parameters), null, snapshot);

    assertThat(config.thinkingConfig()).isEmpty();
  }

  @Test
  void thinkingNotEnabledEmitsNoThinkingConfigEvenIfBudgetOrLevelSet() {
    final var parameters =
        new GeminiModelParameters(
            null, null, null, null, new GeminiThinking(null, 2048, GeminiThinkingLevel.HIGH));
    final var snapshot = new ConversationSnapshot(List.of(), List.of());

    // Constructed directly (bypassing bean validation) purely to prove the `enabled` gate is
    // checked before the mutual-exclusivity check -- an unrealistic combination otherwise.
    final var config = converter.toGenerateContentConfig(model(parameters), null, snapshot);

    assertThat(config.thinkingConfig()).isEmpty();
  }

  // --- System prompt hoisting -----------------------------------------------------------------

  @Test
  void hoistsLeadingSystemMessageToSystemInstructionAndExcludesItFromContents() {
    final var snapshot =
        new ConversationSnapshot(
            List.of(
                SystemMessage.builder().content(List.of(TextContent.textContent("sys"))).build(),
                UserMessage.builder().content(List.of(TextContent.textContent("hi"))).build()),
            List.of());

    final var config = converter.toGenerateContentConfig(model(null), null, snapshot);
    final var contents = converter.toContents(snapshot);

    assertThat(config.systemInstruction()).isPresent();
    assertThat(config.systemInstruction().orElseThrow().text()).isEqualTo("sys");

    assertThat(contents).hasSize(1);
    assertThat(contents.get(0).role()).contains("user");
    assertThat(contents.get(0).text()).isEqualTo("hi");
  }

  @Test
  void noSystemMessageEmitsNoSystemInstruction() {
    final var snapshot =
        new ConversationSnapshot(
            List.of(UserMessage.builder().content(List.of(TextContent.textContent("hi"))).build()),
            List.of());

    final var config = converter.toGenerateContentConfig(model(null), null, snapshot);

    assertThat(config.systemInstruction()).isEmpty();
  }

  // --- Message-history mapping
  // --------------------------------------------------------------------

  @Test
  void mapsUserMessageToUserRoleContent() {
    final var snapshot =
        new ConversationSnapshot(
            List.of(UserMessage.builder().content(List.of(TextContent.textContent("hi"))).build()),
            List.of());

    final var contents = converter.toContents(snapshot);

    assertThat(contents).hasSize(1);
    assertThat(contents.get(0).role()).contains("user");
    assertThat(contents.get(0).text()).isEqualTo("hi");
  }

  @Test
  void mapsAssistantMessageToModelRoleContentWithToolCallAsFunctionCallPart() {
    final var snapshot =
        new ConversationSnapshot(
            List.of(
                AssistantMessage.builder()
                    .content(List.of(TextContent.textContent("calling tool")))
                    .toolCalls(
                        List.of(
                            ToolCall.builder()
                                .id("call-1")
                                .name("getWeather")
                                .arguments(Map.of("city", "Berlin"))
                                .build()))
                    .build()),
            List.of());

    final var contents = converter.toContents(snapshot);

    assertThat(contents).hasSize(1);
    final var content = contents.get(0);
    assertThat(content.role()).contains("model");
    assertThat(content.parts().orElseThrow()).hasSize(2);
    assertThat(content.parts().orElseThrow().get(0).text()).contains("calling tool");

    final var functionCall = content.parts().orElseThrow().get(1).functionCall().orElseThrow();
    assertThat(functionCall.id()).contains("call-1");
    assertThat(functionCall.name()).contains("getWeather");
    assertThat(functionCall.args().orElseThrow()).isEqualTo(Map.of("city", "Berlin"));
  }

  @Test
  void restoresThoughtSignatureOnReplayedFunctionCallPartWhenPresentOnToolCallMetadata() {
    final var signatureBytes = "sig-bytes".getBytes(StandardCharsets.UTF_8);
    final var metadata =
        Map.<String, Object>of(
            GeminiChatModelConfiguration.GOOGLE_GEMINI_ID,
            Map.of(
                GeminiContentConverter.THOUGHT_SIGNATURE_METADATA_KEY,
                Base64.getEncoder().encodeToString(signatureBytes)));
    final var snapshot =
        new ConversationSnapshot(
            List.of(
                AssistantMessage.builder()
                    .toolCalls(
                        List.of(
                            ToolCall.builder()
                                .id("call-1")
                                .name("getWeather")
                                .arguments(Map.of("city", "Berlin"))
                                .metadata(metadata)
                                .build()))
                    .build()),
            List.of());

    final var contents = converter.toContents(snapshot);

    final var functionCallPart = contents.get(0).parts().orElseThrow().get(0);
    assertThat(functionCallPart.functionCall()).isPresent();
    assertThat(functionCallPart.thoughtSignature().orElseThrow()).isEqualTo(signatureBytes);
  }

  @Test
  void omitsThoughtSignatureOnReplayedFunctionCallPartWhenAbsentFromToolCallMetadata() {
    final var snapshot =
        new ConversationSnapshot(
            List.of(
                AssistantMessage.builder()
                    .toolCalls(
                        List.of(
                            ToolCall.builder()
                                .id("call-1")
                                .name("getWeather")
                                .arguments(Map.of("city", "Berlin"))
                                .build()))
                    .build()),
            List.of());

    final var contents = converter.toContents(snapshot);

    final var functionCallPart = contents.get(0).parts().orElseThrow().get(0);
    assertThat(functionCallPart.functionCall()).isPresent();
    assertThat(functionCallPart.thoughtSignature()).isEmpty();
  }

  @Test
  void mapsToolCallResultMessageToUserRoleContentWithFunctionResponsePart() {
    final var snapshot =
        new ConversationSnapshot(
            List.of(
                ToolCallResultMessage.builder()
                    .results(
                        List.of(
                            ToolCallResultContent.builder()
                                .id("call-1")
                                .name("getWeather")
                                .content(List.of(TextContent.textContent("sunny")))
                                .build()))
                    .build()),
            List.of());

    final var contents = converter.toContents(snapshot);

    assertThat(contents).hasSize(1);
    final var content = contents.get(0);
    assertThat(content.role()).contains("user");
    assertThat(content.parts().orElseThrow()).hasSize(1);

    final var functionResponse =
        content.parts().orElseThrow().get(0).functionResponse().orElseThrow();
    assertThat(functionResponse.id()).contains("call-1");
    assertThat(functionResponse.name()).contains("getWeather");
    assertThat(functionResponse.response().orElseThrow()).isEqualTo(Map.of("output", "sunny"));
  }

  @Test
  void mergesMultiPartToolResultIntoASingleFunctionResponsePart() {
    final var snapshot =
        new ConversationSnapshot(
            List.of(
                ToolCallResultMessage.builder()
                    .results(
                        List.of(
                            ToolCallResultContent.builder()
                                .id("call-1")
                                .name("multiPart")
                                .content(
                                    List.of(
                                        TextContent.textContent("a"), TextContent.textContent("b")))
                                .build()))
                    .build()),
            List.of());

    final var contents = converter.toContents(snapshot);

    assertThat(contents).hasSize(1);
    final var parts = contents.get(0).parts().orElseThrow();
    assertThat(parts).hasSize(1);

    final var functionResponse = parts.get(0).functionResponse().orElseThrow();
    assertThat(functionResponse.id()).contains("call-1");
    assertThat(functionResponse.name()).contains("multiPart");
    assertThat(functionResponse.response().orElseThrow())
        .isEqualTo(Map.of("output", List.of("a", "b")));
  }

  @Test
  void toolResultWithTextAndDocumentContentMergesIntoSingleFunctionResponse() {
    final var doc = mockDocument("image/png", "ABC".getBytes(StandardCharsets.UTF_8));
    final var snapshot =
        new ConversationSnapshot(
            List.of(
                ToolCallResultMessage.builder()
                    .results(
                        List.of(
                            ToolCallResultContent.builder()
                                .id("call-1")
                                .name("withImage")
                                .content(
                                    List.of(
                                        TextContent.textContent("described"),
                                        new DocumentContent(doc, null)))
                                .build()))
                    .build()),
            List.of());

    final var contents = converter.toContents(snapshot);

    final var parts = contents.get(0).parts().orElseThrow();
    assertThat(parts).hasSize(1);

    // the document is flattened to a JSON reference rather than embedded natively (see
    // GeminiContentConverter#toFunctionResponseParts), but still wrapped in a functionResponse
    // like the text content, so both merge into one functionResponse instead of leaving the
    // document as an uncorrelated sibling part.
    final var functionResponse = parts.get(0).functionResponse().orElseThrow();
    assertThat(functionResponse.id()).contains("call-1");
    assertThat(functionResponse.response().orElseThrow().get("output"))
        .isEqualTo(
            List.of(
                "described",
                "{\"url\":\"https://example.com/document\",\"name\":\"document\","
                    + "\"camunda.document.type\":\"external\"}"));
  }

  @Test
  void toolResultWithOnlyDocumentContentStillClosesOutTheFunctionCall() {
    final var doc = mockDocument("image/png", "ABC".getBytes(StandardCharsets.UTF_8));
    final var snapshot =
        new ConversationSnapshot(
            List.of(
                ToolCallResultMessage.builder()
                    .results(
                        List.of(
                            ToolCallResultContent.builder()
                                .id("call-1")
                                .name("readDocument")
                                .content(List.of(new DocumentContent(doc, null)))
                                .build()))
                    .build()),
            List.of());

    final var contents = converter.toContents(snapshot);

    final var parts = contents.get(0).parts().orElseThrow();
    assertThat(parts).hasSize(1);

    // a document-only result must still produce a functionResponse carrying name/id - a bare
    // text Part here would never correlate with the preceding functionCall
    final var functionResponse = parts.get(0).functionResponse().orElseThrow();
    assertThat(functionResponse.id()).contains("call-1");
    assertThat(functionResponse.name()).contains("readDocument");
    assertThat(functionResponse.response().orElseThrow().get("output"))
        .isEqualTo(
            "{\"url\":\"https://example.com/document\",\"name\":\"document\","
                + "\"camunda.document.type\":\"external\"}");
  }

  @Test
  void toolResultWithEmptyContentStillClosesOutTheFunctionCall() {
    final var snapshot =
        new ConversationSnapshot(
            List.of(
                ToolCallResultMessage.builder()
                    .results(
                        List.of(
                            ToolCallResultContent.builder()
                                .id("call-1")
                                .name("noOpTool")
                                .content(List.of())
                                .build()))
                    .build()),
            List.of());

    final var contents = converter.toContents(snapshot);

    final var parts = contents.get(0).parts().orElseThrow();
    assertThat(parts).hasSize(1);

    // a null/blank tool result normalizes to an empty content list (ToolCallResultContent
    // #contentFromObject); without a fallback, that produces no parts at all here, leaving the
    // preceding functionCall without a correlated response
    final var functionResponse = parts.get(0).functionResponse().orElseThrow();
    assertThat(functionResponse.id()).contains("call-1");
    assertThat(functionResponse.name()).contains("noOpTool");
    assertThat(functionResponse.response().orElseThrow().get("output"))
        .isEqualTo(ToolCallResult.CONTENT_NO_RESULT);
  }

  @Test
  void emptySnapshotReturnsEmptyContentsListNotNull() {
    final var contents = converter.toContents(new ConversationSnapshot(List.of(), List.of()));

    assertThat(contents).isNotNull();
    assertThat(contents).isEmpty();
  }

  @Test
  void assistantMessageWithNoContentAndNoToolCallsEmitsNoContent() {
    final var snapshot =
        new ConversationSnapshot(List.of(AssistantMessage.builder().build()), List.of());

    final var contents = converter.toContents(snapshot);

    assertThat(contents).isEmpty();
  }

  // --- Tools ----------------------------------------------------------------------------------

  @Test
  void mapsToolDefinitionsToASingleToolWithFunctionDeclarations() {
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

    final var config = converter.toGenerateContentConfig(model(null), null, snapshot);

    assertThat(config.tools()).isPresent();
    assertThat(config.tools().orElseThrow()).hasSize(1);

    final var declarations =
        config.tools().orElseThrow().get(0).functionDeclarations().orElseThrow();
    assertThat(declarations).hasSize(1);
    assertThat(declarations.get(0).name()).contains("SuperfluxProduct");
    assertThat(declarations.get(0).description()).contains("desc");
    assertThat(declarations.get(0).parametersJsonSchema()).contains(schema);
  }

  @Test
  void noToolDefinitionsEmitsNoTools() {
    final var snapshot = new ConversationSnapshot(List.of(), List.of());

    final var config = converter.toGenerateContentConfig(model(null), null, snapshot);

    assertThat(config.tools()).isEmpty();
  }

  // --- Structured output -----------------------------------------------------------------------

  @Test
  void configuresStructuredOutputFromJsonSchema() {
    final Map<String, Object> schema =
        Map.of("type", "object", "properties", Map.of("answer", Map.of("type", "string")));
    final var response =
        new AgentTaskResponseConfiguration(
            new JsonResponseFormatConfiguration(schema, "Answer"), null);
    final var snapshot = new ConversationSnapshot(List.of(), List.of());

    final var config = converter.toGenerateContentConfig(model(null), response, snapshot);

    assertThat(config.responseMimeType()).contains("application/json");
    assertThat(config.responseSchema()).isEmpty();
    assertThat(config.responseJsonSchema()).contains(schema);

    // Prove the raw schema actually survives SDK serialization, not just the in-memory getter.
    final String json = config.toJson();
    assertThat(json).contains("\"answer\"");
    assertThat(json).contains("\"responseMimeType\":\"application/json\"");
  }

  @Test
  void configuresJsonResponseFormatWithoutSchema() {
    final var response =
        new AgentTaskResponseConfiguration(new JsonResponseFormatConfiguration(null, null), null);
    final var snapshot = new ConversationSnapshot(List.of(), List.of());

    final var config = converter.toGenerateContentConfig(model(null), response, snapshot);

    assertThat(config.responseMimeType()).contains("application/json");
    assertThat(config.responseJsonSchema()).isEmpty();
  }

  @Test
  void textResponseFormatHasNoRequestSideEffect() {
    final var response =
        new AgentTaskResponseConfiguration(new TextResponseFormatConfiguration(true), null);
    final var snapshot = new ConversationSnapshot(List.of(), List.of());

    final var config = converter.toGenerateContentConfig(model(null), response, snapshot);

    assertThat(config.responseMimeType()).isEmpty();
    assertThat(config.responseJsonSchema()).isEmpty();
  }

  @Test
  void nullResponseConfigurationHasNoRequestSideEffect() {
    final var snapshot = new ConversationSnapshot(List.of(), List.of());

    final var config = converter.toGenerateContentConfig(model(null), null, snapshot);

    assertThat(config.responseMimeType()).isEmpty();
    assertThat(config.responseJsonSchema()).isEmpty();
  }
}
