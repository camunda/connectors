/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.agent;

import static io.camunda.connector.agenticai.aiagent.TestMessagesFixture.TOOL_CALL_RESULTS;
import static io.camunda.connector.agenticai.model.message.content.TextContent.textContent;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.camunda.connector.agenticai.aiagent.memory.conversation.TestConversationContext;
import io.camunda.connector.agenticai.aiagent.memory.runtime.DefaultRuntimeMemory;
import io.camunda.connector.agenticai.aiagent.memory.runtime.RuntimeMemory;
import io.camunda.connector.agenticai.aiagent.model.AgentContext;
import io.camunda.connector.agenticai.aiagent.model.AgentState;
import io.camunda.connector.agenticai.aiagent.model.request.AgentRequest.AgentRequestData.SystemPromptConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.AgentRequest.AgentRequestData.UserPromptConfiguration;
import io.camunda.connector.agenticai.aiagent.tool.GatewayToolHandlerRegistry;
import io.camunda.connector.agenticai.model.message.SystemMessage;
import io.camunda.connector.agenticai.model.message.ToolCallResultMessage;
import io.camunda.connector.agenticai.model.message.UserMessage;
import io.camunda.connector.agenticai.model.message.content.DocumentContent;
import io.camunda.connector.api.error.ConnectorException;
import io.camunda.document.Document;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AgentMessagesHandlerTest {

  @Mock private GatewayToolHandlerRegistry gatewayToolHandlers;

  private AgentMessagesHandler messagesHandler;
  private RuntimeMemory runtimeMemory;

  @BeforeEach
  void setUp() {
    messagesHandler = new AgentMessagesHandlerImpl(gatewayToolHandlers);
    runtimeMemory = spy(new DefaultRuntimeMemory());
  }

  @Nested
  class SystemMessagesTest {

    @ParameterizedTest
    @NullAndEmptySource
    void addsSystemMessageWithEmptyParameters(Map<String, Object> parameters) {
      final var systemPrompt =
          new SystemPromptConfiguration("You are a helpful assistant.", parameters);
      messagesHandler.addSystemMessage(AgentContext.empty(), runtimeMemory, systemPrompt);

      assertThat(runtimeMemory.allMessages())
          .hasSize(1)
          .containsExactly(SystemMessage.systemMessage("You are a helpful assistant."));
    }

    @Test
    void addsSystemMessageWithParameters() {
      final var systemPrompt =
          new SystemPromptConfiguration(
              "You are a helpful assistant named {{name}}.", Map.of("name", "Johnny"));
      messagesHandler.addSystemMessage(AgentContext.empty(), runtimeMemory, systemPrompt);

      assertThat(runtimeMemory.allMessages())
          .hasSize(1)
          .containsExactly(
              SystemMessage.systemMessage("You are a helpful assistant named Johnny."));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" "})
    void doesNotAddSystemMessageWhenPromptIsEmpty(String prompt) {
      final var systemPrompt = new SystemPromptConfiguration(prompt, Map.of("name", "Johnny"));
      messagesHandler.addSystemMessage(AgentContext.empty(), runtimeMemory, systemPrompt);

      verifyNoInteractions(runtimeMemory);
      assertThat(runtimeMemory.allMessages()).isEmpty();
    }

    @Test
    void throwsExceptionWhenReferencingAMissingVariableName() {
      final var systemPrompt =
          new SystemPromptConfiguration(
              "You are a helpful assistant named {{name}}.", Collections.emptyMap());

      assertThatThrownBy(
              () ->
                  messagesHandler.addSystemMessage(
                      AgentContext.empty(), runtimeMemory, systemPrompt))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Value for the variable 'name' is missing");
    }
  }

  @Nested
  class MessagesFromRequestTest {

    private List<Document> documents;
    private UserPromptConfiguration userPromptWithDocuments;

    @BeforeEach
    void setUp() {
      documents = List.of(mock(Document.class), mock(Document.class));
      userPromptWithDocuments =
          new UserPromptConfiguration("Tell me a story", Collections.emptyMap(), documents);
    }

    @ParameterizedTest
    @EnumSource(AgentState.class)
    void throwsExceptionWhenReceivingToolCallResultsOnEmptyConversation(AgentState agentState) {
      assertThatThrownBy(
              () ->
                  messagesHandler.addMessagesFromRequest(
                      AgentContext.empty().withState(agentState),
                      runtimeMemory,
                      userPromptWithDocuments,
                      TOOL_CALL_RESULTS))
          .isInstanceOfSatisfying(
              ConnectorException.class,
              e -> assertThat(e.getErrorCode()).isEqualTo("TOOL_CALL_RESULTS_ON_EMPTY_CONTEXT"));
    }

    @ParameterizedTest
    @EnumSource(
        value = AgentState.class,
        names = {"READY", "WAITING_FOR_TOOL_INPUT"},
        mode = EnumSource.Mode.EXCLUDE)
    void throwsExceptionWhenAgentIsInInvalidState(AgentState agentState) {
      assertThatThrownBy(
              () ->
                  messagesHandler.addMessagesFromRequest(
                      AgentContext.empty().withState(agentState),
                      runtimeMemory,
                      userPromptWithDocuments,
                      List.of()))
          .isInstanceOfSatisfying(
              ConnectorException.class,
              e -> {
                assertThat(e.getErrorCode()).isEqualTo("IN_INVALID_STATE");
                assertThat(e.getMessage())
                    .isEqualTo(
                        "Agent is in invalid state '%s', not ready to add user messages"
                            .formatted(agentState.name()));
              });
    }

    @Nested
    class UserMessageTest {

      private final AgentContext AGENT_CONTEXT =
          AgentContext.empty()
              .withState(AgentState.READY)
              .withConversation(new TestConversationContext("dummy"));

      @Test
      void addsUserPromptWhenInReadyState() {
        messagesHandler.addMessagesFromRequest(
            AGENT_CONTEXT,
            runtimeMemory,
            new UserPromptConfiguration("Tell me a story", Map.of(), List.of()),
            TOOL_CALL_RESULTS);

        assertThat(runtimeMemory.allMessages())
            .noneMatch(msg -> msg instanceof ToolCallResultMessage)
            .first(InstanceOfAssertFactories.type(UserMessage.class))
            .isEqualTo(UserMessage.userMessage("Tell me a story"));
      }

      @Test
      void addsUserPromptWithParameters() {
        messagesHandler.addMessagesFromRequest(
            AGENT_CONTEXT,
            runtimeMemory,
            new UserPromptConfiguration(
                "Tell me a story about {{name}}", Map.of("name", "Johnny"), List.of()),
            TOOL_CALL_RESULTS);

        assertThat(runtimeMemory.allMessages())
            .noneMatch(msg -> msg instanceof ToolCallResultMessage)
            .first(InstanceOfAssertFactories.type(UserMessage.class))
            .isEqualTo(UserMessage.userMessage("Tell me a story about Johnny"));
      }

      @Test
      void throwsExceptionWhenReferencingAMissingVariableName() {
        assertThatThrownBy(
                () ->
                    messagesHandler.addMessagesFromRequest(
                        AGENT_CONTEXT,
                        runtimeMemory,
                        new UserPromptConfiguration(
                            "Tell me a story about {{name}}", Map.of(), List.of()),
                        TOOL_CALL_RESULTS))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Value for the variable 'name' is missing");
      }

      @Test
      void addsDocumentsToUserMessage() {
        messagesHandler.addMessagesFromRequest(
            AGENT_CONTEXT,
            runtimeMemory,
            new UserPromptConfiguration(null, Map.of(), documents),
            TOOL_CALL_RESULTS);

        assertThat(runtimeMemory.allMessages())
            .noneMatch(msg -> msg instanceof ToolCallResultMessage)
            .first(InstanceOfAssertFactories.type(UserMessage.class))
            .satisfies(
                userMessage ->
                    assertThat(userMessage.content())
                        .hasSize(2)
                        .satisfiesExactly(
                            c -> assertThat(c).isEqualTo(new DocumentContent(documents.get(0))),
                            c -> assertThat(c).isEqualTo(new DocumentContent(documents.get(1)))));
      }

      @Test
      void addsBothUserPromptAndDocuments() {
        messagesHandler.addMessagesFromRequest(
            AGENT_CONTEXT,
            runtimeMemory,
            new UserPromptConfiguration("Tell me a story", Map.of(), documents),
            TOOL_CALL_RESULTS);

        assertThat(runtimeMemory.allMessages())
            .noneMatch(msg -> msg instanceof ToolCallResultMessage)
            .first(InstanceOfAssertFactories.type(UserMessage.class))
            .satisfies(
                userMessage ->
                    assertThat(userMessage.content())
                        .hasSize(3)
                        .satisfiesExactly(
                            c -> assertThat(c).isEqualTo(textContent("Tell me a story")),
                            c -> assertThat(c).isEqualTo(new DocumentContent(documents.get(0))),
                            c -> assertThat(c).isEqualTo(new DocumentContent(documents.get(1)))));
      }

      @ParameterizedTest
      @NullAndEmptySource
      @ValueSource(strings = {" "})
      void throwsExceptionWhenNoContentToAddToUserMessage(String prompt) {
        final var userPrompt = new UserPromptConfiguration(prompt, Map.of(), List.of());

        assertThatThrownBy(
                () ->
                    messagesHandler.addMessagesFromRequest(
                        AGENT_CONTEXT, runtimeMemory, userPrompt, List.of()))
            .isInstanceOfSatisfying(
                ConnectorException.class,
                e -> {
                  assertThat(e.getErrorCode()).isEqualTo("NO_USER_MESSAGE_CONTENT");
                  assertThat(e.getMessage())
                      .isEqualTo(
                          "Agent is in state READY but no user prompt (no text, no documents) to add.");
                });
      }
    }

    @Nested
    class ToolCallResultsTest {

      @Test
      void addsToolCallResultsWhenInWaitingForToolInputState() {
        final var agentContext =
            AgentContext.empty()
                .withState(AgentState.WAITING_FOR_TOOL_INPUT)
                .withConversation(new TestConversationContext("dummy"));

        when(gatewayToolHandlers.transformToolCallResults(agentContext, TOOL_CALL_RESULTS))
            .thenReturn(TOOL_CALL_RESULTS.stream().toList());

        messagesHandler.addMessagesFromRequest(
            agentContext, runtimeMemory, userPromptWithDocuments, TOOL_CALL_RESULTS);

        assertThat(runtimeMemory.allMessages())
            .hasSize(1)
            .noneMatch(msg -> msg instanceof UserMessage)
            .first(InstanceOfAssertFactories.type(ToolCallResultMessage.class))
            .satisfies(
                message ->
                    assertThat(message.results()).containsExactlyElementsOf(TOOL_CALL_RESULTS));
      }

      @Test
      void transformsToolCallResultsViaGatewayToolHandlerRegistry() {
        final var agentContext =
            AgentContext.empty()
                .withState(AgentState.WAITING_FOR_TOOL_INPUT)
                .withConversation(new TestConversationContext("dummy"));

        final var transformedToolCallResults =
            TOOL_CALL_RESULTS.stream()
                .map(
                    toolCallResult -> {
                      if (toolCallResult.name().equals("getWeather")) {
                        return toolCallResult.withContent(
                            "Transformed Weather Result: " + toolCallResult.content());
                      } else {
                        return toolCallResult;
                      }
                    })
                .toList();

        when(gatewayToolHandlers.transformToolCallResults(agentContext, TOOL_CALL_RESULTS))
            .thenReturn(transformedToolCallResults);

        messagesHandler.addMessagesFromRequest(
            agentContext, runtimeMemory, userPromptWithDocuments, TOOL_CALL_RESULTS);

        assertThat(runtimeMemory.allMessages())
            .hasSize(1)
            .noneMatch(msg -> msg instanceof UserMessage)
            .first(InstanceOfAssertFactories.type(ToolCallResultMessage.class))
            .satisfies(
                message ->
                    assertThat(message.results())
                        .containsExactlyElementsOf(transformedToolCallResults));
      }

      @Test
      void throwsExceptionWhenExpectingToolCallResultsButNoneAreGiven() {
        final var agentContext = AgentContext.empty().withState(AgentState.WAITING_FOR_TOOL_INPUT);
        assertThatThrownBy(
                () ->
                    messagesHandler.addMessagesFromRequest(
                        agentContext, runtimeMemory, userPromptWithDocuments, List.of()))
            .isInstanceOfSatisfying(
                ConnectorException.class,
                e -> {
                  assertThat(e.getErrorCode()).isEqualTo("WAITING_FOR_TOOL_INPUT_EMPTY_RESULTS");
                  assertThat(e.getMessage())
                      .isEqualTo(
                          "Agent is waiting for tool input, but tool call results were empty. Is the tool feedback loop configured correctly?");
                });
      }
    }
  }
}
