/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.agentinstance;

import static io.camunda.connector.agenticai.aiagent.agent.AgentErrorCodes.ERROR_CODE_AGENT_INSTANCE_CREATION_FAILED;
import static io.camunda.connector.agenticai.aiagent.agent.AgentErrorCodes.ERROR_CODE_AGENT_INSTANCE_HISTORY_ITEM_FAILED;
import static io.camunda.connector.agenticai.aiagent.agent.AgentErrorCodes.ERROR_CODE_AGENT_INSTANCE_UPDATE_FAILED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.AgentInstanceUpdateStatus;
import io.camunda.client.api.command.ClientHttpException;
import io.camunda.client.api.command.CreateAgentHistoryItemCommandStep1.AgentHistoryContent;
import io.camunda.client.api.command.CreateAgentHistoryItemCommandStep1.AgentHistoryMetrics;
import io.camunda.client.api.command.CreateAgentHistoryItemCommandStep1.AgentHistoryRole;
import io.camunda.client.api.command.CreateAgentHistoryItemCommandStep1.AgentHistoryToolCall;
import io.camunda.client.api.command.CreateAgentInstanceCommandStep1;
import io.camunda.client.api.command.CreateAgentInstanceCommandStep1.CreateAgentInstanceCommandStep5;
import io.camunda.client.api.command.UpdateAgentInstanceCommandStep1;
import io.camunda.client.api.command.UpdateAgentInstanceCommandStep1.UpdateAgentInstanceCommandStep2;
import io.camunda.client.api.response.CreateAgentInstanceResponse;
import io.camunda.client.impl.command.CreateAgentHistoryItemCommandImpl;
import io.camunda.connector.agenticai.adhoctoolsschema.model.AdHocToolElement;
import io.camunda.connector.agenticai.aiagent.model.AgentConfiguration;
import io.camunda.connector.agenticai.aiagent.model.AgentContext;
import io.camunda.connector.agenticai.aiagent.model.AgentConversationTurn;
import io.camunda.connector.agenticai.aiagent.model.AgentExecutionContext;
import io.camunda.connector.agenticai.aiagent.model.AgentMetrics;
import io.camunda.connector.agenticai.aiagent.model.AgentMetrics.TokenUsage;
import io.camunda.connector.agenticai.aiagent.model.request.LimitsConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.PromptConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.provider.OpenAiProviderConfiguration;
import io.camunda.connector.agenticai.aiagent.tool.GatewayToolHandlerRegistry;
import io.camunda.connector.agenticai.autoconfigure.AgenticAiConnectorsConfigurationProperties;
import io.camunda.connector.agenticai.model.message.AssistantMessage;
import io.camunda.connector.agenticai.model.message.MessageUtil;
import io.camunda.connector.agenticai.model.message.ToolCallResultMessage;
import io.camunda.connector.agenticai.model.message.UserMessage;
import io.camunda.connector.agenticai.model.message.content.DocumentContent;
import io.camunda.connector.agenticai.model.message.content.ObjectContent;
import io.camunda.connector.agenticai.model.tool.ToolCall;
import io.camunda.connector.agenticai.model.tool.ToolCallResult;
import io.camunda.connector.api.document.Document;
import io.camunda.connector.api.document.DocumentReference.CamundaDocumentReference;
import io.camunda.connector.api.document.DocumentReference.ExternalDocumentReference;
import io.camunda.connector.api.document.DocumentReference.InlineDocumentReference;
import io.camunda.connector.api.error.ConnectorException;
import io.camunda.connector.api.outbound.JobContext;
import io.camunda.connector.runtime.test.outbound.TestJobContext;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CamundaAgentInstanceClientTest {

  private static final AgenticAiConnectorsConfigurationProperties.RetriesProperties
      RETRIES_CONFIGURATION =
          new AgenticAiConnectorsConfigurationProperties.RetriesProperties(
              4, Duration.ofSeconds(1));

  private static final long ELEMENT_INSTANCE_KEY = 77L;

  private static final long JOB_KEY = 88L;

  private static final long AGENT_INSTANCE_KEY = 999L;

  @Mock private CamundaClient camundaClient;

  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  private CreateAgentInstanceCommandStep1 commandChain;

  @Mock private CreateAgentInstanceResponse response;

  @Mock private UpdateAgentInstanceCommandStep1 updateCommandStep1;

  @Mock(answer = Answers.RETURNS_SELF)
  private UpdateAgentInstanceCommandStep2 updateCommandStep2;

  @Mock(answer = Answers.RETURNS_SELF)
  private CreateAgentHistoryItemCommandImpl historyCommand;

  private CreateAgentInstanceCommandStep5 step5;

  @Mock private GatewayToolHandlerRegistry gatewayToolHandlers;

  private List<Duration> recordedSleeps;
  private CamundaAgentInstanceClient client;

  @BeforeEach
  void setUp() {
    recordedSleeps = new ArrayList<>();
    var historyMapper = new AgentInstanceHistoryMapper(new ObjectMapper(), gatewayToolHandlers);
    client =
        new CamundaAgentInstanceClient(
            camundaClient, RETRIES_CONFIGURATION, recordedSleeps::add, historyMapper);
  }

  private void givenCreateCommand() {
    when(camundaClient.newCreateAgentInstanceCommand()).thenReturn(commandChain);
    step5 =
        commandChain
            .elementInstanceKey(ELEMENT_INSTANCE_KEY)
            .model("gpt-4o")
            .provider(OpenAiProviderConfiguration.OPENAI_ID)
            .systemPrompt("system prompt");
  }

  private void givenCreateCommandWithMaxModelCalls() {
    givenCreateCommand();
    when(step5.maxModelCalls(10)).thenReturn(step5);
  }

  private void givenUpdateCommand() {
    when(camundaClient.newUpdateAgentInstanceCommand(AGENT_INSTANCE_KEY))
        .thenReturn(updateCommandStep1);
    when(updateCommandStep1.elementInstanceKey(ELEMENT_INSTANCE_KEY))
        .thenReturn(updateCommandStep2);
  }

  private void givenHistoryCommand() {
    when(camundaClient.newCreateAgentHistoryItemCommand(AGENT_INSTANCE_KEY))
        .thenReturn(historyCommand);
  }

  @Nested
  class Create {

    @Test
    void shouldReturnAgentInstanceKeyOnFirstSuccessfulAttempt() {
      givenCreateCommandWithMaxModelCalls();
      when(step5.execute()).thenReturn(response);
      when(response.getAgentInstanceKey()).thenReturn(12345L);

      final AgentInstanceKey key = client.create(TestAgentExecutionContext.withLimits());

      assertThat(key).isEqualTo(AgentInstanceKey.of(12345L));
      assertThat(recordedSleeps).isEmpty();
      verify(camundaClient, times(1)).newCreateAgentInstanceCommand();
    }

    @Test
    void shouldReturnAgentInstanceKeyOnFirstAttemptWhenMaxModelCallsIsNull() {
      givenCreateCommand();
      when(step5.execute()).thenReturn(response);
      when(response.getAgentInstanceKey()).thenReturn(67890L);

      final AgentInstanceKey key = client.create(TestAgentExecutionContext.withoutLimits());

      assertThat(key).isEqualTo(AgentInstanceKey.of(67890L));
      assertThat(recordedSleeps).isEmpty();
      verify(camundaClient, times(1)).newCreateAgentInstanceCommand();
    }

    @Test
    void shouldThrowConnectorExceptionImmediatelyForHttp400PermanentError() {
      givenCreateCommandWithMaxModelCalls();
      when(step5.execute()).thenThrow(new ClientHttpException(400, "Bad Request"));

      assertThatThrownBy(() -> client.create(TestAgentExecutionContext.withLimits()))
          .isInstanceOfSatisfying(
              ConnectorException.class,
              e ->
                  assertThat(e.getErrorCode())
                      .isEqualTo(ERROR_CODE_AGENT_INSTANCE_CREATION_FAILED));

      // Only 1 attempt, no sleeps
      assertThat(recordedSleeps).isEmpty();
      verify(camundaClient, times(1)).newCreateAgentInstanceCommand();
    }

    @Test
    void shouldReturnKeyAndRecordOneSleepWhenRetryableErrorPrecedesSuccess() {
      givenCreateCommandWithMaxModelCalls();
      when(step5.execute())
          .thenThrow(new ClientHttpException(404, "Not Found"))
          .thenReturn(response);
      when(response.getAgentInstanceKey()).thenReturn(999L);

      final AgentInstanceKey key = client.create(TestAgentExecutionContext.withLimits());

      assertThat(key).isEqualTo(AgentInstanceKey.of(999L));
      assertThat(recordedSleeps).hasSize(1);
      assertThat(recordedSleeps).containsExactly(Duration.ofSeconds(1));
      verify(camundaClient, times(2)).newCreateAgentInstanceCommand();
    }

    @Test
    void shouldThrowConnectorExceptionWithAttemptCountWhenAllRetriesAreExhausted() {
      givenCreateCommandWithMaxModelCalls();
      when(step5.execute()).thenThrow(new ClientHttpException(500, "Internal Server Error"));

      assertThatThrownBy(() -> client.create(TestAgentExecutionContext.withLimits()))
          .isInstanceOfSatisfying(
              ConnectorException.class,
              e -> {
                assertThat(e.getErrorCode()).isEqualTo(ERROR_CODE_AGENT_INSTANCE_CREATION_FAILED);
                assertThat(e.getMessage()).contains("after 5 attempt(s)");
              });

      // 5 total attempts → 4 sleeps: before attempts 2, 3, 4, 5
      assertThat(recordedSleeps).hasSize(4);
      assertThat(recordedSleeps)
          .containsExactly(
              Duration.ofSeconds(1),
              Duration.ofSeconds(2),
              Duration.ofSeconds(4),
              Duration.ofSeconds(8));
      verify(camundaClient, times(5)).newCreateAgentInstanceCommand();
    }
  }

  @Nested
  class Update {

    @Test
    void shouldSilentlySkipWhenAgentInstanceKeyIsNull() {
      // when
      client.update(
          TestAgentExecutionContext.withLimits(),
          null,
          AgentInstanceUpdateRequest.statusOnly(AgentInstanceUpdateStatus.THINKING));

      // then
      verifyNoInteractions(camundaClient);
    }

    @Test
    void shouldBuildCommandWithStatusOnly() {
      givenUpdateCommand();

      // when
      client.update(
          TestAgentExecutionContext.withLimits(),
          AgentInstanceKey.of(AGENT_INSTANCE_KEY),
          AgentInstanceUpdateRequest.statusOnly(AgentInstanceUpdateStatus.THINKING));

      // then
      verify(updateCommandStep2).status(AgentInstanceUpdateStatus.THINKING);
      verify(updateCommandStep2, never()).modelCalls(anyInt());
      verify(updateCommandStep2, never()).inputTokens(anyLong());
      verify(updateCommandStep2, never()).outputTokens(anyLong());
      verify(updateCommandStep2, never()).toolCalls(anyInt());
      verify(updateCommandStep2).execute();
    }

    @Test
    void shouldBuildCommandWithStatusAndDeltaSkippingZeroFields() {
      givenUpdateCommand();

      // given
      final var agentInstanceKey = AgentInstanceKey.of(AGENT_INSTANCE_KEY);
      final var delta = new AgentMetrics(1, new TokenUsage(10, 20), 0);
      final var request =
          AgentInstanceUpdateRequest.builder()
              .status(AgentInstanceUpdateStatus.IDLE)
              .delta(delta)
              .build();

      // when
      client.update(TestAgentExecutionContext.withLimits(), agentInstanceKey, request);

      // then: status + non-zero delta fields set; toolCalls skipped (0)
      verify(updateCommandStep2).status(AgentInstanceUpdateStatus.IDLE);
      verify(updateCommandStep2).modelCalls(1);
      verify(updateCommandStep2).inputTokens(10L);
      verify(updateCommandStep2).outputTokens(20L);
      verify(updateCommandStep2, never()).toolCalls(0);
      verify(updateCommandStep2).execute();
    }

    @Test
    void shouldBuildCommandWithAllDeltaFields() {
      givenUpdateCommand();

      // given
      final var agentInstanceKey = AgentInstanceKey.of(AGENT_INSTANCE_KEY);
      final var delta = new AgentMetrics(2, new TokenUsage(50, 100), 3);
      final var request =
          AgentInstanceUpdateRequest.builder()
              .status(AgentInstanceUpdateStatus.TOOL_CALLING)
              .delta(delta)
              .build();

      // when
      client.update(TestAgentExecutionContext.withLimits(), agentInstanceKey, request);

      // then
      verify(updateCommandStep2).status(AgentInstanceUpdateStatus.TOOL_CALLING);
      verify(updateCommandStep2).modelCalls(2);
      verify(updateCommandStep2).inputTokens(50L);
      verify(updateCommandStep2).outputTokens(100L);
      verify(updateCommandStep2).toolCalls(3);
      verify(updateCommandStep2).execute();
      assertThat(recordedSleeps).isEmpty();
    }

    @Test
    void shouldRetry404ForUpdateDueToEventualConsistency() {
      // given: a freshly created agent instance may not yet be visible to the update call
      givenUpdateCommand();
      final var agentInstanceKey = AgentInstanceKey.of(AGENT_INSTANCE_KEY);
      when(updateCommandStep2.execute()).thenThrow(new ClientHttpException(404, "Not Found"));

      // when / then: 404 is retryable for update → retries are exhausted before failing
      assertThatThrownBy(
              () ->
                  client.update(
                      TestAgentExecutionContext.withLimits(),
                      agentInstanceKey,
                      AgentInstanceUpdateRequest.statusOnly(AgentInstanceUpdateStatus.THINKING)))
          .isInstanceOfSatisfying(
              ConnectorException.class,
              e -> assertThat(e.getErrorCode()).isEqualTo(ERROR_CODE_AGENT_INSTANCE_UPDATE_FAILED));

      assertThat(recordedSleeps).hasSize(4);
      verify(camundaClient, times(5)).newUpdateAgentInstanceCommand(AGENT_INSTANCE_KEY);
    }

    @Test
    void shouldThrowConnectorExceptionWithAttemptCountWhenAllRetriesExhausted() {
      givenUpdateCommand();

      // given
      final var agentInstanceKey = AgentInstanceKey.of(AGENT_INSTANCE_KEY);
      when(updateCommandStep2.execute())
          .thenThrow(new ClientHttpException(500, "Internal Server Error"));

      // when / then
      assertThatThrownBy(
              () ->
                  client.update(
                      TestAgentExecutionContext.withLimits(),
                      agentInstanceKey,
                      AgentInstanceUpdateRequest.statusOnly(AgentInstanceUpdateStatus.THINKING)))
          .isInstanceOfSatisfying(
              ConnectorException.class,
              e -> {
                assertThat(e.getErrorCode()).isEqualTo(ERROR_CODE_AGENT_INSTANCE_UPDATE_FAILED);
                assertThat(e.getMessage()).contains("after 5 attempt(s)");
              });

      assertThat(recordedSleeps).hasSize(4);
      assertThat(recordedSleeps)
          .containsExactly(
              Duration.ofSeconds(1),
              Duration.ofSeconds(2),
              Duration.ofSeconds(4),
              Duration.ofSeconds(8));
      verify(camundaClient, times(5)).newUpdateAgentInstanceCommand(AGENT_INSTANCE_KEY);
    }
  }

  @Nested
  class HistoryItems {

    @Captor private ArgumentCaptor<List<AgentHistoryContent>> contentCaptor;

    @Test
    void shouldCreateUserHistoryItemBeforeChat() {
      givenHistoryCommand();

      // given
      final var turn =
          new AgentConversationTurn(
              3,
              List.of(
                  UserMessage.builder()
                      .content(MessageUtil.singleTextContent("Hello there"))
                      .build()),
              null,
              AgentMetrics.empty());

      // when
      client.createHistoryForInputMessages(
          TestAgentExecutionContext.withLimits(), AgentInstanceKey.of(AGENT_INSTANCE_KEY), turn);

      // then
      verify(historyCommand).elementInstanceKey(ELEMENT_INSTANCE_KEY);
      verify(historyCommand).jobKey(JOB_KEY);
      verify(historyCommand).role(AgentHistoryRole.USER);
      verify(historyCommand).iteration(3);
      verify(historyCommand).producedAt(any());
      verify(historyCommand).content(contentCaptor.capture());
      verify(historyCommand, never()).toolCalls(any());
      verify(historyCommand, never()).metrics(any());
      verify(historyCommand).execute();

      assertThat(contentCaptor.getValue())
          .singleElement()
          .isInstanceOfSatisfying(
              AgentHistoryContent.TextContent.class,
              text -> assertThat(text.getText()).isEqualTo("Hello there"));
    }

    @Test
    void shouldCreateOneToolResultHistoryItemPerResultBeforeChat() {
      givenHistoryCommand();

      // given: a single tool-call-result message carrying two results (elementId already resolved
      // on the model upstream, == tool name for these ad-hoc tools)
      final var turn =
          new AgentConversationTurn(
              1,
              List.of(
                  ToolCallResultMessage.builder()
                      .results(
                          List.of(
                              ToolCallResult.builder()
                                  .id("a")
                                  .name("getWeather")
                                  .content("sunny")
                                  .elementId("getWeather")
                                  .build(),
                              ToolCallResult.builder()
                                  .id("b")
                                  .name("getTime")
                                  .elementId("getTime")
                                  .build()))
                      .build()),
              null,
              AgentMetrics.empty());

      // when
      client.createHistoryForInputMessages(
          TestAgentExecutionContext.withLimits(), AgentInstanceKey.of(AGENT_INSTANCE_KEY), turn);

      // then: one TOOL_RESULT item per result, each with a single-entry toolCalls array correlating
      // it to the originating tool call. The first result carries its content block; the second has
      // no content, yielding an empty (now valid) content list rather than a placeholder block.
      verify(historyCommand, times(2)).role(AgentHistoryRole.TOOL_RESULT);
      verify(historyCommand, times(2)).iteration(1);
      verify(historyCommand, times(2)).execute();

      verify(historyCommand, times(2)).content(contentCaptor.capture());
      final var contents = contentCaptor.getAllValues();
      assertThat(contents).hasSize(2);
      assertThat(contents.get(0)).singleElement();
      assertThat(((AgentHistoryContent.TextContent) contents.get(0).get(0)).getText())
          .isEqualTo("sunny");
      assertThat(contents.get(1)).isEmpty();

      final ArgumentCaptor<List<AgentHistoryToolCall>> toolCallsCaptor =
          ArgumentCaptor.forClass(List.class);
      verify(historyCommand, times(2)).toolCalls(toolCallsCaptor.capture());
      final var toolCalls = toolCallsCaptor.getAllValues();
      assertThat(toolCalls.get(0))
          .singleElement()
          .satisfies(
              tc -> {
                assertThat(tc.getToolCallId()).isEqualTo("a");
                assertThat(tc.getToolName()).isEqualTo("getWeather");
                assertThat(tc.getElementId()).isEqualTo("getWeather");
                assertThat(tc.getArguments()).isEmpty();
              });
      assertThat(toolCalls.get(1))
          .singleElement()
          .satisfies(
              tc -> {
                assertThat(tc.getToolCallId()).isEqualTo("b");
                assertThat(tc.getToolName()).isEqualTo("getTime");
                assertThat(tc.getElementId()).isEqualTo("getTime");
                assertThat(tc.getArguments()).isEmpty();
              });
    }

    @Test
    void shouldDefaultNullToolResultIdAndNameToEmptyStrings() {
      givenHistoryCommand();

      // a partial tool result missing its id/name must not fail the turn as long as the required
      // elementId is present
      final var turn =
          new AgentConversationTurn(
              1,
              List.of(
                  ToolCallResultMessage.builder()
                      .results(
                          List.of(
                              ToolCallResult.builder()
                                  .elementId("getTime")
                                  .content("partial")
                                  .build()))
                      .build()),
              null,
              AgentMetrics.empty());

      // when / then: no NPE, empty-string identifiers, elementId preserved
      client.createHistoryForInputMessages(
          TestAgentExecutionContext.withLimits(), AgentInstanceKey.of(AGENT_INSTANCE_KEY), turn);

      final ArgumentCaptor<List<AgentHistoryToolCall>> toolCallsCaptor =
          ArgumentCaptor.forClass(List.class);
      verify(historyCommand).toolCalls(toolCallsCaptor.capture());
      assertThat(toolCallsCaptor.getValue())
          .singleElement()
          .satisfies(
              tc -> {
                assertThat(tc.getToolCallId()).isEmpty();
                assertThat(tc.getToolName()).isEmpty();
                assertThat(tc.getElementId()).isEqualTo("getTime");
              });
    }

    @Test
    void shouldThrowWhenToolResultElementIdCannotBeResolved() {
      // a tool result with neither an elementId nor a name leaves the required elementId
      // unresolvable
      final var turn =
          new AgentConversationTurn(
              1,
              List.of(
                  ToolCallResultMessage.builder()
                      .results(List.of(ToolCallResult.builder().content("partial").build()))
                      .build()),
              null,
              AgentMetrics.empty());

      // when / then
      assertThatThrownBy(
              () ->
                  client.createHistoryForInputMessages(
                      TestAgentExecutionContext.withLimits(),
                      AgentInstanceKey.of(AGENT_INSTANCE_KEY),
                      turn))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Cannot resolve element id");
    }

    @Test
    void shouldMapObjectContentToObjectBlock() {
      givenHistoryCommand();

      // given
      final var turn =
          new AgentConversationTurn(
              1,
              List.of(
                  UserMessage.builder()
                      .content(List.of(ObjectContent.objectContent(Map.of("key", "value"))))
                      .build()),
              null,
              AgentMetrics.empty());

      // when
      client.createHistoryForInputMessages(
          TestAgentExecutionContext.withLimits(), AgentInstanceKey.of(AGENT_INSTANCE_KEY), turn);

      // then
      verify(historyCommand).content(contentCaptor.capture());
      assertThat(contentCaptor.getValue())
          .singleElement()
          .isInstanceOfSatisfying(
              AgentHistoryContent.ObjectContent.class,
              object -> assertThat(object.getObject()).containsEntry("key", "value"));
    }

    @Test
    void shouldCreateAssistantHistoryItemWithToolCallsAndMetricsAfterChat() {
      givenHistoryCommand();

      // given
      final var assistantMessage =
          AssistantMessage.builder()
              .content(MessageUtil.singleTextContent("Calling tools"))
              .toolCalls(
                  List.of(
                      ToolCall.builder().id("tc-1").name("getWeather").arguments(Map.of()).build()))
              .build();
      final var turn =
          new AgentConversationTurn(
              2,
              List.of(),
              assistantMessage,
              new AgentMetrics(1, new TokenUsage(11, 22), 1, Duration.ofMillis(345)));

      // when
      client.createHistoryForAssistantMessage(
          TestAgentExecutionContext.withLimits(), AgentInstanceKey.of(AGENT_INSTANCE_KEY), turn);

      // then
      verify(historyCommand).role(AgentHistoryRole.ASSISTANT);
      verify(historyCommand).iteration(2);

      final ArgumentCaptor<List<AgentHistoryToolCall>> toolCallsCaptor =
          ArgumentCaptor.forClass(List.class);
      verify(historyCommand).toolCalls(toolCallsCaptor.capture());
      assertThat(toolCallsCaptor.getValue())
          .singleElement()
          .satisfies(
              tc -> {
                assertThat(tc.getToolCallId()).isEqualTo("tc-1");
                assertThat(tc.getToolName()).isEqualTo("getWeather");
                // ad-hoc tool: element id == tool name (gateway registry resolves to empty)
                assertThat(tc.getElementId()).isEqualTo("getWeather");
              });

      final ArgumentCaptor<AgentHistoryMetrics> metricsCaptor =
          ArgumentCaptor.forClass(AgentHistoryMetrics.class);
      verify(historyCommand).metrics(metricsCaptor.capture());
      assertThat(metricsCaptor.getValue().getInputTokens()).isEqualTo(11L);
      assertThat(metricsCaptor.getValue().getOutputTokens()).isEqualTo(22L);
      assertThat(metricsCaptor.getValue().getDurationMs()).isEqualTo(345L);

      verify(historyCommand).execute();
    }

    @Test
    void shouldResolveElementIdForGatewayAssistantToolCall() {
      givenHistoryCommand();

      // a gateway tool call keeps its namespaced name as toolName; elementId is the resolved
      // BPMN element id from the gateway handlers
      when(gatewayToolHandlers.resolveElementId("MCP_McpTest___greet"))
          .thenReturn(Optional.of("McpTest"));

      final var assistantMessage =
          AssistantMessage.builder()
              .content(MessageUtil.singleTextContent("Calling MCP tool"))
              .toolCalls(
                  List.of(
                      ToolCall.builder()
                          .id("tc-9")
                          .name("MCP_McpTest___greet")
                          .arguments(Map.of("name", "Peter"))
                          .build()))
              .build();
      final var turn =
          new AgentConversationTurn(
              1, List.of(), assistantMessage, new AgentMetrics(1, new TokenUsage(1, 1), 1));

      // when
      client.createHistoryForAssistantMessage(
          TestAgentExecutionContext.withLimits(), AgentInstanceKey.of(AGENT_INSTANCE_KEY), turn);

      // then
      final ArgumentCaptor<List<AgentHistoryToolCall>> toolCallsCaptor =
          ArgumentCaptor.forClass(List.class);
      verify(historyCommand).toolCalls(toolCallsCaptor.capture());
      assertThat(toolCallsCaptor.getValue())
          .singleElement()
          .satisfies(
              tc -> {
                assertThat(tc.getToolCallId()).isEqualTo("tc-9");
                assertThat(tc.getToolName()).isEqualTo("MCP_McpTest___greet");
                assertThat(tc.getElementId()).isEqualTo("McpTest");
              });
    }

    @Test
    void shouldCreateToolOnlyAssistantItemWithEmptyContent() {
      givenHistoryCommand();

      // given: a tool-only assistant message (no text content)
      final var assistantMessage =
          AssistantMessage.builder()
              .toolCalls(
                  List.of(
                      ToolCall.builder().id("tc-1").name("getWeather").arguments(Map.of()).build()))
              .build();
      final var turn =
          new AgentConversationTurn(
              1, List.of(), assistantMessage, new AgentMetrics(1, TokenUsage.empty(), 1));

      // when
      client.createHistoryForAssistantMessage(
          TestAgentExecutionContext.withLimits(), AgentInstanceKey.of(AGENT_INSTANCE_KEY), turn);

      // then: empty content is valid since the tool call carries the turn's intent
      verify(historyCommand).content(contentCaptor.capture());
      assertThat(contentCaptor.getValue()).isEmpty();
    }

    @Test
    void shouldMapCamundaDocumentContentToDocumentBlock() {
      givenHistoryCommand();

      // given: a user message carrying a Camunda document reference
      final var ref = mock(CamundaDocumentReference.class);
      when(ref.getDocumentId()).thenReturn("doc-1");
      when(ref.getStoreId()).thenReturn("store-1");
      when(ref.getContentHash()).thenReturn("hash-1");
      final var document = mock(Document.class);
      when(document.reference()).thenReturn(ref);
      when(document.metadata()).thenReturn(null);

      final var turn =
          new AgentConversationTurn(
              1,
              List.of(
                  UserMessage.builder()
                      .content(List.of(DocumentContent.documentContent(document)))
                      .build()),
              null,
              AgentMetrics.empty());

      // when
      client.createHistoryForInputMessages(
          TestAgentExecutionContext.withLimits(), AgentInstanceKey.of(AGENT_INSTANCE_KEY), turn);

      // then: document reference is built via the client library without throwing
      verify(historyCommand).content(contentCaptor.capture());
      assertThat(contentCaptor.getValue())
          .singleElement()
          .isInstanceOfSatisfying(
              AgentHistoryContent.DocumentContent.class,
              doc -> assertThat(doc.getDocumentReference().getDocumentId()).isEqualTo("doc-1"));
    }

    @Test
    void shouldThrowWhenDocumentReferenceTypeUnsupported() {
      // given: a document carrying an unsupported reference type
      final var document = mock(Document.class);
      when(document.reference()).thenReturn(mock(InlineDocumentReference.class));
      final var turn =
          new AgentConversationTurn(
              1,
              List.of(
                  UserMessage.builder()
                      .content(List.of(DocumentContent.documentContent(document)))
                      .build()),
              null,
              AgentMetrics.empty());

      // when / then
      assertThatThrownBy(
              () ->
                  client.createHistoryForInputMessages(
                      TestAgentExecutionContext.withLimits(),
                      AgentInstanceKey.of(AGENT_INSTANCE_KEY),
                      turn))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Unsupported document reference type");
    }

    @Test
    void shouldThrowWhenExternalDocumentReferenceMissingName() {
      // given: an external document reference with a url but no name
      final var ref = mock(ExternalDocumentReference.class);
      when(ref.url()).thenReturn("https://example.com/doc");
      when(ref.name()).thenReturn(null);
      final var document = mock(Document.class);
      when(document.reference()).thenReturn(ref);
      final var turn =
          new AgentConversationTurn(
              1,
              List.of(
                  UserMessage.builder()
                      .content(List.of(DocumentContent.documentContent(document)))
                      .build()),
              null,
              AgentMetrics.empty());

      // when / then
      assertThatThrownBy(
              () ->
                  client.createHistoryForInputMessages(
                      TestAgentExecutionContext.withLimits(),
                      AgentInstanceKey.of(AGENT_INSTANCE_KEY),
                      turn))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("External document reference requires both url and name");
    }

    @Test
    void shouldThrowWhenAssistantMessageHasNeitherContentNorToolCalls() {
      // given: an assistant message with no content and no tool calls
      final var turn =
          new AgentConversationTurn(
              1,
              List.of(),
              AssistantMessage.builder().build(),
              new AgentMetrics(1, TokenUsage.empty(), 0));

      // when / then
      assertThatThrownBy(
              () ->
                  client.createHistoryForAssistantMessage(
                      TestAgentExecutionContext.withLimits(),
                      AgentInstanceKey.of(AGENT_INSTANCE_KEY),
                      turn))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("neither content nor tool calls");
    }

    @Test
    void shouldThrowHistoryItemFailedWhenRetriesExhausted() {
      givenHistoryCommand();

      // given
      when(historyCommand.execute())
          .thenThrow(new ClientHttpException(500, "Internal Server Error"));
      final var turn =
          new AgentConversationTurn(
              1,
              List.of(UserMessage.builder().content(MessageUtil.singleTextContent("hi")).build()),
              null,
              AgentMetrics.empty());

      // when / then
      assertThatThrownBy(
              () ->
                  client.createHistoryForInputMessages(
                      TestAgentExecutionContext.withLimits(),
                      AgentInstanceKey.of(AGENT_INSTANCE_KEY),
                      turn))
          .isInstanceOfSatisfying(
              ConnectorException.class,
              e ->
                  assertThat(e.getErrorCode())
                      .isEqualTo(ERROR_CODE_AGENT_INSTANCE_HISTORY_ITEM_FAILED));

      assertThat(recordedSleeps).hasSize(4);
    }

    @Test
    void shouldSkipBeforeChatWhenAgentInstanceKeyNull() {
      final var turn =
          new AgentConversationTurn(
              1,
              List.of(UserMessage.builder().content(MessageUtil.singleTextContent("hi")).build()),
              null,
              AgentMetrics.empty());

      client.createHistoryForInputMessages(TestAgentExecutionContext.withLimits(), null, turn);

      verifyNoInteractions(historyCommand);
      verify(camundaClient, never()).newCreateAgentHistoryItemCommand(anyLong());
    }

    @Test
    void shouldSkipAfterChatWhenAgentInstanceKeyNull() {
      final var turn =
          new AgentConversationTurn(
              1,
              List.of(),
              AssistantMessage.builder().content(MessageUtil.singleTextContent("done")).build(),
              new AgentMetrics(1, TokenUsage.empty(), 0));

      client.createHistoryForAssistantMessage(TestAgentExecutionContext.withLimits(), null, turn);

      verifyNoInteractions(historyCommand);
      verify(camundaClient, never()).newCreateAgentHistoryItemCommand(anyLong());
    }
  }

  private static class TestAgentExecutionContext implements AgentExecutionContext {

    public static TestAgentExecutionContext withoutLimits() {
      return new TestAgentExecutionContext(null);
    }

    public static TestAgentExecutionContext withLimits() {
      return new TestAgentExecutionContext(new LimitsConfiguration(10));
    }

    private final TestJobContext jobContext;

    private final LimitsConfiguration limitsConfiguration;

    private TestAgentExecutionContext(LimitsConfiguration limitsConfiguration) {
      this.jobContext = new TestJobContext(Map::of, () -> "");
      jobContext.setElementInstanceKey(ELEMENT_INSTANCE_KEY);
      jobContext.setJobKey(JOB_KEY);

      this.limitsConfiguration = limitsConfiguration;
    }

    @Override
    public JobContext jobContext() {
      return jobContext;
    }

    @Override
    public AgentContext initialAgentContext() {
      return null;
    }

    @Override
    public List<ToolCallResult> initialToolCallResults() {
      return List.of();
    }

    @Override
    public List<AdHocToolElement> toolElements() {
      return List.of();
    }

    @Override
    public PromptConfiguration.UserPromptConfiguration userPrompt() {
      return null;
    }

    @Override
    public AgentConfiguration configuration() {
      return new AgentConfiguration(
          new OpenAiProviderConfiguration(
              new OpenAiProviderConfiguration.OpenAiConnection(
                  null, null, new OpenAiProviderConfiguration.OpenAiModel("gpt-4o", null))),
          new PromptConfiguration.SystemPromptConfiguration("system prompt"),
          null,
          null,
          limitsConfiguration,
          null,
          null);
    }
  }
}
