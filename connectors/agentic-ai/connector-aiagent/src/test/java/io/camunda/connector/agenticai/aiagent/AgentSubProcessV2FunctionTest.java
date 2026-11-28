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

import io.camunda.connector.agenticai.aiagent.agent.AgentExecutionContextFactory;
import io.camunda.connector.agenticai.aiagent.agent.AgentSubProcessExecutionContext;
import io.camunda.connector.agenticai.aiagent.agent.AgentSubProcessRequestHandler;
import io.camunda.connector.agenticai.aiagent.model.AgentFeatures;
import io.camunda.connector.agenticai.aiagent.model.document.DocumentDelivery;
import io.camunda.connector.agenticai.aiagent.model.request.DocumentsConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.PromptConfiguration.SystemPromptConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.PromptConfiguration.UserPromptConfiguration;
import io.camunda.connector.agenticai.aiagent.request.AgentSubProcessRequestData;
import io.camunda.connector.agenticai.aiagent.request.AgentSubProcessV2Request;
import io.camunda.connector.agenticai.aiagent.request.v2.AnthropicChatModelConfiguration;
import io.camunda.connector.agenticai.aiagent.request.v2.AnthropicChatModelConfiguration.AnthropicBackend.AnthropicApiBackend;
import io.camunda.connector.agenticai.aiagent.request.v2.AnthropicChatModelConfiguration.AnthropicBackend.AnthropicApiBackend.AnthropicApi;
import io.camunda.connector.api.outbound.JobContext;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AgentSubProcessV2FunctionTest {

  private static final AnthropicChatModelConfiguration PROVIDER =
      new AnthropicChatModelConfiguration(
          new AnthropicChatModelConfiguration.AnthropicConnection(
              new AnthropicApiBackend(
                  new AnthropicApi("anthropic-api-key", null, null, null, null)),
              new AnthropicChatModelConfiguration.AnthropicModel("claude-sonnet-4-6", null),
              null));

  private static AgentSubProcessRequestData data(@Nullable DocumentsConfiguration documents) {
    return new AgentSubProcessRequestData(
        new SystemPromptConfiguration("system prompt"),
        new UserPromptConfiguration("user prompt", null),
        documents,
        null,
        null,
        null,
        null);
  }

  @Mock private AgentSubProcessRequestHandler agentRequestHandler;
  @Mock private OutboundConnectorContext context;
  @Mock private JobContext jobContext;

  @Captor private ArgumentCaptor<AgentSubProcessExecutionContext> executionContextCaptor;

  @Test
  void passesTheDocumentConfigurationOfTheElement() throws Exception {
    final var documents = new DocumentsConfiguration(true, DocumentDelivery.REFERENCE);
    final var executionContext = executionContext(documents);

    assertThat(executionContext.configuration().documents()).isEqualTo(documents);
    assertThat(executionContext.newInstanceFeatures())
        .containsExactly(AgentFeatures.RUNTIME_TOOLS, AgentFeatures.DOCUMENTS);
  }

  @Test
  void hasDocumentReferencesOffWhenTheElementHasNoDocumentConfiguration() throws Exception {
    final var executionContext = executionContext(null);

    assertThat(executionContext.configuration().documents())
        .isEqualTo(DocumentsConfiguration.REFERENCES_OFF);
    assertThat(executionContext.newInstanceFeatures()).containsExactly(AgentFeatures.RUNTIME_TOOLS);
  }

  private AgentSubProcessExecutionContext executionContext(
      @Nullable DocumentsConfiguration documents) throws Exception {
    when(context.bindVariables(AgentSubProcessV2Request.class))
        .thenReturn(
            new AgentSubProcessV2Request(List.of(), null, List.of(), PROVIDER, data(documents)));
    when(context.getJobContext()).thenReturn(jobContext);
    final var function =
        new AgentSubProcessV2Function(
            new AgentExecutionContextFactory(null, null), agentRequestHandler);

    function.execute(context);

    verify(agentRequestHandler).handleRequest(executionContextCaptor.capture());
    return executionContextCaptor.getValue();
  }
}
