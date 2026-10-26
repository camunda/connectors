/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.agent;

import static io.camunda.connector.agenticai.aiagent.TestMessagesFixture.TEST_CHAT_MODEL;
import static io.camunda.connector.agenticai.aiagent.TestMessagesFixture.TEST_SYSTEM_PROMPT;
import static io.camunda.connector.agenticai.aiagent.TestMessagesFixture.assistantMessage;
import static io.camunda.connector.agenticai.aiagent.TestMessagesFixture.systemMessage;
import static io.camunda.connector.agenticai.aiagent.agent.AgentErrorCodes.ERROR_CODE_FAILED_TO_PARSE_RESPONSE_CONTENT;
import static io.camunda.connector.agenticai.testutil.TestDocuments.createDocument;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.agenticai.aiagent.conversation.AgentConversation;
import io.camunda.connector.agenticai.aiagent.conversation.TurnReconstructor;
import io.camunda.connector.agenticai.aiagent.document.DocumentHandle;
import io.camunda.connector.agenticai.aiagent.document.DocumentRegistries;
import io.camunda.connector.agenticai.aiagent.document.IngestedDocument;
import io.camunda.connector.agenticai.aiagent.model.AgentConfiguration;
import io.camunda.connector.agenticai.aiagent.model.AgentContext;
import io.camunda.connector.agenticai.aiagent.model.AgentFeatures;
import io.camunda.connector.agenticai.aiagent.model.AgentMetrics;
import io.camunda.connector.agenticai.aiagent.model.AgentState;
import io.camunda.connector.agenticai.aiagent.model.document.DocumentId;
import io.camunda.connector.agenticai.aiagent.model.document.DocumentRegistry;
import io.camunda.connector.agenticai.aiagent.model.document.DocumentSource;
import io.camunda.connector.agenticai.aiagent.model.message.AssistantMessage;
import io.camunda.connector.agenticai.aiagent.model.message.content.DocumentContent;
import io.camunda.connector.agenticai.aiagent.model.message.content.TextContent;
import io.camunda.connector.agenticai.aiagent.model.request.ResponseConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.ResponseFormatConfiguration.JsonResponseFormatConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.ResponseFormatConfiguration.TextResponseFormatConfiguration;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCall;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolDefinition;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolKind;
import io.camunda.connector.agenticai.aiagent.request.AgentTaskResponseConfiguration;
import io.camunda.connector.agenticai.aiagent.tool.GatewayToolHandlerRegistry;
import io.camunda.connector.agenticai.aiagent.tool.ToolCallProcessVariable;
import io.camunda.connector.api.document.Document;
import io.camunda.connector.api.document.DocumentFactory;
import io.camunda.connector.api.error.ConnectorException;
import io.camunda.connector.document.jackson.JacksonModuleDocumentDeserializer;
import io.camunda.connector.runtime.core.intrinsic.DisabledIntrinsicFunctionExecutor;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.assertj.core.api.ThrowingConsumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AgentResponseHandlerTest {

  // Base context with modelCalls=5; toAgentContext() adds 1 from ingest → expected context has 6
  private static final AgentContext BASE_AGENT_CONTEXT =
      AgentContext.empty()
          .withState(AgentState.READY)
          .withMetrics(AgentMetrics.empty().withModelCalls(5));

  private static final AgentContext EXPECTED_AGENT_CONTEXT =
      BASE_AGENT_CONTEXT.withMetrics(AgentMetrics.empty().withModelCalls(6));

  // Assistant messages in these tests carry no tool calls, so the registry returns empty
  private static final List<ToolCallProcessVariable> EXPECTED_TOOL_CALLS = List.of();

  private static final String HAIKU_TEXT =
      "Endless waves whisper | moonlight dances on the tide | secrets drift below.";
  private static final String HAIKU_JSON =
      "{\"text\":\"%s\", \"length\": %d}".formatted(HAIKU_TEXT, HAIKU_TEXT.length());
  private static final ThrowingConsumer<Object> HAIKU_JSON_ASSERTIONS =
      json ->
          assertThat(json)
              .asInstanceOf(InstanceOfAssertFactories.map(String.class, Object.class))
              .containsExactly(entry("text", HAIKU_TEXT), entry("length", HAIKU_TEXT.length()));

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Mock private GatewayToolHandlerRegistry gatewayToolHandlers;

  private AgentResponseHandler responseHandler;

  @BeforeEach
  void setUp() {
    responseHandler = new AgentResponseHandlerImpl(objectMapper, gatewayToolHandlers);
    // by default, registry passes tool calls through unchanged
    lenient()
        .when(gatewayToolHandlers.transformToolCalls(any(), any()))
        .thenAnswer(inv -> inv.getArgument(1));
  }

  /**
   * Builds a conversation that has completed one turn with the given assistant message and response
   * configuration. The conversation has RAW_TOOL_CALLS on the assistant message by default when the
   * provided assistantMessage has no tool calls; the tool calls are taken from the message itself.
   */
  private AgentConversation conversationWith(
      ResponseConfiguration responseConfig, AssistantMessage assistantMessage) {
    var config =
        new AgentConfiguration(
            TEST_CHAT_MODEL, TEST_SYSTEM_PROMPT, null, null, null, null, responseConfig);
    var history = TurnReconstructor.reconstruct(List.of());
    return AgentConversation.rehydrate(
            config, BASE_AGENT_CONTEXT, history, systemMessage("system"), List.of())
        .ingest(assistantMessage, new AgentMetrics(1, AgentMetrics.TokenUsage.empty(), 0));
  }

  @Nested
  class Text {

    @ParameterizedTest
    @MethodSource("emptyAssistantMessages")
    void returnsEmptyResponseWhenAssistantMessageDoesNotContainText(
        AssistantMessage assistantMessage) {
      final var response =
          createResponse(
              new AgentTaskResponseConfiguration(new TextResponseFormatConfiguration(false), false),
              assistantMessage);

      assertThat(response.responseMessage()).isNull();
      assertThat(response.responseText()).isNull();
      assertThat(response.responseJson()).isNull();
    }

    @Test
    void returnsTextResponseIfConfigured() {
      final var response =
          createResponse(
              new AgentTaskResponseConfiguration(new TextResponseFormatConfiguration(false), false),
              assistantMessage(HAIKU_TEXT));

      assertThat(response.responseMessage()).isNull();
      assertThat(response.responseText()).isEqualTo(HAIKU_TEXT);
      assertThat(response.responseJson()).isNull();
    }

    @Test
    void returnsTextResponseIfResponseConfigurationIsMissing() {
      final var response = createResponse(null, assistantMessage(HAIKU_TEXT));

      assertThat(response.responseMessage()).isNull();
      assertThat(response.responseText()).isEqualTo(HAIKU_TEXT);
      assertThat(response.responseJson()).isNull();
    }

    @Test
    void returnsTextResponseIfResponseFormatIsMissing() {
      final var response =
          createResponse(
              new AgentTaskResponseConfiguration(null, false), assistantMessage(HAIKU_TEXT));

      assertThat(response.responseMessage()).isNull();
      assertThat(response.responseText()).isEqualTo(HAIKU_TEXT);
      assertThat(response.responseJson()).isNull();
    }

    @Test
    void triesToParseResponseTextAsJsonIfConfigured() {
      final var response =
          createResponse(
              new AgentTaskResponseConfiguration(new TextResponseFormatConfiguration(true), false),
              assistantMessage(HAIKU_JSON));

      assertThat(response.responseMessage()).isNull();
      assertThat(response.responseText()).isEqualTo(HAIKU_JSON);
      assertThat(response.responseJson()).satisfies(HAIKU_JSON_ASSERTIONS);
    }

    @Test
    void returnsNullAsJsonObjectWhenParsingJsonFails() {
      final var response =
          createResponse(
              new AgentTaskResponseConfiguration(new TextResponseFormatConfiguration(true), false),
              assistantMessage(HAIKU_TEXT));

      assertThat(response.responseMessage()).isNull();
      assertThat(response.responseText()).isEqualTo(HAIKU_TEXT);
      assertThat(response.responseJson()).isNull();
    }

    @Test
    void parsesJsonWrappedInMarkdownCodeBlocks() {
      // given - JSON wrapped in markdown code blocks (common with Anthropic Claude)
      String markdownWrappedJson = "```json\n" + HAIKU_JSON + "\n```";

      // when
      final var response =
          createResponse(
              new AgentTaskResponseConfiguration(new TextResponseFormatConfiguration(true), false),
              assistantMessage(markdownWrappedJson));

      // then
      assertThat(response.responseMessage()).isNull();
      assertThat(response.responseText()).isEqualTo(markdownWrappedJson);
      assertThat(response.responseJson()).satisfies(HAIKU_JSON_ASSERTIONS);
    }

    @Test
    void parsesJsonWrappedInMarkdownCodeBlocksWithoutLanguage() {
      // given - JSON wrapped in markdown code blocks without language specifier
      String markdownWrappedJson = "```\n" + HAIKU_JSON + "\n```";

      // when
      final var response =
          createResponse(
              new AgentTaskResponseConfiguration(new TextResponseFormatConfiguration(true), false),
              assistantMessage(markdownWrappedJson));

      // then
      assertThat(response.responseMessage()).isNull();
      assertThat(response.responseText()).isEqualTo(markdownWrappedJson);
      assertThat(response.responseJson()).satisfies(HAIKU_JSON_ASSERTIONS);
    }

    @Test
    void returnsAssistantMessageIfConfigured() {
      AssistantMessage assistantMessage = assistantMessage(HAIKU_TEXT);
      final var response =
          createResponse(
              new AgentTaskResponseConfiguration(new TextResponseFormatConfiguration(false), true),
              assistantMessage);

      assertThat(response.responseMessage()).isNotNull().isEqualTo(assistantMessage);
      assertThat(response.responseText()).isEqualTo(HAIKU_TEXT);
      assertThat(response.responseJson()).isNull();
    }

    @Test
    void joinsAllTextContentBlocksWhenAssistantMessageHasMultipleTextContentBlocks() {
      // Gemini 2.5 models can return several TextContent blocks in one assistant message (e.g.
      // split at a thoughtSignature boundary); the response text must not silently drop anything
      // after the first block.
      final var assistantMessage =
          assistantMessage(
              List.of(
                  TextContent.textContent("First half. "),
                  TextContent.textContent("Second half.")));

      final var response =
          createResponse(
              new AgentTaskResponseConfiguration(new TextResponseFormatConfiguration(false), false),
              assistantMessage);

      assertThat(response.responseText()).isEqualTo("First half. Second half.");
    }

    static Stream<AssistantMessage> emptyAssistantMessages() {
      return Stream.of(
          AssistantMessage.builder().build(),
          assistantMessage(List.of(DocumentContent.documentContent(mock(Document.class)))));
    }
  }

  @Nested
  class Json {

    @Test
    void returnsParsedJsonResponse() {
      final var response =
          createResponse(
              new AgentTaskResponseConfiguration(
                  new JsonResponseFormatConfiguration(null, null), false),
              assistantMessage(HAIKU_JSON));

      assertThat(response.responseMessage()).isNull();
      assertThat(response.responseText()).isNull();
      assertThat(response.responseJson()).satisfies(HAIKU_JSON_ASSERTIONS);
    }

    @Test
    void throwsExceptionWhenJsonParsingFails() {
      assertThatThrownBy(
              () ->
                  createResponse(
                      new AgentTaskResponseConfiguration(
                          new JsonResponseFormatConfiguration(null, null), false),
                      assistantMessage(HAIKU_TEXT)))
          .hasMessageStartingWith("Failed to parse response content as JSON")
          .isInstanceOfSatisfying(
              ConnectorException.class,
              e ->
                  assertThat(e.getErrorCode())
                      .isEqualTo(ERROR_CODE_FAILED_TO_PARSE_RESPONSE_CONTENT));
    }

    @Test
    void parsesJsonWrappedInMarkdownCodeBlocks() {
      // given - JSON wrapped in markdown code blocks (common with Anthropic Claude on Bedrock)
      String markdownWrappedJson = "```json\n" + HAIKU_JSON + "\n```";

      // when
      final var response =
          createResponse(
              new AgentTaskResponseConfiguration(
                  new JsonResponseFormatConfiguration(null, null), false),
              assistantMessage(markdownWrappedJson));

      // then
      assertThat(response.responseMessage()).isNull();
      assertThat(response.responseText()).isNull();
      assertThat(response.responseJson()).satisfies(HAIKU_JSON_ASSERTIONS);
    }

    @Test
    void parsesJsonWrappedInMarkdownCodeBlocksWithoutLanguage() {
      // given - JSON wrapped in markdown code blocks without language specifier
      String markdownWrappedJson = "```\n" + HAIKU_JSON + "\n```";

      // when
      final var response =
          createResponse(
              new AgentTaskResponseConfiguration(
                  new JsonResponseFormatConfiguration(null, null), false),
              assistantMessage(markdownWrappedJson));

      // then
      assertThat(response.responseMessage()).isNull();
      assertThat(response.responseText()).isNull();
      assertThat(response.responseJson()).satisfies(HAIKU_JSON_ASSERTIONS);
    }
  }

  /**
   * security-testing-findings#275, PR review thread PRRT_kwDOIGZYus6kVTwr: {@code
   * ConnectorsAutoConfiguration#connectorObjectMapper} -- the mapper actually injected here in
   * production -- registers {@link JacksonModuleDocumentDeserializer}, unlike the bare {@code new
   * ObjectMapper()} the rest of this test class uses. These tests wire a mapper the same way
   * (document module present, {@link DisabledIntrinsicFunctionExecutor} as its function executor,
   * matching the fixed production wiring) to prove that an LLM-generated {@code responseText}
   * shaped like a {@code camunda.function.type} call cannot reach the executor through {@link
   * AgentResponseHandlerImpl#createResponse}, while an ordinary JSON response still parses
   * correctly through the same, document-module-equipped mapper.
   */
  @Nested
  class ProductionMapperWiring {

    // Built inside each test, not as a field initializer: a @Nested class's field initializers
    // run while constructing the instance, before MockitoExtension injects the outer
    // gatewayToolHandlers mock, so a handler built here would permanently capture a null.
    private AgentResponseHandler handlerWithDocumentModuleMapper() {
      var documentModuleObjectMapper =
          new ObjectMapper()
              .registerModule(
                  new JacksonModuleDocumentDeserializer(
                      mock(DocumentFactory.class), new DisabledIntrinsicFunctionExecutor()));
      return new AgentResponseHandlerImpl(documentModuleObjectMapper, gatewayToolHandlers);
    }

    @Test
    void doesNotDispatchAnIntrinsicFunctionFoundInTheModelResponseText() {
      // given - an LLM response shaped exactly like the discriminator object this cache/executor
      // dispatches for a model-declared FEEL literal; here it arrives as ordinary response text,
      // which no allow-list check ever covers.
      String exploitJson = "{\"camunda.function.type\":\"base64\",\"params\":[\"test\"]}";
      var conversation =
          conversationWith(
              new AgentTaskResponseConfiguration(
                  new JsonResponseFormatConfiguration(null, null), false),
              assistantMessage(exploitJson));

      // then - the executor refuses rather than returning "dGVzdA==" (base64("test")), which
      // would prove the function actually ran.
      assertThatThrownBy(() -> handlerWithDocumentModuleMapper().createResponse(conversation))
          .isInstanceOf(UnsupportedOperationException.class)
          .hasMessageContaining("Intrinsic function dispatch is disabled");
    }

    @Test
    void stillParsesOrdinaryJsonResponseTextThroughTheDocumentModuleEquippedMapper() {
      var conversation =
          conversationWith(
              new AgentTaskResponseConfiguration(
                  new JsonResponseFormatConfiguration(null, null), false),
              assistantMessage(HAIKU_JSON));

      var response = handlerWithDocumentModuleMapper().createResponse(conversation);

      assertThat(response.responseJson()).satisfies(HAIKU_JSON_ASSERTIONS);
    }
  }

  @Test
  void handsOnlyTheProcessToolCallsToTheProcess() {
    final var runtimeCall =
        new ToolCall("call-1", "acme_lookup", Map.of()).withKind(ToolKind.RUNTIME);
    final var processCall = new ToolCall("call-2", "getWeather", Map.of("location", "MUC"));
    final var conversation =
        conversationWith(
            new AgentTaskResponseConfiguration(new TextResponseFormatConfiguration(false), false),
            assistantMessage("both", List.of(runtimeCall, processCall)));

    final var response = responseHandler.createResponse(conversation);

    assertThat(response.toolCalls())
        .extracting(call -> call.metadata().id())
        .containsExactly("call-2");
  }

  @Nested
  class DocumentParameters {

    private static final ToolDefinition UPLOAD =
        ToolDefinition.builder()
            .name("upload")
            .inputSchema(
                Map.of(
                    "type",
                    "object",
                    "properties",
                    Map.of("doc", Map.of("type", "string", "format", "camunda-document-id"))))
            .build();
    private static final ToolDefinition WEATHER =
        ToolDefinition.builder().name("getWeather").inputSchema(Map.of("type", "object")).build();

    private final Document report = createDocument("report", "report.txt");
    private final DocumentId reportId = DocumentHandle.idFor(report);
    private final DocumentRegistry registry =
        DocumentRegistries.withDocuments(
            DocumentRegistry.empty(),
            List.of(new IngestedDocument(report, DocumentSource.userMessage())));

    private AgentConversation conversation(List<String> features, ToolCall... calls) {
      final var context =
          AgentContext.empty()
              .withState(AgentState.READY)
              .withFeatures(features)
              .withDocumentRegistry(registry)
              .withToolDefinitions(List.of(UPLOAD, WEATHER));
      return AgentConversation.rehydrate(
              new AgentConfiguration(
                  TEST_CHAT_MODEL, TEST_SYSTEM_PROMPT, null, null, null, null, null),
              context,
              TurnReconstructor.reconstruct(List.of()),
              systemMessage("system"),
              List.of())
          .ingest(
              assistantMessage("calling", List.of(calls)),
              new AgentMetrics(1, AgentMetrics.TokenUsage.empty(), calls.length));
    }

    private AgentConversation conversation(ToolCall... calls) {
      return conversation(List.of(AgentFeatures.RUNTIME_TOOLS, AgentFeatures.DOCUMENTS), calls);
    }

    @Test
    void handsTheCamundaDocumentReferenceOfAKnownTagToTheProcess() {
      final var call =
          new ToolCall("u1", "upload", Map.of("doc", "<doc id=\"" + reportId + "\" />"));

      final var response = responseHandler.createResponse(conversation(call));

      assertThat(response.toolCalls())
          .singleElement()
          .satisfies(
              toolCall -> {
                assertThat(toolCall.metadata().id()).isEqualTo("u1");
                assertThat(toolCall.arguments())
                    .containsEntry("doc", registry.findById(reportId).orElseThrow().reference());
              });
    }

    @Test
    void keepsTheToolCallsOfTheStoredAssistantMessageAsTheModelSentThem() {
      final var call =
          new ToolCall("u1", "upload", Map.of("doc", "<doc id=\"" + reportId + "\" />"));
      final var conversation = conversation(call);

      responseHandler.createResponse(conversation);

      assertThat(conversation.lastTurn().orElseThrow().assistantMessage().toolCalls())
          .containsExactly(call);
    }

    @Test
    void resolvesTheDocumentsOfEveryCallOfTheProcessAndLeavesCallsWithoutDocumentParameters() {
      final var upload =
          new ToolCall("u1", "upload", Map.of("doc", "<doc id=\"" + reportId + "\" />"));
      final var weather = new ToolCall("w1", "getWeather", Map.of("doc", "nope"));

      final var response = responseHandler.createResponse(conversation(upload, weather));

      assertThat(response.toolCalls())
          .extracting(call -> call.metadata().id(), call -> call.arguments().get("doc"))
          .containsExactly(
              tuple("u1", registry.findById(reportId).orElseThrow().reference()),
              tuple("w1", "nope"));
    }

    @Test
    void passesTheCallsWithResolvedArgumentsToTheGatewayToolHandlers() {
      final var call =
          new ToolCall("u1", "upload", Map.of("doc", "<doc id=\"" + reportId + "\" />"));

      responseHandler.createResponse(conversation(call));

      @SuppressWarnings("unchecked")
      final ArgumentCaptor<List<ToolCall>> transformed = ArgumentCaptor.forClass(List.class);
      verify(gatewayToolHandlers).transformToolCalls(any(), transformed.capture());
      assertThat(transformed.getValue())
          .singleElement()
          .satisfies(
              toolCall ->
                  assertThat(toolCall.arguments())
                      .containsEntry("doc", registry.findById(reportId).orElseThrow().reference()));
    }

    @Test
    void anAgentInstanceWithoutTheDocumentsFeatureHandsTheArgumentsAsTheModelSentThem() {
      final var call = new ToolCall("u1", "upload", Map.of("doc", "<doc id=\"x\" />"));

      final var response =
          responseHandler.createResponse(conversation(List.of(AgentFeatures.RUNTIME_TOOLS), call));

      assertThat(response.toolCalls())
          .singleElement()
          .satisfies(
              toolCall ->
                  assertThat(toolCall.arguments()).containsEntry("doc", "<doc id=\"x\" />"));
    }

    @Test
    void failsForACallWithInvalidDocumentParametersThatIsHandedToTheProcess() {
      final var call = new ToolCall("u1", "upload", Map.of("doc", "bogus"));

      assertThatThrownBy(() -> responseHandler.createResponse(conversation(call)))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("'u1'")
          .hasMessageContaining("'upload'");
    }

    @Test
    void doesNotHandOutTheCallOfAToolOfTheProcessThatWasRejectedForItsDocumentParameters() {
      final var rejected =
          new ToolCall("u1", "upload", Map.of("doc", "bogus")).withKind(ToolKind.RUNTIME);
      final var weather = new ToolCall("w1", "getWeather", Map.of());

      final var response = responseHandler.createResponse(conversation(rejected, weather));

      assertThat(response.toolCalls())
          .extracting(call -> call.metadata().id())
          .containsExactly("w1");
    }
  }

  private AgentResponse createResponse(
      ResponseConfiguration responseConfiguration, AssistantMessage assistantMessage) {
    final var conversation = conversationWith(responseConfiguration, assistantMessage);
    final var response = responseHandler.createResponse(conversation);

    assertThat(response).isNotNull();
    assertThat(response.context()).isEqualTo(EXPECTED_AGENT_CONTEXT);
    assertThat(response.toolCalls()).containsExactlyElementsOf(EXPECTED_TOOL_CALLS);

    return response;
  }
}
