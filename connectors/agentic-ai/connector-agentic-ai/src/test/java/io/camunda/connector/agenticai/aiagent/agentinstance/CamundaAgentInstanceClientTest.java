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

import io.camunda.client.CamundaClient;
import io.camunda.client.api.ProblemDetail;
import io.camunda.client.api.command.AgentInstanceHistoryContent;
import io.camunda.client.api.command.AgentInstanceHistoryMetrics;
import io.camunda.client.api.command.AgentInstanceHistoryToolCall;
import io.camunda.client.api.command.AgentInstanceUpdateStatus;
import io.camunda.client.api.command.ClientHttpException;
import io.camunda.client.api.command.CreateAgentInstanceCommandStep1;
import io.camunda.client.api.command.CreateAgentInstanceCommandStep1.CreateAgentInstanceCommandStep5;
import io.camunda.client.api.command.ProblemException;
import io.camunda.client.api.command.UpdateAgentInstanceCommandStep1;
import io.camunda.client.api.command.UpdateAgentInstanceCommandStep1.AgentTool;
import io.camunda.client.api.command.UpdateAgentInstanceCommandStep1.UpdateAgentInstanceCommandStep2;
import io.camunda.client.api.response.CreateAgentInstanceResponse;
import io.camunda.client.api.search.enums.AgentInstanceHistoryRole;
import io.camunda.client.impl.command.CreateAgentHistoryItemCommandImpl;
import io.camunda.connector.agenticai.adhoctoolsschema.model.AdHocToolElement;
import io.camunda.connector.agenticai.aiagent.model.AgentConfiguration;
import io.camunda.connector.agenticai.aiagent.model.AgentContext;
import io.camunda.connector.agenticai.aiagent.model.AgentConversationTurn;
import io.camunda.connector.agenticai.aiagent.model.AgentExecutionContext;
import io.camunda.connector.agenticai.aiagent.model.AgentMetrics;
import io.camunda.connector.agenticai.aiagent.model.AgentMetrics.TokenUsage;
import io.camunda.connector.agenticai.aiagent.model.message.AssistantMessage;
import io.camunda.connector.agenticai.aiagent.model.message.MessageUtil;
import io.camunda.connector.agenticai.aiagent.model.message.ToolCallResultMessage;
import io.camunda.connector.agenticai.aiagent.model.message.UserMessage;
import io.camunda.connector.agenticai.aiagent.model.message.content.DocumentContent;
import io.camunda.connector.agenticai.aiagent.model.message.content.ObjectContent;
import io.camunda.connector.agenticai.aiagent.model.request.LimitsConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.PromptConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.provider.OpenAiProviderConfiguration;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCall;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCallResult;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolDefinition;
import io.camunda.connector.agenticai.aiagent.tool.GatewayToolHandlerRegistry;
import io.camunda.connector.agenticai.autoconfigure.AgenticAiConnectorsConfigurationProperties;
import io.camunda.connector.api.document.Document;
import io.camunda.connector.api.document.DocumentReference.CamundaDocumentReference;
import io.camunda.connector.api.document.DocumentReference.ExternalDocumentReference;
import io.camunda.connector.api.document.DocumentReference.InlineDocumentReference;
import io.camunda.connector.api.error.ConnectorException;
import io.camunda.connector.api.outbound.JobContext;
import io.camunda.connector.runtime.test.outbound.TestJobContext;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
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
    var historyMapper = new AgentInstanceHistoryMapper(gatewayToolHandlers);
    var toolMapper = new AgentInstanceToolMapper(gatewayToolHandlers);
    client =
        new CamundaAgentInstanceClient(
            camundaClient, RETRIES_CONFIGURATION, recordedSleeps::add, historyMapper, toolMapper);
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
          .thenThrow(new ClientHttpException(503, "Service Unavailable"))
          .thenReturn(response);
      when(response.getAgentInstanceKey()).thenReturn(999L);

      final AgentInstanceKey key = client.create(TestAgentExecutionContext.withLimits());

      assertThat(key).isEqualTo(AgentInstanceKey.of(999L));
      assertThat(recordedSleeps).hasSize(1);
      assertThat(recordedSleeps).containsExactly(Duration.ofSeconds(1));
      verify(camundaClient, times(2)).newCreateAgentInstanceCommand();
    }

    @Test
    void shouldThrowConnectorExceptionImmediatelyForHttp404PermanentError() {
      // given: a 404 from the create endpoint (x-eventually-consistent: false) means the
      // referenced element instance genuinely doesn't exist, not a not-yet-visible record
      givenCreateCommandWithMaxModelCalls();
      when(step5.execute()).thenThrow(new ClientHttpException(404, "Not Found"));

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

    @Test
    void shouldReturnExistingAgentInstanceKeyOnConflictWithParseableDetail() {
      // given: a 409 ALREADY_EXISTS response whose detail embeds the existing agent instance key
      givenCreateCommandWithMaxModelCalls();
      final var detail =
          "Command 'CREATE' rejected with code 'ALREADY_EXISTS': Expected to associate element "
              + "instance with key '77' with an agent instance, but it is already associated with "
              + "agent instance with key '999'.";
      when(step5.execute()).thenThrow(conflictException(detail));

      // when
      final AgentInstanceKey key = client.create(TestAgentExecutionContext.withLimits());

      // then: the existing key is reused, no retry
      assertThat(key).isEqualTo(AgentInstanceKey.of(999L));
      assertThat(recordedSleeps).isEmpty();
      verify(camundaClient, times(1)).newCreateAgentInstanceCommand();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(
        strings = {
          // unrelated wording
          "Some unrelated conflict message.",
          // whole message must match, not just a fragment -- extra text before/after an
          // otherwise well-formed detail must not be accepted
          "extra prefix Command 'CREATE' rejected with code 'ALREADY_EXISTS': Expected to "
              + "associate element instance with key '77' with an agent instance, but it is "
              + "already associated with agent instance with key '999'. extra suffix",
          // well-worded detail whose embedded key isn't numeric
          "Command 'CREATE' rejected with code 'ALREADY_EXISTS': Expected to associate element "
              + "instance with key '77' with an agent instance, but it is already associated "
              + "with agent instance with key 'abc'."
        })
    void shouldThrowConnectorExceptionImmediatelyOnUnparseableConflictDetail(
        @Nullable String detail) {
      // given: a 409 ALREADY_EXISTS response whose detail doesn't match the expected contract
      givenCreateCommandWithMaxModelCalls();
      when(step5.execute()).thenThrow(conflictException(detail));

      // when / then: the conflict cannot be resolved, so it fails permanently, no retry
      assertThatThrownBy(() -> client.create(TestAgentExecutionContext.withLimits()))
          .isInstanceOfSatisfying(
              ConnectorException.class,
              e ->
                  assertThat(e.getErrorCode())
                      .isEqualTo(ERROR_CODE_AGENT_INSTANCE_CREATION_FAILED));

      assertThat(recordedSleeps).isEmpty();
      verify(camundaClient, times(1)).newCreateAgentInstanceCommand();
    }

    private ProblemException conflictException(@Nullable String detail) {
      final var problemDetail =
          new ProblemDetail().setStatus(409).setTitle("ALREADY_EXISTS").setDetail(detail);
      return new ProblemException(409, "Conflict", problemDetail);
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
    void shouldThrowConnectorExceptionImmediatelyForHttp404PermanentError() {
      // given: the update endpoint is x-eventually-consistent: false and Zeebe key-based
      // partition routing guarantees the create is visible before the key is returned, so a 404
      // means the agent instance genuinely doesn't exist rather than being not-yet-visible
      givenUpdateCommand();
      final var agentInstanceKey = AgentInstanceKey.of(AGENT_INSTANCE_KEY);
      when(updateCommandStep2.execute()).thenThrow(new ClientHttpException(404, "Not Found"));

      // when / then: 404 is permanent for update → fails immediately, no retries
      assertThatThrownBy(
              () ->
                  client.update(
                      TestAgentExecutionContext.withLimits(),
                      agentInstanceKey,
                      AgentInstanceUpdateRequest.statusOnly(AgentInstanceUpdateStatus.THINKING)))
          .isInstanceOfSatisfying(
              ConnectorException.class,
              e -> assertThat(e.getErrorCode()).isEqualTo(ERROR_CODE_AGENT_INSTANCE_UPDATE_FAILED));

      assertThat(recordedSleeps).isEmpty();
      verify(camundaClient, times(1)).newUpdateAgentInstanceCommand(AGENT_INSTANCE_KEY);
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

    @SuppressWarnings("unchecked")
    @Test
    void shouldBuildCommandWithToolsForAdHocTools() {
      givenUpdateCommand();

      // given: ad-hoc tools where name == elementId
      final var tools =
          List.of(
              ToolDefinition.builder()
                  .name("getWeather")
                  .description("Get the weather forecast")
                  .inputSchema(Map.of("type", "object"))
                  .build(),
              ToolDefinition.builder()
                  .name("calculateSum")
                  .description("Calculate a sum")
                  .inputSchema(Map.of("type", "object"))
                  .build());

      final var request =
          AgentInstanceUpdateRequest.builder()
              .status(AgentInstanceUpdateStatus.THINKING)
              .tools(tools)
              .build();

      // when
      client.update(
          TestAgentExecutionContext.withLimits(), AgentInstanceKey.of(AGENT_INSTANCE_KEY), request);

      // then: tools are passed to the command
      final ArgumentCaptor<List<AgentTool>> toolsCaptor = ArgumentCaptor.forClass(List.class);
      verify(updateCommandStep2).tools(toolsCaptor.capture());
      final var capturedTools = toolsCaptor.getValue();
      assertThat(capturedTools).hasSize(2);
      assertThat(capturedTools.get(0).getName()).isEqualTo("getWeather");
      assertThat(capturedTools.get(0).getDescription()).isEqualTo("Get the weather forecast");
      assertThat(capturedTools.get(0).getElementId()).isEqualTo("getWeather");
      assertThat(capturedTools.get(1).getName()).isEqualTo("calculateSum");
      assertThat(capturedTools.get(1).getDescription()).isEqualTo("Calculate a sum");
      assertThat(capturedTools.get(1).getElementId()).isEqualTo("calculateSum");
      verify(updateCommandStep2).execute();
    }

    @SuppressWarnings("unchecked")
    @Test
    void shouldResolveElementIdForGatewayToolsInUpdate() {
      givenUpdateCommand();

      // given: a gateway tool with a resolved elementId
      when(gatewayToolHandlers.resolveElementId("MCP_McpTest___greet"))
          .thenReturn(Optional.of("McpTest"));

      final var tools =
          List.of(
              ToolDefinition.builder()
                  .name("MCP_McpTest___greet")
                  .description("Greet someone")
                  .inputSchema(Map.of("type", "object"))
                  .build());

      final var request =
          AgentInstanceUpdateRequest.builder()
              .status(AgentInstanceUpdateStatus.TOOL_CALLING)
              .tools(tools)
              .build();

      // when
      client.update(
          TestAgentExecutionContext.withLimits(), AgentInstanceKey.of(AGENT_INSTANCE_KEY), request);

      // then: gateway tool elementId is resolved through the registry
      final ArgumentCaptor<List<AgentTool>> toolsCaptor = ArgumentCaptor.forClass(List.class);
      verify(updateCommandStep2).tools(toolsCaptor.capture());
      final var capturedTools = toolsCaptor.getValue();
      assertThat(capturedTools).hasSize(1);
      assertThat(capturedTools.get(0).getName()).isEqualTo("MCP_McpTest___greet");
      assertThat(capturedTools.get(0).getDescription()).isEqualTo("Greet someone");
      assertThat(capturedTools.get(0).getElementId()).isEqualTo("McpTest");
      verify(updateCommandStep2).execute();
    }

    @Test
    void shouldNotCallToolsWhenToolsFieldIsNull() {
      givenUpdateCommand();

      // given: no tools in the request
      final var request = AgentInstanceUpdateRequest.statusOnly(AgentInstanceUpdateStatus.THINKING);

      // when
      client.update(
          TestAgentExecutionContext.withLimits(), AgentInstanceKey.of(AGENT_INSTANCE_KEY), request);

      // then: tools() is never called on the command
      verify(updateCommandStep2, never()).tools(any());
      verify(updateCommandStep2).execute();
    }

    @Test
    void shouldNotCallToolsWhenToolsFieldIsEmpty() {
      givenUpdateCommand();

      // given: empty tools list in the request
      final var request =
          AgentInstanceUpdateRequest.builder()
              .status(AgentInstanceUpdateStatus.THINKING)
              .tools(List.of())
              .build();

      // when
      client.update(
          TestAgentExecutionContext.withLimits(), AgentInstanceKey.of(AGENT_INSTANCE_KEY), request);

      // then: tools() is never called on the command
      verify(updateCommandStep2, never()).tools(any());
      verify(updateCommandStep2).execute();
    }
  }

  @Nested
  class HistoryItems {

    private static final OffsetDateTime TURN_INGESTION_TIMESTAMP =
        OffsetDateTime.parse("2026-07-02T10:00:00Z");

    @Captor private ArgumentCaptor<List<AgentInstanceHistoryContent>> contentCaptor;

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
          TestAgentExecutionContext.withLimits(),
          AgentInstanceKey.of(AGENT_INSTANCE_KEY),
          turn,
          Optional.empty(),
          TURN_INGESTION_TIMESTAMP);

      // then: the passed-in turn ingestion timestamp is used verbatim, not now()
      verify(historyCommand).elementInstanceKey(ELEMENT_INSTANCE_KEY);
      verify(historyCommand).jobKey(JOB_KEY);
      verify(historyCommand).role(AgentInstanceHistoryRole.USER);
      verify(historyCommand).loopIteration(3);
      verify(historyCommand).producedAt(TURN_INGESTION_TIMESTAMP);
      verify(historyCommand).content(contentCaptor.capture());
      verify(historyCommand, never()).toolCalls(any());
      verify(historyCommand, never()).metrics(any());
      verify(historyCommand).execute();

      assertThat(contentCaptor.getValue())
          .singleElement()
          .isInstanceOfSatisfying(
              AgentInstanceHistoryContent.TextContent.class,
              text -> assertThat(text.getText()).isEqualTo("Hello there"));
    }

    @Test
    void shouldCreateOneToolResultHistoryItemPerResultBeforeChat() {
      givenHistoryCommand();

      // given: a single tool-call-result message carrying two results (elementId already resolved
      // on the model upstream, == tool name for these ad-hoc tools), each with its own distinct
      // completedAt, neither of which is the turn ingestion timestamp
      final var fastCompletedAt = OffsetDateTime.parse("2026-07-02T09:59:50Z");
      final var slowCompletedAt = OffsetDateTime.parse("2026-07-02T09:59:58Z");
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
                                  .completedAt(fastCompletedAt)
                                  .build(),
                              ToolCallResult.builder()
                                  .id("b")
                                  .name("getTime")
                                  .elementId("getTime")
                                  .completedAt(slowCompletedAt)
                                  .build()))
                      .build()),
              null,
              AgentMetrics.empty());

      // and: the previous turn whose assistant message requested both tools. Call "a" carries
      // arguments; call "b" has none, so result "b" yields empty arguments.
      final var previousTurn =
          new AgentConversationTurn(
              1,
              List.of(),
              AssistantMessage.builder()
                  .toolCalls(
                      List.of(
                          ToolCall.builder()
                              .id("a")
                              .name("getWeather")
                              .arguments(Map.of("city", "Berlin"))
                              .build(),
                          ToolCall.builder().id("b").name("getTime").build()))
                  .build(),
              AgentMetrics.empty());

      // when
      client.createHistoryForInputMessages(
          TestAgentExecutionContext.withLimits(),
          AgentInstanceKey.of(AGENT_INSTANCE_KEY),
          turn,
          Optional.of(previousTurn),
          TURN_INGESTION_TIMESTAMP);

      // then: one TOOL_RESULT item per result, each with a single-entry toolCalls array correlating
      // it to the originating tool call. The first result carries its content block; the second has
      // no content, yielding an empty (now valid) content list rather than a placeholder block.
      verify(historyCommand, times(2)).role(AgentInstanceHistoryRole.TOOL_RESULT);
      verify(historyCommand, times(2)).loopIteration(1);
      verify(historyCommand, times(2)).execute();

      // each result's own completedAt is used, not the shared turn ingestion timestamp
      verify(historyCommand).producedAt(fastCompletedAt);
      verify(historyCommand).producedAt(slowCompletedAt);
      verify(historyCommand, never()).producedAt(TURN_INGESTION_TIMESTAMP);

      verify(historyCommand, times(2)).content(contentCaptor.capture());
      final var contents = contentCaptor.getAllValues();
      assertThat(contents).hasSize(2);
      assertThat(contents.get(0)).singleElement();
      assertThat(((AgentInstanceHistoryContent.TextContent) contents.get(0).get(0)).getText())
          .isEqualTo("sunny");
      assertThat(contents.get(1)).isEmpty();

      final ArgumentCaptor<List<AgentInstanceHistoryToolCall>> toolCallsCaptor =
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
                assertThat(tc.getArguments()).containsExactlyEntriesOf(Map.of("city", "Berlin"));
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
    void shouldThrowWhenToolResultHasNoOriginatingToolCall() {
      // a tool result with a (non-null) id that does not correlate to any tool call in the previous
      // turn is an invariant violation and must fail rather than silently emit empty arguments
      final var turn =
          new AgentConversationTurn(
              1,
              List.of(
                  ToolCallResultMessage.builder()
                      .results(
                          List.of(
                              ToolCallResult.builder()
                                  .id("orphan")
                                  .name("getWeather")
                                  .elementId("getWeather")
                                  .content("sunny")
                                  .build()))
                      .build()),
              null,
              AgentMetrics.empty());

      // when / then: no matching originating tool call in the previous turn
      assertThatThrownBy(
              () ->
                  client.createHistoryForInputMessages(
                      TestAgentExecutionContext.withLimits(),
                      AgentInstanceKey.of(AGENT_INSTANCE_KEY),
                      turn,
                      Optional.empty(),
                      TURN_INGESTION_TIMESTAMP))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("No originating tool call found")
          .hasMessageContaining("orphan");
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
                                  .completedAt(TURN_INGESTION_TIMESTAMP)
                                  .build()))
                      .build()),
              null,
              AgentMetrics.empty());

      // when / then: no NPE, empty-string identifiers, elementId preserved
      client.createHistoryForInputMessages(
          TestAgentExecutionContext.withLimits(),
          AgentInstanceKey.of(AGENT_INSTANCE_KEY),
          turn,
          Optional.empty(),
          TURN_INGESTION_TIMESTAMP);

      final ArgumentCaptor<List<AgentInstanceHistoryToolCall>> toolCallsCaptor =
          ArgumentCaptor.forClass(List.class);
      verify(historyCommand).toolCalls(toolCallsCaptor.capture());
      assertThat(toolCallsCaptor.getValue())
          .singleElement()
          .satisfies(
              tc -> {
                assertThat(tc.getToolCallId()).isEmpty();
                assertThat(tc.getToolName()).isEmpty();
                assertThat(tc.getElementId()).isEqualTo("getTime");
                assertThat(tc.getArguments()).isEmpty();
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
                      turn,
                      Optional.empty(),
                      TURN_INGESTION_TIMESTAMP))
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
          TestAgentExecutionContext.withLimits(),
          AgentInstanceKey.of(AGENT_INSTANCE_KEY),
          turn,
          Optional.empty(),
          TURN_INGESTION_TIMESTAMP);

      // then
      verify(historyCommand).content(contentCaptor.capture());
      assertThat(contentCaptor.getValue())
          .singleElement()
          .isInstanceOfSatisfying(
              AgentInstanceHistoryContent.ObjectContent.class,
              object -> assertThat(object.getObject()).isEqualTo(Map.of("key", "value")));
    }

    @Test
    void shouldMapNonMapObjectContentToObjectBlock() {
      givenHistoryCommand();

      // given: a list-shaped object result (e.g. a "list users" tool) mapped to OBJECT content
      final var users = List.of("alice", "bob");
      final var turn =
          new AgentConversationTurn(
              1,
              List.of(
                  UserMessage.builder()
                      .content(List.of(ObjectContent.objectContent(users)))
                      .build()),
              null,
              AgentMetrics.empty());

      // when
      client.createHistoryForInputMessages(
          TestAgentExecutionContext.withLimits(),
          AgentInstanceKey.of(AGENT_INSTANCE_KEY),
          turn,
          Optional.empty(),
          TURN_INGESTION_TIMESTAMP);

      // then
      verify(historyCommand).content(contentCaptor.capture());
      assertThat(contentCaptor.getValue())
          .singleElement()
          .isInstanceOfSatisfying(
              AgentInstanceHistoryContent.ObjectContent.class,
              object -> assertThat(object.getObject()).isEqualTo(users));
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
          TestAgentExecutionContext.withLimits(),
          AgentInstanceKey.of(AGENT_INSTANCE_KEY),
          turn,
          TURN_INGESTION_TIMESTAMP);

      // then
      verify(historyCommand).role(AgentInstanceHistoryRole.ASSISTANT);
      verify(historyCommand).loopIteration(2);
      verify(historyCommand).producedAt(TURN_INGESTION_TIMESTAMP);

      final ArgumentCaptor<List<AgentInstanceHistoryToolCall>> toolCallsCaptor =
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

      final ArgumentCaptor<AgentInstanceHistoryMetrics> metricsCaptor =
          ArgumentCaptor.forClass(AgentInstanceHistoryMetrics.class);
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
          TestAgentExecutionContext.withLimits(),
          AgentInstanceKey.of(AGENT_INSTANCE_KEY),
          turn,
          TURN_INGESTION_TIMESTAMP);

      // then
      final ArgumentCaptor<List<AgentInstanceHistoryToolCall>> toolCallsCaptor =
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
          TestAgentExecutionContext.withLimits(),
          AgentInstanceKey.of(AGENT_INSTANCE_KEY),
          turn,
          TURN_INGESTION_TIMESTAMP);

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
          TestAgentExecutionContext.withLimits(),
          AgentInstanceKey.of(AGENT_INSTANCE_KEY),
          turn,
          Optional.empty(),
          TURN_INGESTION_TIMESTAMP);

      // then: document reference is built via the client library without throwing
      verify(historyCommand).content(contentCaptor.capture());
      assertThat(contentCaptor.getValue())
          .singleElement()
          .isInstanceOfSatisfying(
              AgentInstanceHistoryContent.DocumentContent.class,
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
                      turn,
                      Optional.empty(),
                      TURN_INGESTION_TIMESTAMP))
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
                      turn,
                      Optional.empty(),
                      TURN_INGESTION_TIMESTAMP))
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
                      turn,
                      TURN_INGESTION_TIMESTAMP))
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
                      turn,
                      Optional.empty(),
                      TURN_INGESTION_TIMESTAMP))
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

      client.createHistoryForInputMessages(
          TestAgentExecutionContext.withLimits(),
          null,
          turn,
          Optional.empty(),
          TURN_INGESTION_TIMESTAMP);

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

      client.createHistoryForAssistantMessage(
          TestAgentExecutionContext.withLimits(), null, turn, TURN_INGESTION_TIMESTAMP);

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
