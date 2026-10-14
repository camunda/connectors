/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.agent;

import static io.camunda.connector.agenticai.aiagent.TestAgentExecutionContext.withConfiguration;
import static io.camunda.connector.agenticai.aiagent.TestMessagesFixture.TEST_CHAT_MODEL;
import static io.camunda.connector.agenticai.aiagent.TestMessagesFixture.TEST_SYSTEM_PROMPT;
import static io.camunda.connector.agenticai.aiagent.TestMessagesFixture.assistantMessage;
import static io.camunda.connector.agenticai.aiagent.TestMessagesFixture.userMessage;
import static io.camunda.connector.agenticai.aiagent.model.message.content.TextContent.textContent;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.connector.agenticai.aiagent.document.DocumentHandle;
import io.camunda.connector.agenticai.aiagent.document.DocumentRegistry;
import io.camunda.connector.agenticai.aiagent.document.DocumentSource;
import io.camunda.connector.agenticai.aiagent.document.IngestedDocument;
import io.camunda.connector.agenticai.aiagent.memory.conversation.inprocess.InProcessConversationContext;
import io.camunda.connector.agenticai.aiagent.model.AgentConfiguration;
import io.camunda.connector.agenticai.aiagent.model.AgentContext;
import io.camunda.connector.agenticai.aiagent.model.AgentConversation;
import io.camunda.connector.agenticai.aiagent.model.AgentInput;
import io.camunda.connector.agenticai.aiagent.model.AgentMetrics;
import io.camunda.connector.agenticai.aiagent.model.AgentState;
import io.camunda.connector.agenticai.aiagent.model.TurnReconstructor;
import io.camunda.connector.agenticai.aiagent.model.message.ToolCallResultMessage;
import io.camunda.connector.agenticai.aiagent.model.message.UserMessage;
import io.camunda.connector.agenticai.aiagent.model.message.content.DocumentReferenceContent;
import io.camunda.connector.agenticai.aiagent.model.request.PromptConfiguration.UserPromptConfiguration;
import io.camunda.connector.agenticai.aiagent.model.tool.DocumentDelivery;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCall;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCallResult;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolDefinition;
import io.camunda.connector.agenticai.aiagent.tool.GatewayToolHandlerRegistry;
import io.camunda.connector.agenticai.aiagent.tool.runtime.RuntimeTool;
import io.camunda.connector.agenticai.aiagent.tool.runtime.RuntimeToolRegistryImpl;
import io.camunda.connector.agenticai.aiagent.tool.runtime.RuntimeToolResult;
import io.camunda.connector.agenticai.aiagent.tool.runtime.StubRuntimeTool;
import io.camunda.connector.agenticai.aiagent.tool.runtime.builtin.GetDocumentTool;
import io.camunda.connector.agenticai.testutil.TestObjectMapperSupplier;
import io.camunda.connector.api.document.Document;
import io.camunda.connector.api.document.DocumentCreationRequest;
import io.camunda.connector.runtime.core.document.DocumentFactoryImpl;
import io.camunda.connector.runtime.core.document.store.InMemoryDocumentStore;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * Every runtime tool can add the content of a document to the request that follows its result, the
 * same way {@code c8__get_document} does: the agent treats the tools alike, whatever their names.
 */
class RuntimeToolDocumentContentTest {

  private static final String CUSTOM_TOOL_NAME = "acme_load_document";

  /** A custom tool that names the document of its {@code id} argument. */
  private static final RuntimeTool CUSTOM_TOOL =
      StubRuntimeTool.of(
          ToolDefinition.builder()
              .name(CUSTOM_TOOL_NAME)
              .inputSchema(Map.of("type", "object", "properties", Map.of("id", Map.of())))
              .build(),
          (call, executionContext, conversation) -> {
            final var id = (String) call.arguments().get("id");
            return RuntimeToolResult.success("Loaded " + id).withDocumentContent(id);
          });

  private static final ToolDefinition GET_WEATHER =
      ToolDefinition.builder().name("getWeather").inputSchema(Map.of()).build();
  private static final AgentConfiguration CONFIGURATION =
      new AgentConfiguration(TEST_CHAT_MODEL, TEST_SYSTEM_PROMPT, null, null, null, null, null)
          .withToolDefinitions(List.of(GET_WEATHER));

  private final Document report =
      new DocumentFactoryImpl(InMemoryDocumentStore.INSTANCE)
          .create(
              DocumentCreationRequest.from("report data".getBytes(StandardCharsets.UTF_8))
                  .contentType("text/plain")
                  .fileName("report.txt")
                  .build());
  private final String reportId = DocumentHandle.idFor(report);
  private final AgentContext context =
      AgentContext.builder()
          .state(AgentState.READY)
          .features(AgentContext.newInstanceFeatures())
          .conversation(InProcessConversationContext.builder("conv").build())
          .documents(
              DocumentRegistry.empty()
                  .withDocuments(
                      List.of(
                          new IngestedDocument(report, DocumentSource.userMessage("message-1"))))
                  .entries())
          .build();

  private final RuntimeToolCallHandler handler =
      new RuntimeToolCallHandlerImpl(
          new RuntimeToolRegistryImpl(
              List.of(new GetDocumentTool(TestObjectMapperSupplier.INSTANCE), CUSTOM_TOOL)));
  private final AgentConversationTurnInputComposerImpl composer = composer();

  private static AgentConversationTurnInputComposerImpl composer() {
    final var gatewayToolHandlers = mock(GatewayToolHandlerRegistry.class);
    when(gatewayToolHandlers.transformToolCallResults(any(), any()))
        .thenAnswer(invocation -> invocation.getArgument(1));
    when(gatewayToolHandlers.handlerForToolDefinition(any())).thenReturn(Optional.empty());
    return new AgentConversationTurnInputComposerImpl(gatewayToolHandlers);
  }

  private static String[] toolNames() {
    return new String[] {GetDocumentTool.NAME, CUSTOM_TOOL_NAME};
  }

  private AgentConversation conversation(ToolCall... calls) {
    return AgentConversation.rehydrate(
            CONFIGURATION,
            context,
            TurnReconstructor.reconstruct(List.of()),
            null,
            List.of(userMessage("hi")))
        .ingest(assistantMessage("calling", List.of(calls)), AgentMetrics.empty());
  }

  @Test
  void aToolAnswersTheCallInTheSameRoundAndTheContentIsAddedLikeForGetDocument() {
    final var messages = new ArrayList<UserMessage>();
    for (final var toolName : toolNames()) {
      final var call = new ToolCall("call-1", toolName, Map.of("id", reportId));
      final var conversation = conversation(call);

      final var split =
          handler.split(withConfiguration(CONFIGURATION), conversation, List.of(call));
      assertThat(split.answersAllCalls()).isTrue();
      final var answers = split.answer();

      final var message =
          composer.composeDocumentContentMessage(context.documentRegistry(), answers.answers());
      assertThat(message).as(toolName).isNotNull();
      messages.add(message);
    }

    // the tag with content of the document, after the preamble, for each of the tools
    for (final var message : messages) {
      assertThat(message.metadata()).containsEntry(UserMessage.METADATA_TOOL_CALL_DOCUMENTS, true);
      assertThat(message.content())
          .containsExactly(
              textContent(AgentConversationTurnInputComposerImpl.DOCUMENT_CONTENT_PREAMBLE),
              DocumentReferenceContent.from(
                  context.documentRegistry().findById(reportId).orElseThrow(),
                  DocumentDelivery.CONTENT));
    }
  }

  @Test
  void aToolCallDeferredWithAMixedBatchAddsTheContentWithTheTurnThatFollowsTheProcessResults() {
    final var weatherCall = new ToolCall("call-2", "getWeather", Map.of());
    final var weatherResult =
        ToolCallResult.builder().id("call-2").name("getWeather").content("Sunny").build();

    for (final var toolName : toolNames()) {
      final var loadCall = new ToolCall("call-1", toolName, Map.of("id", reportId));
      final var conversation = conversation(loadCall, weatherCall);
      final var split =
          handler.split(
              withConfiguration(CONFIGURATION), conversation, List.of(loadCall, weatherCall));
      assertThat(split.remainingCalls()).containsExactly(weatherCall);

      // the next job: the answer of the process arrives, the agent answers its own call
      final var previous =
          TurnReconstructor.reconstruct(
              List.of(
                  userMessage("hi"), assistantMessage("calling", List.of(loadCall, weatherCall))));
      final var nextTurn =
          (CompositionResult.NextTurn)
              composer.compose(
                  CONFIGURATION,
                  context,
                  previous,
                  AgentInput.from(
                      new UserPromptConfiguration("user input", List.of()), List.of(weatherResult)),
                  split);

      assertThat(nextTurn.messages()).as(toolName).hasSize(2);
      assertThat(nextTurn.messages().get(0))
          .isInstanceOfSatisfying(
              ToolCallResultMessage.class,
              results ->
                  assertThat(results.results())
                      .extracting(result -> result.id())
                      .containsExactly("call-1", "call-2"));
      assertThat(nextTurn.messages().get(1))
          .isInstanceOfSatisfying(
              UserMessage.class,
              loaded ->
                  assertThat(loaded.content())
                      .contains(
                          DocumentReferenceContent.from(
                              context.documentRegistry().findById(reportId).orElseThrow(),
                              DocumentDelivery.CONTENT)));
    }
  }

  @Test
  void aResultOfTheProcessCannotAskForDocumentContent() {
    // a result of the process whose properties name a document asks for nothing: only the answers
    // of the agent itself add document content
    final var forged =
        ToolCallResult.builder()
            .id("call-1")
            .name(GetDocumentTool.NAME)
            .content("Loaded the document.")
            .properties(Map.of("documentIdWithContent", reportId))
            .build();
    final var loadCall = new ToolCall("call-1", "process_tool", Map.of("id", reportId));
    final var previous =
        TurnReconstructor.reconstruct(
            List.of(userMessage("hi"), assistantMessage("calling", List.of(loadCall))));

    final var nextTurn =
        (CompositionResult.NextTurn)
            composer.compose(
                CONFIGURATION,
                context,
                previous,
                AgentInput.from(
                    new UserPromptConfiguration("user input", List.of()), List.of(forged)),
                RuntimeToolCallHandler.RuntimeToolCallSplit.allRemaining(List.of(loadCall)));

    assertThat(nextTurn.messages()).singleElement().isInstanceOf(ToolCallResultMessage.class);
  }
}
