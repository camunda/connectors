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

  @Mock private CreateAgentInstanceCommandStep1 createCommandStep1;

  @Mock(answer = Answers.RETURNS_SELF)
  private CreateAgentInstanceCommandStep2 createCommandStep2;

  @Mock private CreateAgentInstanceResponse response;

  @Mock private UpdateAgentInstanceCommandStep1 updateCommandStep1;

  @Mock(answer = Answers.RETURNS_SELF)
  private UpdateAgentInstanceCommandStep2 updateCommandStep2;

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
    when(camundaClient.newCreateAgentInstanceCommand()).thenReturn(createCommandStep1);
    when(createCommandStep1.elementInstanceKey(ELEMENT_INSTANCE_KEY))
        .thenReturn(createCommandStep2);
  }

  private void givenUpdateCommand() {
    when(camundaClient.newUpdateAgentInstanceCommand(AGENT_INSTANCE_KEY))
        .thenReturn(updateCommandStep1);
    when(updateCommandStep1.elementInstanceKey(ELEMENT_INSTANCE_KEY))
        .thenReturn(updateCommandStep2);
  }

  @Nested
  class Create {

    @SuppressWarnings("unchecked")
    @Test
    void shouldReturnAgentInstanceKeyOnFirstSuccessfulAttempt() {
      givenCreateCommand();
      when(createCommandStep2.execute()).thenReturn(response);
      when(response.getAgentInstanceKey()).thenReturn(12345L);

      final var executionContext = TestAgentExecutionContext.withLimits();
      final AgentInstanceKey key = client.create(executionContext);

      assertThat(key).isEqualTo(AgentInstanceKey.of(12345L));
      assertThat(recordedSleeps).isEmpty();
      verify(camundaClient, times(1)).newCreateAgentInstanceCommand();

      // definition and tools are established as a CONFIGURATION history item, not direct fields
      final ArgumentCaptor<List<AgentInstanceHistoryItem>> historyCaptor =
          ArgumentCaptor.forClass(List.class);
      verify(createCommandStep1).elementInstanceKey(ELEMENT_INSTANCE_KEY);
      verify(createCommandStep2).jobKey(JOB_KEY);
      // top-level limits are forbidden alongside a history batch
      verify(createCommandStep2, never()).maxModelCalls(anyInt());
      verify(createCommandStep2, never()).jobLease(any());
      verify(createCommandStep2).history(historyCaptor.capture());

      assertThat(historyCaptor.getValue())
          .singleElement()
          .satisfies(
              item -> {
                assertThat(item.getRole()).isEqualTo(AgentInstanceHistoryRole.CONFIGURATION);
                assertThat(item.getHistoryItemId())
                    .isEqualTo(executionContext.configuration().fingerprint());
                assertThat(item.getLoopIteration()).isEqualTo(1);
                assertThat(item.getModel()).isEqualTo("gpt-4o");
                assertThat(item.getProvider()).isEqualTo(OpenAiProviderConfiguration.OPENAI_ID);
                assertThat(item.getSystemPrompt())
                    .singleElement()
                    .isInstanceOfSatisfying(
                        AgentInstanceHistoryContent.TextContent.class,
                        text -> assertThat(text.getText()).isEqualTo("system prompt"));
                assertThat(item.getTools()).isEmpty();
              });
    }

    @Test
    void shouldReturnAgentInstanceKeyOnFirstAttemptWhenMaxModelCallsIsNull() {
      givenCreateCommand();
      when(createCommandStep2.execute()).thenReturn(response);
      when(response.getAgentInstanceKey()).thenReturn(67890L);

      final AgentInstanceKey key = client.create(TestAgentExecutionContext.withoutLimits());

      assertThat(key).isEqualTo(AgentInstanceKey.of(67890L));
      assertThat(recordedSleeps).isEmpty();
      verify(camundaClient, times(1)).newCreateAgentInstanceCommand();
      verify(createCommandStep2).jobKey(JOB_KEY);
      verify(createCommandStep2).history(any());
      verify(createCommandStep2, never()).maxModelCalls(anyInt());
    }

    @Test
    void shouldForwardLeaseTokenWhenActivationIsLeased() {
      givenCreateCommand();
      when(createCommandStep2.execute()).thenReturn(response);
      when(response.getAgentInstanceKey()).thenReturn(12345L);

      client.create(TestAgentExecutionContext.withLeaseToken("lease-token-abc"));

      verify(createCommandStep2).jobLease("lease-token-abc");
    }

    @Test
    void shouldThrowConnectorExceptionImmediatelyForHttp400PermanentError() {
      givenCreateCommand();
      when(createCommandStep2.execute()).thenThrow(new ClientHttpException(400, "Bad Request"));

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
      givenCreateCommand();
      when(createCommandStep2.execute())
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
      givenCreateCommand();
      when(createCommandStep2.execute()).thenThrow(new ClientHttpException(404, "Not Found"));

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
      givenCreateCommand();
      when(createCommandStep2.execute())
          .thenThrow(new ClientHttpException(500, "Internal Server Error"));

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
      givenCreateCommand();
      final var detail =
          "Command 'CREATE' rejected with code 'ALREADY_EXISTS': Expected to associate element "
              + "instance with key '77' with an agent instance, but it is already associated with "
              + "agent instance with key '999'.";
      when(createCommandStep2.execute()).thenThrow(conflictException(detail));

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
      givenCreateCommand();
      when(createCommandStep2.execute()).thenThrow(conflictException(detail));

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
  class ToolDiscoveryStart {

    @Test
    void shouldSkipWhenAgentInstanceKeyIsNull() {
      // when
      client.applyToolDiscoveryStart(TestAgentExecutionContext.withLimits(), null);

      // then
      verifyNoInteractions(camundaClient);
    }

    @Test
    void shouldSendOneBatchedUpdateWithToolDiscoveryStatusAndEmptyHistory() {
      givenUpdateCommand();

      // when
      client.applyToolDiscoveryStart(
          TestAgentExecutionContext.withLimits(), AgentInstanceKey.of(AGENT_INSTANCE_KEY));

      // then
      verify(updateCommandStep2).status(AgentInstanceUpdateStatus.TOOL_DISCOVERY);
      verify(updateCommandStep2).jobKey(JOB_KEY);
      verify(updateCommandStep2, never()).jobLease(any());
      verify(updateCommandStep2).history(List.of());
      verify(updateCommandStep2).execute();
    }

    @Test
    void shouldForwardLeaseTokenWhenActivationIsLeased() {
      givenUpdateCommand();

      // when
      client.applyToolDiscoveryStart(
          TestAgentExecutionContext.withLeaseToken("lease-token-abc"),
          AgentInstanceKey.of(AGENT_INSTANCE_KEY));

      // then
      verify(updateCommandStep2).jobLease("lease-token-abc");
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
                  client.applyToolDiscoveryStart(
                      TestAgentExecutionContext.withLimits(), agentInstanceKey))
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
          configuration,
          null,
          userTurn("hi", configuration.fingerprint()),
          Optional.empty(),
          TURN_INGESTION_TIMESTAMP);

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
          configuration,
          AgentInstanceKey.of(AGENT_INSTANCE_KEY),
          turn,
          Optional.of(precedingTurn(configuration.fingerprint())),
          TURN_INGESTION_TIMESTAMP);

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
          configuration,
          AgentInstanceKey.of(AGENT_INSTANCE_KEY),
          userTurn("hi", configuration.fingerprint()),
          Optional.of(precedingTurn(configuration.fingerprint())),
          TURN_INGESTION_TIMESTAMP);

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
          configuration,
          AgentInstanceKey.of(AGENT_INSTANCE_KEY),
          userTurn("hi", configuration.fingerprint()),
          Optional.of(precedingTurn(configuration.fingerprint())),
          TURN_INGESTION_TIMESTAMP);

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
          configuration,
          AgentInstanceKey.of(AGENT_INSTANCE_KEY),
          userTurn("hi", configuration.fingerprint()),
          Optional.empty(),
          TURN_INGESTION_TIMESTAMP);

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
      // model/provider are fixed at create time only, not re-pushed by turn-start items
      assertThat(configurationItem.getModel()).isNull();
      assertThat(configurationItem.getProvider()).isNull();
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
          configuration,
          AgentInstanceKey.of(AGENT_INSTANCE_KEY),
          userTurn("hi", configuration.fingerprint()),
          Optional.of(precedingTurn(previousConfiguration.fingerprint())),
          TURN_INGESTION_TIMESTAMP);

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
          configuration,
          AgentInstanceKey.of(AGENT_INSTANCE_KEY),
          userTurn("hi", configuration.fingerprint()),
          Optional.of(precedingTurn(previousConfiguration.fingerprint())),
          TURN_INGESTION_TIMESTAMP);

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
          configuration,
          AgentInstanceKey.of(AGENT_INSTANCE_KEY),
          turn,
          Optional.of(previousTurn),
          TURN_INGESTION_TIMESTAMP);

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
                      configuration,
                      AgentInstanceKey.of(AGENT_INSTANCE_KEY),
                      turn,
                      Optional.empty(),
                      TURN_INGESTION_TIMESTAMP))
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
   * Plan decision 3: a batched update (any of the three turn methods, or {@code
   * applyToolDiscoveryStart}) that gets rejected with 404 means the job activation was superseded,
   * and must fail without provoking any retry at any level.
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
                      configuration(),
                      AgentInstanceKey.of(AGENT_INSTANCE_KEY),
                      userMessageTurn(),
                      Optional.empty(),
                      TURN_INGESTION_TIMESTAMP))
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
    void shouldThrowNonRetryableConnectorRetryExceptionOn404ForApplyToolDiscoveryStart() {
      givenUpdateCommand();
      when(updateCommandStep2.execute()).thenThrow(new ClientHttpException(404, "Not Found"));

      assertThatThrownBy(
              () ->
                  client.applyToolDiscoveryStart(
                      TestAgentExecutionContext.withLimits(),
                      AgentInstanceKey.of(AGENT_INSTANCE_KEY)))
          .isInstanceOfSatisfying(
              ConnectorRetryException.class,
              e -> {
                assertThat(e.getRetries()).isEqualTo(0);
                assertThat(e.getErrorCode()).isEqualTo(ERROR_CODE_AGENT_INSTANCE_SUPERSEDED);
              });
      assertThat(recordedSleeps).isEmpty();
    }

    @Test
    void shouldKeepPermanentUpdateFailedForApplyTurnStartOn400() {
      givenUpdateCommand();
      when(updateCommandStep2.execute()).thenThrow(new ClientHttpException(400, "Bad Request"));

      assertThatThrownBy(
              () ->
                  client.applyTurnStart(
                      TestAgentExecutionContext.withLimits(),
                      configuration(),
                      AgentInstanceKey.of(AGENT_INSTANCE_KEY),
                      userMessageTurn(),
                      Optional.empty(),
                      TURN_INGESTION_TIMESTAMP))
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
                      configuration(),
                      AgentInstanceKey.of(AGENT_INSTANCE_KEY),
                      userMessageTurn(),
                      Optional.empty(),
                      TURN_INGESTION_TIMESTAMP))
          .isInstanceOfSatisfying(
              ConnectorException.class,
              e -> assertThat(e.getErrorCode()).isEqualTo(ERROR_CODE_AGENT_INSTANCE_UPDATE_FAILED))
          .isNotInstanceOf(ConnectorRetryException.class);
      assertThat(recordedSleeps).hasSize(4);
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
