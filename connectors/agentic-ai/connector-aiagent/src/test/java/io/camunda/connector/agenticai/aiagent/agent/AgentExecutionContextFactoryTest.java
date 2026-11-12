/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.agent;

import static io.camunda.connector.agenticai.aiagent.TestMessagesFixture.AD_HOC_TOOL_ELEMENTS;
import static io.camunda.connector.agenticai.aiagent.TestMessagesFixture.TOOL_CALL_RESULTS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import io.camunda.connector.agenticai.aiagent.configuration.AgenticAiConnectorsConfigurationProperties.ChatModelProperties;
import io.camunda.connector.agenticai.aiagent.configuration.AgenticAiConnectorsConfigurationProperties.ChatModelProperties.ApiProperties;
import io.camunda.connector.agenticai.aiagent.model.AgentConfiguration;
import io.camunda.connector.agenticai.aiagent.model.AgentContext;
import io.camunda.connector.agenticai.aiagent.model.AgentExecutionContext;
import io.camunda.connector.agenticai.aiagent.model.chatmodel.ChatModelConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.EventHandlingConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.EventHandlingConfiguration.EventHandlingBehavior;
import io.camunda.connector.agenticai.aiagent.model.request.LimitsConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.PromptConfiguration.SystemPromptConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.PromptConfiguration.UserPromptConfiguration;
import io.camunda.connector.agenticai.aiagent.request.AgentSubProcessRequestData;
import io.camunda.connector.agenticai.aiagent.request.AgentSubProcessV1Request;
import io.camunda.connector.agenticai.aiagent.request.AgentSubProcessV2Request;
import io.camunda.connector.agenticai.aiagent.request.AgentTaskRequestData;
import io.camunda.connector.agenticai.aiagent.request.AgentTaskV1Request;
import io.camunda.connector.agenticai.aiagent.request.AgentTaskV2Request;
import io.camunda.connector.agenticai.aiagent.request.ToolsConfiguration;
import io.camunda.connector.agenticai.aiagent.request.V1ToV2ProviderConfigurationMapperImpl;
import io.camunda.connector.agenticai.aiagent.request.shared.TimeoutConfiguration;
import io.camunda.connector.agenticai.aiagent.request.v1.AnthropicProviderConfiguration;
import io.camunda.connector.agenticai.aiagent.request.v1.AnthropicProviderConfiguration.AnthropicAuthentication;
import io.camunda.connector.agenticai.aiagent.request.v1.AnthropicProviderConfiguration.AnthropicConnection;
import io.camunda.connector.agenticai.aiagent.request.v1.AnthropicProviderConfiguration.AnthropicModel;
import io.camunda.connector.agenticai.aiagent.request.v2.AnthropicChatModelConfiguration;
import io.camunda.connector.agenticai.aiagent.request.v2.AnthropicChatModelConfiguration.AnthropicBackend.AnthropicApiBackend;
import io.camunda.connector.agenticai.aiagent.request.v2.AnthropicChatModelConfiguration.AnthropicBackend.AnthropicApiBackend.AnthropicApi;
import io.camunda.connector.agenticai.tools.schema.processdefinition.ProcessDefinitionAdHocToolElementsResolver;
import io.camunda.connector.api.outbound.JobContext;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AgentExecutionContextFactoryTest {

  private static final Duration DEFAULT_TIMEOUT = Duration.ofMinutes(3);
  private static final String CONTAINER_ELEMENT_ID = "tools-container";

  private static final AnthropicProviderConfiguration V1_PROVIDER =
      new AnthropicProviderConfiguration(
          new AnthropicConnection(
              null,
              new AnthropicAuthentication("anthropic-api-key"),
              null,
              new AnthropicModel("claude-sonnet-4-6", null)));

  private static final AnthropicChatModelConfiguration V1_PROVIDER_AS_V2 =
      new AnthropicChatModelConfiguration(
          new AnthropicChatModelConfiguration.AnthropicConnection(
              new AnthropicApiBackend(
                  new AnthropicApi("anthropic-api-key", null, null, null, null)),
              new AnthropicChatModelConfiguration.AnthropicModel("claude-sonnet-4-6", null),
              new TimeoutConfiguration(DEFAULT_TIMEOUT)));

  private static final AnthropicChatModelConfiguration V2_PROVIDER =
      new AnthropicChatModelConfiguration(
          new AnthropicChatModelConfiguration.AnthropicConnection(
              new AnthropicApiBackend(new AnthropicApi("v2-api-key", null, null, null, null)),
              new AnthropicChatModelConfiguration.AnthropicModel("claude-opus-4-6", null),
              null));

  private static final AgentContext AGENT_CONTEXT = AgentContext.empty();
  private static final SystemPromptConfiguration SYSTEM_PROMPT =
      new SystemPromptConfiguration("system prompt");
  private static final UserPromptConfiguration USER_PROMPT =
      new UserPromptConfiguration("user prompt", null);
  private static final LimitsConfiguration LIMITS = new LimitsConfiguration(7);
  private static final EventHandlingConfiguration EVENTS =
      new EventHandlingConfiguration(EventHandlingBehavior.INTERRUPT_TOOL_CALLS);

  private static final AgentTaskRequestData TASK_DATA =
      new AgentTaskRequestData(
          AGENT_CONTEXT,
          SYSTEM_PROMPT,
          USER_PROMPT,
          new ToolsConfiguration(CONTAINER_ELEMENT_ID, TOOL_CALL_RESULTS),
          null,
          LIMITS,
          null);

  private static final AgentSubProcessRequestData SUB_PROCESS_DATA =
      new AgentSubProcessRequestData(SYSTEM_PROMPT, USER_PROMPT, null, LIMITS, EVENTS, null);

  private final ProcessDefinitionAdHocToolElementsResolver toolElementsResolver =
      (physicalTenantId, processDefinitionKey, adHocSubProcessId) ->
          CONTAINER_ELEMENT_ID.equals(adHocSubProcessId) ? AD_HOC_TOOL_ELEMENTS : List.of();

  @Mock private OutboundConnectorContext context;
  @Mock private JobContext jobContext;

  @BeforeEach
  void setUp() {
    when(context.getJobContext()).thenReturn(jobContext);
  }

  @Test
  void taskContextOfV1RequestHasV2ProviderWhenRewriteIsEnabled() {
    var executionContext =
        factory(true).taskContext(context, new AgentTaskV1Request(V1_PROVIDER, TASK_DATA));

    assertTaskContext(executionContext, V1_PROVIDER_AS_V2);
  }

  @Test
  void taskContextOfV1RequestKeepsV1ProviderWhenRewriteIsDisabled() {
    var executionContext =
        factory(false).taskContext(context, new AgentTaskV1Request(V1_PROVIDER, TASK_DATA));

    assertTaskContext(executionContext, V1_PROVIDER);
  }

  @Test
  void taskContextOfV2RequestKeepsV2Provider() {
    var executionContext =
        factory(true).taskContext(context, new AgentTaskV2Request(V2_PROVIDER, TASK_DATA));

    assertTaskContext(executionContext, V2_PROVIDER);
  }

  @Test
  void subProcessContextOfV1RequestHasV2ProviderWhenRewriteIsEnabled() {
    var executionContext =
        factory(true)
            .subProcessContext(
                context,
                new AgentSubProcessV1Request(
                    AD_HOC_TOOL_ELEMENTS,
                    AGENT_CONTEXT,
                    TOOL_CALL_RESULTS,
                    V1_PROVIDER,
                    SUB_PROCESS_DATA));

    assertSubProcessContext(executionContext, V1_PROVIDER_AS_V2);
  }

  @Test
  void subProcessContextOfV1RequestKeepsV1ProviderWhenRewriteIsDisabled() {
    var executionContext =
        factory(false)
            .subProcessContext(
                context,
                new AgentSubProcessV1Request(
                    AD_HOC_TOOL_ELEMENTS,
                    AGENT_CONTEXT,
                    TOOL_CALL_RESULTS,
                    V1_PROVIDER,
                    SUB_PROCESS_DATA));

    assertSubProcessContext(executionContext, V1_PROVIDER);
  }

  @Test
  void subProcessContextOfV2RequestKeepsV2Provider() {
    var executionContext =
        factory(true)
            .subProcessContext(
                context,
                new AgentSubProcessV2Request(
                    AD_HOC_TOOL_ELEMENTS,
                    AGENT_CONTEXT,
                    TOOL_CALL_RESULTS,
                    V2_PROVIDER,
                    SUB_PROCESS_DATA));

    assertSubProcessContext(executionContext, V2_PROVIDER);
  }

  private AgentExecutionContextFactory factory(boolean rewriteV1ProviderConfigToV2) {
    return new AgentExecutionContextFactory(
        toolElementsResolver,
        new V1ToV2ProviderConfigurationMapperImpl(
            new ChatModelProperties(new ApiProperties(DEFAULT_TIMEOUT), null)),
        rewriteV1ProviderConfigToV2);
  }

  private void assertTaskContext(
      AgentTaskExecutionContext executionContext, ChatModelConfiguration expectedChatModel) {
    assertJobAndAgentState(executionContext);
    assertThat(executionContext.configuration())
        .isEqualTo(
            new AgentConfiguration(
                expectedChatModel, SYSTEM_PROMPT, USER_PROMPT, null, LIMITS, null, null));
  }

  private void assertSubProcessContext(
      AgentSubProcessExecutionContext executionContext, ChatModelConfiguration expectedChatModel) {
    assertJobAndAgentState(executionContext);
    assertThat(executionContext.configuration())
        .isEqualTo(
            new AgentConfiguration(
                expectedChatModel, SYSTEM_PROMPT, USER_PROMPT, null, LIMITS, EVENTS, null));
  }

  private void assertJobAndAgentState(AgentExecutionContext executionContext) {
    assertThat(executionContext.jobContext()).isSameAs(jobContext);
    assertThat(executionContext.documentFactory()).isSameAs(context);
    assertThat(executionContext.initialAgentContext()).isSameAs(AGENT_CONTEXT);
    assertThat(executionContext.initialToolCallResults()).isEqualTo(TOOL_CALL_RESULTS);
    assertThat(executionContext.toolElements()).isEqualTo(AD_HOC_TOOL_ELEMENTS);
  }
}
