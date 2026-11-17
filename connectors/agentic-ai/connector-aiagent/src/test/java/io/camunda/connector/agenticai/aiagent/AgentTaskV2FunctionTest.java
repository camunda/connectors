/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.connector.agenticai.aiagent.agent.AgentTaskExecutionContext;
import io.camunda.connector.agenticai.aiagent.agent.AgentTaskRequestHandler;
import io.camunda.connector.agenticai.aiagent.model.AgentFeatures;
import io.camunda.connector.agenticai.aiagent.model.request.PromptConfiguration.SystemPromptConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.PromptConfiguration.UserPromptConfiguration;
import io.camunda.connector.agenticai.aiagent.request.AgentTaskRequestData;
import io.camunda.connector.agenticai.aiagent.request.AgentTaskV2Request;
import io.camunda.connector.agenticai.aiagent.request.v2.AnthropicChatModelConfiguration;
import io.camunda.connector.agenticai.aiagent.request.v2.AnthropicChatModelConfiguration.AnthropicBackend.AnthropicApiBackend;
import io.camunda.connector.agenticai.aiagent.request.v2.AnthropicChatModelConfiguration.AnthropicBackend.AnthropicApiBackend.AnthropicApi;
import io.camunda.connector.agenticai.tools.schema.processdefinition.ProcessDefinitionAdHocToolElementsResolver;
import io.camunda.connector.api.outbound.JobContext;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AgentTaskV2FunctionTest {

  private static final AnthropicChatModelConfiguration PROVIDER =
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

  @Mock private ProcessDefinitionAdHocToolElementsResolver toolElementsResolver;
  @Mock private AgentTaskRequestHandler agentRequestHandler;
  @Mock private OutboundConnectorContext context;
  @Mock private JobContext jobContext;

  @Captor private ArgumentCaptor<AgentTaskExecutionContext> executionContextCaptor;

  @Test
  void givesNewAgentInstancesTheAgentInstanceFeatures() {
    when(context.bindVariables(AgentTaskV2Request.class))
        .thenReturn(new AgentTaskV2Request(PROVIDER, DATA));
    when(context.getJobContext()).thenReturn(jobContext);
    final var function = new AgentTaskV2Function(toolElementsResolver, agentRequestHandler);

    function.execute(context);

    verify(agentRequestHandler).handleRequest(executionContextCaptor.capture());
    assertThat(executionContextCaptor.getValue().newInstanceFeatures())
        .containsExactly(AgentFeatures.RUNTIME_TOOLS, AgentFeatures.DOCUMENTS);
  }
}
