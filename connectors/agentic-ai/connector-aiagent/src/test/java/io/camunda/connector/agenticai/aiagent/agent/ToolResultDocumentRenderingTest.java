/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.agent;

import static io.camunda.connector.agenticai.aiagent.TestMessagesFixture.TOOL_CALLS;
import static io.camunda.connector.agenticai.aiagent.TestMessagesFixture.assistantMessage;
import static io.camunda.connector.agenticai.aiagent.TestMessagesFixture.userMessage;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.connector.agenticai.aiagent.TestMessagesFixture;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.anthropic.AnthropicContentConverter;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.bedrock.BedrockConverseContentConverter;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.gemini.GeminiContentConverter;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.openai.OpenAiContentConverter;
import io.camunda.connector.agenticai.aiagent.conversation.AgentConversation;
import io.camunda.connector.agenticai.aiagent.conversation.AgentInput;
import io.camunda.connector.agenticai.aiagent.conversation.TurnReconstructor;
import io.camunda.connector.agenticai.aiagent.document.DocumentReferences;
import io.camunda.connector.agenticai.aiagent.memory.conversation.inprocess.InProcessConversationContext;
import io.camunda.connector.agenticai.aiagent.model.AgentConfiguration;
import io.camunda.connector.agenticai.aiagent.model.AgentContext;
import io.camunda.connector.agenticai.aiagent.model.AgentFeatures;
import io.camunda.connector.agenticai.aiagent.model.AgentState;
import io.camunda.connector.agenticai.aiagent.model.message.ToolCallResultMessage;
import io.camunda.connector.agenticai.aiagent.model.message.content.ObjectContent;
import io.camunda.connector.agenticai.aiagent.model.message.content.TextContent;
import io.camunda.connector.agenticai.aiagent.model.request.PromptConfiguration.UserPromptConfiguration;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCallResult;
import io.camunda.connector.agenticai.aiagent.tool.GatewayToolHandlerRegistry;
import io.camunda.connector.agenticai.testutil.TestObjectMapperSupplier;
import io.camunda.connector.api.document.Document;
import io.camunda.connector.api.document.DocumentCreationRequest;
import io.camunda.connector.runtime.core.document.DocumentFactoryImpl;
import io.camunda.connector.runtime.core.document.store.InMemoryDocumentStore;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The model gets the tag of a document in a tool result: the composed messages are windowed with
 * the document expander and every provider converter renders the result of the window with the
 * tags.
 */
class ToolResultDocumentRenderingTest {

  private static final AgentContext CONTEXT =
      AgentContext.builder()
          .state(AgentState.READY)
          .features(List.of(AgentFeatures.RUNTIME_TOOLS, AgentFeatures.DOCUMENTS))
          .conversation(InProcessConversationContext.builder("conv").build())
          .build();
  private static final AgentConfiguration CONFIGURATION =
      new AgentConfiguration(
          TestMessagesFixture.TEST_CHAT_MODEL,
          TestMessagesFixture.TEST_SYSTEM_PROMPT,
          null,
          null,
          null,
          null,
          null);

  private final DocumentFactoryImpl documentFactory =
      new DocumentFactoryImpl(InMemoryDocumentStore.INSTANCE);
  private AgentConversationTurnInputComposerImpl composer;

  @BeforeEach
  void setUp() {
    final var gatewayToolHandlers = mock(GatewayToolHandlerRegistry.class);
    when(gatewayToolHandlers.transformToolCallResults(any(), any()))
        .thenAnswer(invocation -> invocation.getArgument(1));
    when(gatewayToolHandlers.handlerForToolDefinition(any())).thenReturn(Optional.empty());
    composer = new AgentConversationTurnInputComposerImpl(gatewayToolHandlers);
  }

  private Document document(String content, String fileName, String contentType) {
    return documentFactory.create(
        DocumentCreationRequest.from(content.getBytes(StandardCharsets.UTF_8))
            .contentType(contentType)
            .fileName(fileName)
            .build());
  }

  @Test
  void everyProviderRendersTheDocumentsOfTheWindowedToolResultsAsTags() {
    final var bare = document("bare", "bare.pdf", "application/pdf");
    final var nested = document("nested", "nested.txt", "text/plain");
    final var results =
        List.of(
            ToolCallResult.builder().id("abcdef").name("getWeather").content(bare).build(),
            ToolCallResult.builder()
                .id("fedcba")
                .name("getDateTime")
                .content(Map.of("status", 200, "file", nested))
                .build());
    final var previous =
        TurnReconstructor.reconstruct(
            List.of(userMessage("hi"), assistantMessage("thinking", TOOL_CALLS)));

    final var nextTurn =
        (CompositionResult.NextTurn)
            composer.compose(
                CONFIGURATION,
                CONTEXT,
                previous,
                AgentInput.from(new UserPromptConfiguration("user input", List.of()), results));

    // the stored message holds the bare document as a reference and the nested one as it is
    final var stored = (ToolCallResultMessage) nextTurn.messages().getFirst();
    assertThat(stored.results().get(0).content()).containsExactly(DocumentReferences.from(bare));
    assertThat(stored.results().get(1).content()).singleElement().isInstanceOf(ObjectContent.class);

    final var conversation =
        AgentConversation.rehydrate(CONFIGURATION, CONTEXT, previous, null, nextTurn.messages());
    final var windowed =
        conversation.window(100).messages().stream()
            .filter(ToolCallResultMessage.class::isInstance)
            .map(ToolCallResultMessage.class::cast)
            .findFirst()
            .orElseThrow();

    // the providers never see a reference: the windowed result holds the tag of the document
    assertThat(windowed.results().get(0).content())
        .containsExactly(TextContent.textContent(DocumentReferences.from(bare).toTag()));
    assertThat(windowed.results().get(1).content())
        .containsExactly(
            ObjectContent.objectContent(
                Map.of("status", 200, "file", DocumentReferences.from(nested).toTag())));

    for (int i = 0; i < results.size(); i++) {
      final var fileName = i == 0 ? "bare.pdf" : "nested.txt";
      final var rendered = renderedByEveryProvider(windowed.results().get(i).content());
      // the document is in the request as a tag, not as its reference
      assertThat(rendered)
          .allSatisfy(text -> assertThat(text).contains(fileName).doesNotContain("storeId"));
    }
  }

  private static List<String> renderedByEveryProvider(
      List<io.camunda.connector.agenticai.aiagent.model.message.content.Content> content) {
    final var mapper = TestObjectMapperSupplier.INSTANCE;
    return List.of(
        new AnthropicContentConverter(mapper).toToolResultBlocks(content).toString(),
        new BedrockConverseContentConverter(mapper).toToolResultBlocks(content).toString(),
        new GeminiContentConverter(mapper).toFunctionResponseParts(content).toString(),
        new OpenAiContentConverter(mapper).toResponsesToolResultOutputItems(content).toString());
  }
}
