/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.agentinstance;

import static io.camunda.connector.agenticai.aiagent.agent.AgentErrorCodes.ERROR_CODE_AGENT_INSTANCE_CREATION_FAILED;
import static io.camunda.connector.agenticai.aiagent.agent.AgentErrorCodes.ERROR_CODE_AGENT_INSTANCE_UPDATE_FAILED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.AgentInstanceUpdateStatus;
import io.camunda.client.api.command.ClientHttpException;
import io.camunda.client.api.command.CreateAgentInstanceCommandStep1;
import io.camunda.client.api.command.CreateAgentInstanceCommandStep1.CreateAgentInstanceCommandStep5;
import io.camunda.client.api.command.UpdateAgentInstanceCommandStep1;
import io.camunda.client.api.command.UpdateAgentInstanceCommandStep1.UpdateAgentInstanceCommandStep2;
import io.camunda.client.api.response.CreateAgentInstanceResponse;
import io.camunda.connector.agenticai.adhoctoolsschema.model.AdHocToolElement;
import io.camunda.connector.agenticai.aiagent.model.AgentContext;
import io.camunda.connector.agenticai.aiagent.model.AgentExecutionContext;
import io.camunda.connector.agenticai.aiagent.model.AgentMetadata;
import io.camunda.connector.agenticai.aiagent.model.AgentMetrics;
import io.camunda.connector.agenticai.aiagent.model.AgentMetrics.TokenUsage;
import io.camunda.connector.agenticai.aiagent.model.AgentState;
import io.camunda.connector.agenticai.aiagent.model.request.EventHandlingConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.LimitsConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.MemoryConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.PromptConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.ResponseConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.provider.OpenAiProviderConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.provider.ProviderConfiguration;
import io.camunda.connector.agenticai.autoconfigure.AgenticAiConnectorsConfigurationProperties;
import io.camunda.connector.agenticai.model.tool.ToolCallResult;
import io.camunda.connector.api.error.ConnectorException;
import io.camunda.connector.api.outbound.JobContext;
import io.camunda.connector.runtime.test.outbound.TestJobContext;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CamundaAgentInstanceClientTest {

  private static final AgenticAiConnectorsConfigurationProperties.RetriesProperties
      RETRIES_CONFIGURATION =
          new AgenticAiConnectorsConfigurationProperties.RetriesProperties(
              4, Duration.ofSeconds(1));

  private static final long ELEMENT_INSTANCE_KEY = 77L;

  private static final long AGENT_INSTANCE_KEY = 999L;

  @Mock private CamundaClient camundaClient;

  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  private CreateAgentInstanceCommandStep1 commandChain;

  @Mock private CreateAgentInstanceResponse response;

  @Mock private UpdateAgentInstanceCommandStep1 updateCommandStep1;

  @Mock(answer = Answers.RETURNS_SELF)
  private UpdateAgentInstanceCommandStep2 updateCommandStep2;

  private CreateAgentInstanceCommandStep5 step5;

  private List<Duration> recordedSleeps;
  private CamundaAgentInstanceClient client;

  @BeforeEach
  void setUp() {
    recordedSleeps = new ArrayList<>();
    client =
        new CamundaAgentInstanceClient(camundaClient, RETRIES_CONFIGURATION, recordedSleeps::add);
    lenient().when(camundaClient.newCreateAgentInstanceCommand()).thenReturn(commandChain);
    step5 =
        commandChain
            .elementInstanceKey(ELEMENT_INSTANCE_KEY)
            .model("gpt-4o")
            .provider(OpenAiProviderConfiguration.OPENAI_ID)
            .systemPrompt("system prompt");
    lenient().when(step5.maxModelCalls(10)).thenReturn(step5);
    lenient()
        .when(camundaClient.newUpdateAgentInstanceCommand(AGENT_INSTANCE_KEY))
        .thenReturn(updateCommandStep1);
    lenient()
        .when(updateCommandStep1.elementInstanceKey(ELEMENT_INSTANCE_KEY))
        .thenReturn(updateCommandStep2);
  }

  @Nested
  class Create {

    @Test
    void shouldReturnAgentInstanceKeyOnFirstSuccessfulAttempt() {
      when(step5.execute()).thenReturn(response);
      when(response.getAgentInstanceKey()).thenReturn(12345L);

      final AgentInstanceKey key = client.create(TestAgentExecutionContext.withLimits());

      assertThat(key).isEqualTo(AgentInstanceKey.of(12345L));
      assertThat(recordedSleeps).isEmpty();
      verify(camundaClient, times(1)).newCreateAgentInstanceCommand();
    }

    @Test
    void shouldReturnAgentInstanceKeyOnFirstAttemptWhenMaxModelCallsIsNull() {
      when(step5.execute()).thenReturn(response);
      when(response.getAgentInstanceKey()).thenReturn(67890L);

      final AgentInstanceKey key = client.create(TestAgentExecutionContext.withoutLimits());

      assertThat(key).isEqualTo(AgentInstanceKey.of(67890L));
      assertThat(recordedSleeps).isEmpty();
      verify(camundaClient, times(1)).newCreateAgentInstanceCommand();
    }

    @Test
    void shouldThrowConnectorExceptionImmediatelyForHttp400PermanentError() {
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
    void shouldSilentlySkipWhenMetadataIsNull() {
      // given
      final var agentContext = AgentContext.builder().state(AgentState.READY).build();

      // when
      client.update(
          TestAgentExecutionContext.withLimits(),
          agentContext,
          AgentInstanceUpdateRequest.statusOnly(AgentInstanceUpdateStatus.THINKING));

      // then
      verifyNoInteractions(camundaClient);
    }

    @Test
    void shouldSilentlySkipWhenAgentInstanceKeyIsNull() {
      // given
      final var metadata = new AgentMetadata(1L, 2L, null);
      final var agentContext =
          AgentContext.builder().state(AgentState.READY).metadata(metadata).build();

      // when
      client.update(
          TestAgentExecutionContext.withLimits(),
          agentContext,
          AgentInstanceUpdateRequest.statusOnly(AgentInstanceUpdateStatus.THINKING));

      // then
      verifyNoInteractions(camundaClient);
    }

    @Test
    void shouldBuildCommandWithStatusOnly() {
      // given
      final var agentContext = agentContextWithInstanceKey();

      // when
      client.update(
          TestAgentExecutionContext.withLimits(),
          agentContext,
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
      // given
      final var agentContext = agentContextWithInstanceKey();
      final var delta = new AgentMetrics(1, new TokenUsage(10, 20), 0);
      final var request =
          AgentInstanceUpdateRequest.builder()
              .status(AgentInstanceUpdateStatus.IDLE)
              .delta(delta)
              .build();

      // when
      client.update(TestAgentExecutionContext.withLimits(), agentContext, request);

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
      // given
      final var agentContext = agentContextWithInstanceKey();
      final var delta = new AgentMetrics(2, new TokenUsage(50, 100), 3);
      final var request =
          AgentInstanceUpdateRequest.builder()
              .status(AgentInstanceUpdateStatus.TOOL_CALLING)
              .delta(delta)
              .build();

      // when
      client.update(TestAgentExecutionContext.withLimits(), agentContext, request);

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
    void shouldThrowConnectorExceptionImmediatelyFor404PermanentError() {
      // given
      final var agentContext = agentContextWithInstanceKey();
      when(updateCommandStep2.execute()).thenThrow(new ClientHttpException(404, "Not Found"));

      // when / then
      assertThatThrownBy(
              () ->
                  client.update(
                      TestAgentExecutionContext.withLimits(),
                      agentContext,
                      AgentInstanceUpdateRequest.statusOnly(AgentInstanceUpdateStatus.THINKING)))
          .isInstanceOfSatisfying(
              ConnectorException.class,
              e -> assertThat(e.getErrorCode()).isEqualTo(ERROR_CODE_AGENT_INSTANCE_UPDATE_FAILED));

      // 404 is PERMANENT for update → single attempt, no sleeps
      assertThat(recordedSleeps).isEmpty();
      verify(camundaClient, times(1)).newUpdateAgentInstanceCommand(AGENT_INSTANCE_KEY);
    }

    @Test
    void shouldThrowConnectorExceptionWithAttemptCountWhenAllRetriesExhausted() {
      // given
      final var agentContext = agentContextWithInstanceKey();
      when(updateCommandStep2.execute())
          .thenThrow(new ClientHttpException(500, "Internal Server Error"));

      // when / then
      assertThatThrownBy(
              () ->
                  client.update(
                      TestAgentExecutionContext.withLimits(),
                      agentContext,
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

    private static AgentContext agentContextWithInstanceKey() {
      final var metadata = new AgentMetadata(1L, 2L, AGENT_INSTANCE_KEY);
      return AgentContext.builder().state(AgentState.READY).metadata(metadata).build();
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
    public ProviderConfiguration provider() {
      return new OpenAiProviderConfiguration(
          new OpenAiProviderConfiguration.OpenAiConnection(
              null, null, new OpenAiProviderConfiguration.OpenAiModel("gpt-4o", null)));
    }

    @Override
    public PromptConfiguration.SystemPromptConfiguration systemPrompt() {
      return new PromptConfiguration.SystemPromptConfiguration("system prompt");
    }

    @Override
    public PromptConfiguration.UserPromptConfiguration userPrompt() {
      return null;
    }

    @Override
    public MemoryConfiguration memory() {
      return null;
    }

    @Override
    public LimitsConfiguration limits() {
      return limitsConfiguration;
    }

    @Override
    public EventHandlingConfiguration events() {
      return null;
    }

    @Override
    public ResponseConfiguration response() {
      return null;
    }
  }
}
