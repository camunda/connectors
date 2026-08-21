/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.agentinstance;

import static io.camunda.connector.agenticai.aiagent.agent.AgentErrorCodes.ERROR_CODE_AGENT_INSTANCE_CREATION_FAILED;
import static io.camunda.connector.agenticai.aiagent.agent.AgentErrorCodes.ERROR_CODE_AGENT_INSTANCE_SUPERSEDED;
import static io.camunda.connector.agenticai.aiagent.agent.AgentErrorCodes.ERROR_CODE_AGENT_INSTANCE_UPDATE_FAILED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.ProblemDetail;
import io.camunda.client.api.command.AgentInstanceHistoryContent;
import io.camunda.client.api.command.AgentInstanceHistoryItem;
import io.camunda.client.api.command.AgentInstanceUpdateStatus;
import io.camunda.client.api.command.AgentTool;
import io.camunda.client.api.command.ClientHttpException;
import io.camunda.client.api.command.CreateAgentInstanceCommandStep1;
import io.camunda.client.api.command.CreateAgentInstanceCommandStep1.CreateAgentInstanceCommandStep2;
import io.camunda.client.api.command.ProblemException;
import io.camunda.client.api.command.UpdateAgentInstanceCommandStep1;
import io.camunda.client.api.command.UpdateAgentInstanceCommandStep1.UpdateAgentInstanceCommandStep2;
import io.camunda.client.api.response.CreateAgentInstanceResponse;
import io.camunda.client.api.search.enums.AgentInstanceHistoryRole;
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
import io.camunda.connector.agenticai.aiagent.model.message.content.TextContent;
import io.camunda.connector.agenticai.aiagent.model.request.LimitsConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.PromptConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.v1.OpenAiProviderConfiguration;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCall;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCallResult;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCallResultContent;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolDefinition;
import io.camunda.connector.agenticai.aiagent.tool.GatewayToolHandlerRegistry;
import io.camunda.connector.agenticai.autoconfigure.AgenticAiConnectorsConfigurationProperties;
import io.camunda.connector.api.error.ConnectorException;
import io.camunda.connector.api.error.ConnectorRetryException;
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

  private CreateAgentInstanceCommandStep2 step5;

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

  /**
   * The batched turn methods ({@code applyTurnStart}/{@code applyTurnCompletion}/{@code
   * applyToolCallResults}) replace the request-level status/metrics/tools update plus the
   * single-item history create with one combined {@code update} command carrying a {@code
   * history()} batch (plan decision 2). {@code jobKey}/{@code jobLease} live on the command, not
   * per item, unlike the old single-item {@code newCreateAgentHistoryItemCommand}.
   */
  @Nested
  class TurnStart {

    private static final OffsetDateTime TURN_INGESTION_TIMESTAMP =
        OffsetDateTime.parse("2026-07-02T10:00:00Z");

    @Captor private ArgumentCaptor<List<AgentInstanceHistoryItem>> historyCaptor;

    private AgentConfiguration configuration(String systemPrompt, List<ToolDefinition> tools) {
      return new AgentConfiguration(
              new OpenAiProviderConfiguration(
                  new OpenAiProviderConfiguration.OpenAiConnection(
                      null, null, new OpenAiProviderConfiguration.OpenAiModel("gpt-4o", null))),
              new PromptConfiguration.SystemPromptConfiguration(systemPrompt),
              null,
              null,
              null,
              null,
              null)
          .withToolDefinitions(tools);
    }

    private AgentConversationTurn userTurn(String text, String configurationFingerprint) {
      return new AgentConversationTurn(
          1,
          List.of(UserMessage.builder().content(MessageUtil.singleTextContent(text)).build()),
          null,
          AgentMetrics.empty(),
          configurationFingerprint);
    }

    /** A completed turn carrying the given configuration fingerprint, for use as previousTurn. */
    private AgentConversationTurn precedingTurn(String configurationFingerprint) {
      return new AgentConversationTurn(
          1,
          List.of(),
          AssistantMessage.builder().content(MessageUtil.singleTextContent("prior")).build(),
          AgentMetrics.empty(),
          configurationFingerprint);
    }

    @Test
    void shouldSkipWhenAgentInstanceKeyIsNull() {
      final var configuration = configuration("Be nice.", List.of());

      client.applyTurnStart(
          TestAgentExecutionContext.withLimits(),
          null,
          userTurn("hi", configuration.fingerprint()),
          Optional.empty(),
          TURN_INGESTION_TIMESTAMP,
          configuration);

      verifyNoInteractions(camundaClient);
    }

    @Test
    void shouldSendOneBatchedUpdateWithThinkingStatusAndInputItem() {
      givenUpdateCommand();
      final var message =
          UserMessage.builder().content(MessageUtil.singleTextContent("Hello there")).build();
      final var configuration = configuration("Be nice.", List.of());
      final var turn =
          new AgentConversationTurn(
              3, List.of(message), null, AgentMetrics.empty(), configuration.fingerprint());

      client.applyTurnStart(
          TestAgentExecutionContext.withLimits(),
          AgentInstanceKey.of(AGENT_INSTANCE_KEY),
          turn,
          Optional.of(precedingTurn(configuration.fingerprint())),
          TURN_INGESTION_TIMESTAMP,
          configuration);

      verify(updateCommandStep2).status(AgentInstanceUpdateStatus.THINKING);
      verify(updateCommandStep2).jobKey(JOB_KEY);
      verify(updateCommandStep2, never()).jobLease(any());
      verify(updateCommandStep2).history(historyCaptor.capture());
      verify(updateCommandStep2).execute();

      // configuration unchanged from previousTurn's -- only the input item
      assertThat(historyCaptor.getValue())
          .singleElement()
          .satisfies(
              item -> {
                assertThat(item.getRole()).isEqualTo(AgentInstanceHistoryRole.USER);
                assertThat(item.getHistoryItemId()).isEqualTo(message.id().toString());
                assertThat(item.getLoopIteration()).isEqualTo(3);
                assertThat(item.getProducedAt()).isEqualTo(TURN_INGESTION_TIMESTAMP);
              });
    }

    @Test
    void shouldForwardLeaseTokenWhenActivationIsLeased() {
      givenUpdateCommand();
      final var configuration = configuration("Be nice.", List.of());

      client.applyTurnStart(
          TestAgentExecutionContext.withLeaseToken("lease-token-abc"),
          AgentInstanceKey.of(AGENT_INSTANCE_KEY),
          userTurn("hi", configuration.fingerprint()),
          Optional.of(precedingTurn(configuration.fingerprint())),
          TURN_INGESTION_TIMESTAMP,
          configuration);

      verify(updateCommandStep2).jobLease("lease-token-abc");
    }

    @Test
    void shouldNotPrependConfigurationItemWhenFingerprintUnchanged() {
      givenUpdateCommand();
      final var tools =
          List.of(
              ToolDefinition.builder()
                  .name("getWeather")
                  .description("Get the weather forecast")
                  .inputSchema(Map.of("type", "object"))
                  .build());
      final var configuration = configuration("Be nice.", tools);

      client.applyTurnStart(
          TestAgentExecutionContext.withLimits(),
          AgentInstanceKey.of(AGENT_INSTANCE_KEY),
          userTurn("hi", configuration.fingerprint()),
          Optional.of(precedingTurn(configuration.fingerprint())),
          TURN_INGESTION_TIMESTAMP,
          configuration);

      verify(updateCommandStep2).history(historyCaptor.capture());
      assertThat(historyCaptor.getValue())
          .singleElement()
          .satisfies(item -> assertThat(item.getRole()).isEqualTo(AgentInstanceHistoryRole.USER));
    }

    @Test
    void shouldPrependConfigurationItemWhenNoPreviousTurnExists() {
      // the very first turn: no completed turn precedes it, so the configuration is always new
      givenUpdateCommand();
      final var tools =
          List.of(
              ToolDefinition.builder()
                  .name("getWeather")
                  .description("Get the weather forecast")
                  .inputSchema(Map.of("type", "object"))
                  .build());
      final var configuration = configuration("Be nice.", tools);

      client.applyTurnStart(
          TestAgentExecutionContext.withLimits(),
          AgentInstanceKey.of(AGENT_INSTANCE_KEY),
          userTurn("hi", configuration.fingerprint()),
          Optional.empty(),
          TURN_INGESTION_TIMESTAMP,
          configuration);

      verify(updateCommandStep2).history(historyCaptor.capture());
      final var history = historyCaptor.getValue();
      assertThat(history).hasSize(2);

      final var configurationItem = history.get(0);
      assertThat(configurationItem.getRole()).isEqualTo(AgentInstanceHistoryRole.CONFIGURATION);
      // the fingerprint doubles as the item's historyItemId, so a repeat send with unchanged
      // content dedups for free
      assertThat(configurationItem.getHistoryItemId()).isEqualTo(configuration.fingerprint());
      assertThat(configurationItem.getLoopIteration()).isEqualTo(1);
      // a CONFIGURATION item has no natural content of its own
      assertThat(configurationItem.getContent()).isEmpty();
      assertThat(configurationItem.getSystemPrompt())
          .singleElement()
          .isInstanceOfSatisfying(
              AgentInstanceHistoryContent.TextContent.class,
              text -> assertThat(text.getText()).isEqualTo("Be nice."));
      assertThat(configurationItem.getTools())
          .singleElement()
          .satisfies(tool -> assertThat(tool.getName()).isEqualTo("getWeather"));

      assertThat(history.get(1).getRole()).isEqualTo(AgentInstanceHistoryRole.USER);
    }

    @Test
    void shouldPrependConfigurationItemWhenSystemPromptChanged() {
      givenUpdateCommand();
      final var previousConfiguration = configuration("Be nice.", List.of());
      final var configuration = configuration("Be mean.", List.of());

      client.applyTurnStart(
          TestAgentExecutionContext.withLimits(),
          AgentInstanceKey.of(AGENT_INSTANCE_KEY),
          userTurn("hi", configuration.fingerprint()),
          Optional.of(precedingTurn(previousConfiguration.fingerprint())),
          TURN_INGESTION_TIMESTAMP,
          configuration);

      verify(updateCommandStep2).history(historyCaptor.capture());
      assertThat(historyCaptor.getValue()).hasSize(2);
      assertThat(historyCaptor.getValue().get(0).getRole())
          .isEqualTo(AgentInstanceHistoryRole.CONFIGURATION);
    }

    @Test
    void shouldPrependConfigurationItemWhenToolsChanged() {
      givenUpdateCommand();
      final var previousConfiguration = configuration("Be nice.", List.of());
      final var configuration =
          configuration(
              "Be nice.",
              List.of(ToolDefinition.builder().name("getWeather").description("d").build()));

      client.applyTurnStart(
          TestAgentExecutionContext.withLimits(),
          AgentInstanceKey.of(AGENT_INSTANCE_KEY),
          userTurn("hi", configuration.fingerprint()),
          Optional.of(precedingTurn(previousConfiguration.fingerprint())),
          TURN_INGESTION_TIMESTAMP,
          configuration);

      verify(updateCommandStep2).history(historyCaptor.capture());
      assertThat(historyCaptor.getValue()).hasSize(2);
      assertThat(historyCaptor.getValue().get(0).getRole())
          .isEqualTo(AgentInstanceHistoryRole.CONFIGURATION);
    }

    @Test
    void shouldCreateOneToolResultItemPerResultUsingOwnCompletedAt() {
      givenUpdateCommand();
      final var configuration = configuration("Be nice.", List.of());
      final var fastCompletedAt = OffsetDateTime.parse("2026-07-02T09:59:50Z");
      final var turn =
          new AgentConversationTurn(
              1,
              List.of(
                  ToolCallResultMessage.builder()
                      .results(
                          List.of(
                              ToolCallResultContent.builder()
                                  .id("a")
                                  .name("getWeather")
                                  .content(List.of(TextContent.textContent("sunny")))
                                  .elementId("getWeather")
                                  .completedAt(fastCompletedAt)
                                  .build()))
                      .build()),
              null,
              AgentMetrics.empty(),
              configuration.fingerprint());
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
                              .build()))
                  .build(),
              AgentMetrics.empty(),
              configuration.fingerprint());

      client.applyTurnStart(
          TestAgentExecutionContext.withLimits(),
          AgentInstanceKey.of(AGENT_INSTANCE_KEY),
          turn,
          Optional.of(previousTurn),
          TURN_INGESTION_TIMESTAMP,
          configuration);

      verify(updateCommandStep2).history(historyCaptor.capture());
      assertThat(historyCaptor.getValue())
          .singleElement()
          .satisfies(
              item -> {
                assertThat(item.getRole()).isEqualTo(AgentInstanceHistoryRole.TOOL_RESULT);
                assertThat(item.getHistoryItemId()).isEqualTo("a");
                assertThat(item.getProducedAt()).isEqualTo(fastCompletedAt);
                assertThat(item.getToolCalls())
                    .singleElement()
                    .satisfies(
                        tc ->
                            assertThat(tc.getArguments())
                                .containsExactlyEntriesOf(Map.of("city", "Berlin")));
              });
    }

    @Test
    void shouldThrowWhenToolResultHasNoOriginatingToolCall() {
      final var configuration = configuration("Be nice.", List.of());
      final var turn =
          new AgentConversationTurn(
              1,
              List.of(
                  ToolCallResultMessage.builder()
                      .results(
                          List.of(
                              ToolCallResultContent.builder()
                                  .id("orphan")
                                  .name("getWeather")
                                  .elementId("getWeather")
                                  .content(List.of(TextContent.textContent("sunny")))
                                  .build()))
                      .build()),
              null,
              AgentMetrics.empty(),
              configuration.fingerprint());

      assertThatThrownBy(
              () ->
                  client.applyTurnStart(
                      TestAgentExecutionContext.withLimits(),
                      AgentInstanceKey.of(AGENT_INSTANCE_KEY),
                      turn,
                      Optional.empty(),
                      TURN_INGESTION_TIMESTAMP,
                      configuration))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("No originating tool call found")
          .hasMessageContaining("orphan");
    }
  }

  @Nested
  class TurnCompletion {

    private static final OffsetDateTime PRODUCED_AT = OffsetDateTime.parse("2026-07-02T10:05:00Z");

    @Captor private ArgumentCaptor<List<AgentInstanceHistoryItem>> historyCaptor;

    @Test
    void shouldSkipWhenAgentInstanceKeyIsNull() {
      final var turn =
          new AgentConversationTurn(
              1,
              List.of(),
              AssistantMessage.builder().content(MessageUtil.singleTextContent("done")).build(),
              new AgentMetrics(1, TokenUsage.empty(), 0),
              null);

      client.applyTurnCompletion(
          TestAgentExecutionContext.withLimits(),
          null,
          turn,
          PRODUCED_AT,
          AgentInstanceUpdateStatus.IDLE);

      verifyNoInteractions(camundaClient);
    }

    @Test
    void shouldSendOneBatchedUpdateWithStatusAndAssistantItemCarryingMetrics() {
      givenUpdateCommand();
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
              new AgentMetrics(1, new TokenUsage(11, 22), 1, Duration.ofMillis(345)),
              null);

      client.applyTurnCompletion(
          TestAgentExecutionContext.withLimits(),
          AgentInstanceKey.of(AGENT_INSTANCE_KEY),
          turn,
          PRODUCED_AT,
          AgentInstanceUpdateStatus.TOOL_CALLING);

      // no request-level metrics/tools anymore -- they ride on the history item instead
      verify(updateCommandStep2).status(AgentInstanceUpdateStatus.TOOL_CALLING);
      verify(updateCommandStep2).jobKey(JOB_KEY);
      verify(updateCommandStep2, never()).jobLease(any());
      verify(updateCommandStep2, never()).modelCalls(anyInt());
      verify(updateCommandStep2, never()).inputTokens(anyLong());
      verify(updateCommandStep2, never()).outputTokens(anyLong());
      verify(updateCommandStep2, never()).toolCalls(anyInt());
      verify(updateCommandStep2, never()).tools(any());
      verify(updateCommandStep2).history(historyCaptor.capture());
      verify(updateCommandStep2).execute();

      assertThat(historyCaptor.getValue())
          .singleElement()
          .satisfies(
              item -> {
                assertThat(item.getRole()).isEqualTo(AgentInstanceHistoryRole.ASSISTANT);
                assertThat(item.getHistoryItemId()).isEqualTo(assistantMessage.id().toString());
                assertThat(item.getLoopIteration()).isEqualTo(2);
                assertThat(item.getProducedAt()).isEqualTo(PRODUCED_AT);
                assertThat(item.getMetrics().getInputTokens()).isEqualTo(11L);
                assertThat(item.getMetrics().getOutputTokens()).isEqualTo(22L);
                assertThat(item.getMetrics().getDurationMs()).isEqualTo(345L);
              });
    }

    @Test
    void shouldThrowWhenAssistantMessageHasNeitherContentNorToolCalls() {
      final var turn =
          new AgentConversationTurn(
              1,
              List.of(),
              AssistantMessage.builder().build(),
              new AgentMetrics(1, TokenUsage.empty(), 0),
              null);

      assertThatThrownBy(
              () ->
                  client.applyTurnCompletion(
                      TestAgentExecutionContext.withLimits(),
                      AgentInstanceKey.of(AGENT_INSTANCE_KEY),
                      turn,
                      PRODUCED_AT,
                      AgentInstanceUpdateStatus.IDLE))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("neither content nor tool calls");
    }
  }

  @Nested
  class ToolCallResultsBatch {

    @Captor private ArgumentCaptor<List<AgentInstanceHistoryItem>> historyCaptor;

    @Test
    void shouldSkipWhenAgentInstanceKeyIsNull() {
      final var previousTurn =
          new AgentConversationTurn(
              1,
              List.of(),
              AssistantMessage.builder().toolCalls(List.of()).build(),
              AgentMetrics.empty(),
              null);

      client.applyToolCallResults(
          TestAgentExecutionContext.withLimits(),
          null,
          List.of(ToolCallResult.builder().id("a").name("getWeather").build()),
          previousTurn);

      verifyNoInteractions(camundaClient);
    }

    @Test
    void shouldSendOneBatchedUpdateWithoutChangingStatus() {
      givenUpdateCommand();
      final var completedAt = OffsetDateTime.parse("2026-07-02T09:59:50Z");
      final var arrivedResult =
          ToolCallResult.builder()
              .id("a")
              .name("getWeather")
              .content("sunny")
              .completedAt(completedAt)
              .build();
      final var previousTurn =
          new AgentConversationTurn(
              3,
              List.of(),
              AssistantMessage.builder()
                  .toolCalls(
                      List.of(
                          ToolCall.builder()
                              .id("a")
                              .name("getWeather")
                              .arguments(Map.of("city", "Berlin"))
                              .build()))
                  .build(),
              AgentMetrics.empty(),
              null);

      client.applyToolCallResults(
          TestAgentExecutionContext.withLimits(),
          AgentInstanceKey.of(AGENT_INSTANCE_KEY),
          List.of(arrivedResult),
          previousTurn);

      verify(updateCommandStep2, never()).status(any());
      verify(updateCommandStep2).jobKey(JOB_KEY);
      verify(updateCommandStep2).history(historyCaptor.capture());
      verify(updateCommandStep2).execute();

      assertThat(historyCaptor.getValue())
          .singleElement()
          .satisfies(
              item -> {
                assertThat(item.getRole()).isEqualTo(AgentInstanceHistoryRole.TOOL_RESULT);
                assertThat(item.getHistoryItemId()).isEqualTo("a");
                assertThat(item.getLoopIteration()).isEqualTo(4);
                assertThat(item.getProducedAt()).isEqualTo(completedAt);
              });
    }

    @Test
    void shouldThrowWhenArrivedToolCallResultHasNoOriginatingToolCall() {
      final var previousTurn =
          new AgentConversationTurn(
              1,
              List.of(),
              AssistantMessage.builder().toolCalls(List.of()).build(),
              AgentMetrics.empty(),
              null);

      assertThatThrownBy(
              () ->
                  client.applyToolCallResults(
                      TestAgentExecutionContext.withLimits(),
                      AgentInstanceKey.of(AGENT_INSTANCE_KEY),
                      List.of(ToolCallResult.builder().id("orphan").name("getWeather").build()),
                      previousTurn))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("No originating tool call found")
          .hasMessageContaining("orphan");
    }
  }

  /**
   * Plan decision 3: a batched update (any of the three turn methods) that gets rejected with 404
   * means the job activation was superseded, and must fail without provoking any retry at any level
   * -- unlike a batch-less {@link AgentInstanceClient#update}, which keeps today's behavior.
   */
  @Nested
  class Supersession {

    private static final OffsetDateTime TURN_INGESTION_TIMESTAMP =
        OffsetDateTime.parse("2026-07-02T10:00:00Z");

    private AgentConversationTurn userMessageTurn() {
      return new AgentConversationTurn(
          1,
          List.of(UserMessage.builder().content(MessageUtil.singleTextContent("hi")).build()),
          null,
          AgentMetrics.empty(),
          null);
    }

    private AgentConfiguration configuration() {
      return new AgentConfiguration(
          new OpenAiProviderConfiguration(
              new OpenAiProviderConfiguration.OpenAiConnection(
                  null, null, new OpenAiProviderConfiguration.OpenAiModel("gpt-4o", null))),
          new PromptConfiguration.SystemPromptConfiguration("Be nice."),
          null,
          null,
          null,
          null,
          null);
    }

    @Test
    void shouldThrowNonRetryableConnectorRetryExceptionOn404ForApplyTurnStart() {
      givenUpdateCommand();
      when(updateCommandStep2.execute()).thenThrow(new ClientHttpException(404, "Not Found"));

      assertThatThrownBy(
              () ->
                  client.applyTurnStart(
                      TestAgentExecutionContext.withLimits(),
                      AgentInstanceKey.of(AGENT_INSTANCE_KEY),
                      userMessageTurn(),
                      Optional.empty(),
                      TURN_INGESTION_TIMESTAMP,
                      configuration()))
          .isInstanceOfSatisfying(
              ConnectorRetryException.class,
              e -> {
                assertThat(e.getRetries()).isEqualTo(0);
                assertThat(e.getErrorCode()).isEqualTo(ERROR_CODE_AGENT_INSTANCE_SUPERSEDED);
              });
      assertThat(recordedSleeps).isEmpty();
    }

    @Test
    void shouldThrowNonRetryableConnectorRetryExceptionOn404ForApplyTurnCompletion() {
      givenUpdateCommand();
      when(updateCommandStep2.execute()).thenThrow(new ClientHttpException(404, "Not Found"));
      final var turn =
          new AgentConversationTurn(
              1,
              List.of(),
              AssistantMessage.builder().content(MessageUtil.singleTextContent("done")).build(),
              new AgentMetrics(1, TokenUsage.empty(), 0),
              null);

      assertThatThrownBy(
              () ->
                  client.applyTurnCompletion(
                      TestAgentExecutionContext.withLimits(),
                      AgentInstanceKey.of(AGENT_INSTANCE_KEY),
                      turn,
                      TURN_INGESTION_TIMESTAMP,
                      AgentInstanceUpdateStatus.IDLE))
          .isInstanceOfSatisfying(
              ConnectorRetryException.class, e -> assertThat(e.getRetries()).isEqualTo(0));
    }

    @Test
    void shouldThrowNonRetryableConnectorRetryExceptionOn404ForApplyToolCallResults() {
      givenUpdateCommand();
      when(updateCommandStep2.execute()).thenThrow(new ClientHttpException(404, "Not Found"));
      final var previousTurn =
          new AgentConversationTurn(
              1,
              List.of(),
              AssistantMessage.builder()
                  .toolCalls(List.of(ToolCall.builder().id("a").name("getWeather").build()))
                  .build(),
              AgentMetrics.empty(),
              null);

      assertThatThrownBy(
              () ->
                  client.applyToolCallResults(
                      TestAgentExecutionContext.withLimits(),
                      AgentInstanceKey.of(AGENT_INSTANCE_KEY),
                      List.of(
                          ToolCallResult.builder()
                              .id("a")
                              .name("getWeather")
                              .completedAt(TURN_INGESTION_TIMESTAMP)
                              .build()),
                      previousTurn))
          .isInstanceOfSatisfying(
              ConnectorRetryException.class, e -> assertThat(e.getRetries()).isEqualTo(0));
    }

    @Test
    void shouldKeepPlainUpdateFailedForBatchLessUpdateOn404() {
      // update() never carries a batch, so it is never treated as superseded -- unchanged
      givenUpdateCommand();
      when(updateCommandStep2.execute()).thenThrow(new ClientHttpException(404, "Not Found"));

      assertThatThrownBy(
              () ->
                  client.update(
                      TestAgentExecutionContext.withLimits(),
                      AgentInstanceKey.of(AGENT_INSTANCE_KEY),
                      AgentInstanceUpdateRequest.statusOnly(
                          AgentInstanceUpdateStatus.TOOL_DISCOVERY)))
          .isInstanceOfSatisfying(
              ConnectorException.class,
              e -> assertThat(e.getErrorCode()).isEqualTo(ERROR_CODE_AGENT_INSTANCE_UPDATE_FAILED))
          .isNotInstanceOf(ConnectorRetryException.class);
    }

    @Test
    void shouldKeepPermanentUpdateFailedForApplyTurnStartOn400() {
      givenUpdateCommand();
      when(updateCommandStep2.execute()).thenThrow(new ClientHttpException(400, "Bad Request"));

      assertThatThrownBy(
              () ->
                  client.applyTurnStart(
                      TestAgentExecutionContext.withLimits(),
                      AgentInstanceKey.of(AGENT_INSTANCE_KEY),
                      userMessageTurn(),
                      Optional.empty(),
                      TURN_INGESTION_TIMESTAMP,
                      configuration()))
          .isInstanceOfSatisfying(
              ConnectorException.class,
              e -> assertThat(e.getErrorCode()).isEqualTo(ERROR_CODE_AGENT_INSTANCE_UPDATE_FAILED))
          .isNotInstanceOf(ConnectorRetryException.class);
      assertThat(recordedSleeps).isEmpty();
    }

    @Test
    void shouldRetryApplyTurnStartOn5xxThenFailAfterExhaustion() {
      givenUpdateCommand();
      when(updateCommandStep2.execute())
          .thenThrow(new ClientHttpException(500, "Internal Server Error"));

      assertThatThrownBy(
              () ->
                  client.applyTurnStart(
                      TestAgentExecutionContext.withLimits(),
                      AgentInstanceKey.of(AGENT_INSTANCE_KEY),
                      userMessageTurn(),
                      Optional.empty(),
                      TURN_INGESTION_TIMESTAMP,
                      configuration()))
          .isInstanceOfSatisfying(
              ConnectorException.class,
              e -> assertThat(e.getErrorCode()).isEqualTo(ERROR_CODE_AGENT_INSTANCE_UPDATE_FAILED))
          .isNotInstanceOf(ConnectorRetryException.class);
      assertThat(recordedSleeps).hasSize(4);
    }
  }

  @Nested
  class JobLeaseFencing {

    private static final String LEASE_TOKEN = "lease-token-abc";

    @Test
    void shouldNeverForwardLeaseTokenOnUpdate() {
      givenUpdateCommand();

      // when: an activation carrying a lease token issues an update
      client.update(
          TestAgentExecutionContext.withLeaseToken(LEASE_TOKEN),
          AgentInstanceKey.of(AGENT_INSTANCE_KEY),
          AgentInstanceUpdateRequest.statusOnly(AgentInstanceUpdateStatus.THINKING));

      // then: the lease is not forwarded on the update command -- on that command it only fences a
      // batched history() list, which this status/metrics update never sends
      verify(updateCommandStep2, never()).jobLease(any());
      verify(updateCommandStep2).execute();
    }
  }

  private static class TestAgentExecutionContext implements AgentExecutionContext {

    public static TestAgentExecutionContext withoutLimits() {
      return new TestAgentExecutionContext(null);
    }

    public static TestAgentExecutionContext withLimits() {
      return new TestAgentExecutionContext(new LimitsConfiguration(10));
    }

    public static TestAgentExecutionContext withLeaseToken(String leaseToken) {
      return new TestAgentExecutionContext(new LimitsConfiguration(10), leaseToken);
    }

    private final TestJobContext jobContext;

    private final LimitsConfiguration limitsConfiguration;

    private TestAgentExecutionContext(LimitsConfiguration limitsConfiguration) {
      this(limitsConfiguration, null);
    }

    private TestAgentExecutionContext(
        LimitsConfiguration limitsConfiguration, @Nullable String leaseToken) {
      this.jobContext = new TestJobContext(Map::of, () -> "");
      jobContext.setElementInstanceKey(ELEMENT_INSTANCE_KEY);
      jobContext.setJobKey(JOB_KEY);
      if (leaseToken != null) {
        jobContext.setLeaseToken(leaseToken);
      }

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
