/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.camunda.connector.agenticai.adhoctoolsschema.processdefinition.ProcessDefinitionAdHocToolElementsResolver;
import io.camunda.connector.agenticai.aiagent.agent.AgentTaskRequestHandler;
import io.camunda.connector.agenticai.aiagent.model.AgentTaskExecutionContext;
import io.camunda.connector.agenticai.aiagent.model.request.AgentTaskRequestData;
import io.camunda.connector.agenticai.aiagent.model.request.AgentTaskV1Request;
import io.camunda.connector.agenticai.aiagent.model.request.PromptConfiguration.SystemPromptConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.PromptConfiguration.UserPromptConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.V1ToV2ProviderConfigurationMapper;
import io.camunda.connector.agenticai.aiagent.model.request.v1.AnthropicProviderConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.v1.AnthropicProviderConfiguration.AnthropicAuthentication;
import io.camunda.connector.agenticai.aiagent.model.request.v1.AnthropicProviderConfiguration.AnthropicConnection;
import io.camunda.connector.agenticai.aiagent.model.request.v1.AnthropicProviderConfiguration.AnthropicModel;
import io.camunda.connector.agenticai.aiagent.model.request.v2.AnthropicChatModelConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.v2.AnthropicChatModelConfiguration.AnthropicBackend.AnthropicApiBackend;
import io.camunda.connector.agenticai.aiagent.model.request.v2.AnthropicChatModelConfiguration.AnthropicBackend.AnthropicApiBackend.AnthropicApi;
import io.camunda.connector.api.outbound.JobContext;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AgentTaskV1FunctionTest {

  private static final AnthropicProviderConfiguration V1_PROVIDER =
      new AnthropicProviderConfiguration(
          new AnthropicConnection(
              null,
              new AnthropicAuthentication("anthropic-api-key"),
              null,
              new AnthropicModel("claude-sonnet-4-6", null)));

  private static final AnthropicChatModelConfiguration V2_PROVIDER =
      new AnthropicChatModelConfiguration(
          new AnthropicChatModelConfiguration.AnthropicConnection(
              new AnthropicApiBackend(
                  new AnthropicApi("anthropic-api-key", null, null, null, null)),
              new AnthropicChatModelConfiguration.AnthropicModel("claude-sonnet-4-6", null),
              null));

  private static final AgentTaskRequestData DATA =
      new AgentTaskRequestData(
          null,
          new SystemPromptConfiguration("system prompt"),
          new UserPromptConfiguration("user prompt", null),
          null,
          null,
          null,
          null);

  private static final AgentTaskV1Request REQUEST = new AgentTaskV1Request(V1_PROVIDER, DATA);

  @Mock private ProcessDefinitionAdHocToolElementsResolver toolElementsResolver;
  @Mock private AgentTaskRequestHandler agentRequestHandler;
  @Mock private V1ToV2ProviderConfigurationMapper providerConfigurationMapper;
  @Mock private OutboundConnectorContext context;
  @Mock private JobContext jobContext;

  @Captor private ArgumentCaptor<AgentTaskExecutionContext> executionContextCaptor;

  @BeforeEach
  void setUp() {
    when(context.bindVariables(AgentTaskV1Request.class)).thenReturn(REQUEST);
    when(context.getJobContext()).thenReturn(jobContext);
  }

  @Test
  void routesThroughNativeProviderWhenSwitchIsEnabled() {
    when(providerConfigurationMapper.map(V1_PROVIDER)).thenReturn(V2_PROVIDER);
    var function =
        new AgentTaskV1Function(
            toolElementsResolver, agentRequestHandler, providerConfigurationMapper, true);

    function.execute(context);

    verify(agentRequestHandler).handleRequest(executionContextCaptor.capture());
    var chatModel = executionContextCaptor.getValue().configuration().chatModel();
    assertThat(chatModel).isInstanceOf(AnthropicChatModelConfiguration.class).isSameAs(V2_PROVIDER);
  }

  @Test
  void keepsV1ProviderConfigurationWhenSwitchIsDisabled() {
    var function =
        new AgentTaskV1Function(
            toolElementsResolver, agentRequestHandler, providerConfigurationMapper, false);

    function.execute(context);

    verify(agentRequestHandler).handleRequest(executionContextCaptor.capture());
    verifyNoInteractions(providerConfigurationMapper);
    var chatModel = executionContextCaptor.getValue().configuration().chatModel();
    assertThat(chatModel).isSameAs(V1_PROVIDER);
  }
}
