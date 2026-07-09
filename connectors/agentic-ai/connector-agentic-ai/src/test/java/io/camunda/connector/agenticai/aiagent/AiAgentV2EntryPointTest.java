/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import io.camunda.connector.agenticai.adhoctoolsschema.processdefinition.ProcessDefinitionAdHocToolElementsResolver;
import io.camunda.connector.agenticai.aiagent.framework.ChatModelApiRegistryImpl;
import io.camunda.connector.agenticai.aiagent.framework.api.LlmProviderChatModelApiConfiguration;
import io.camunda.connector.agenticai.aiagent.framework.langchain4j.Langchain4JAiFrameworkAdapter;
import io.camunda.connector.agenticai.aiagent.framework.langchain4j.Langchain4JChatModelApiFactory;
import io.camunda.connector.agenticai.aiagent.model.OutboundConnectorAgentExecutionContext;
import io.camunda.connector.agenticai.aiagent.model.request.OutboundConnectorAgentRequest.OutboundConnectorAgentRequestData;
import io.camunda.connector.agenticai.aiagent.model.request.OutboundConnectorAgentRequestV2;
import io.camunda.connector.agenticai.aiagent.model.request.chatmodel.AnthropicChatModel;
import io.camunda.connector.agenticai.aiagent.model.request.chatmodel.AnthropicChatModel.AnthropicBackend.AnthropicDirectBackend;
import io.camunda.connector.agenticai.aiagent.model.request.chatmodel.AnthropicChatModel.AnthropicConnection;
import io.camunda.connector.agenticai.aiagent.model.request.chatmodel.AnthropicChatModel.AnthropicModel;
import io.camunda.connector.api.error.ConnectorException;
import io.camunda.connector.api.outbound.JobContext;
import java.util.List;
import org.junit.jupiter.api.Test;

class AiAgentV2EntryPointTest {

  private AnthropicChatModel anthropicConfig() {
    return new AnthropicChatModel(
        new AnthropicConnection(
            new AnthropicDirectBackend(null, "sk-ant"),
            new AnthropicModel("claude-sonnet-4-6", null),
            null,
            null));
  }

  @Test
  void v2EntryPointMappingWrapsLlmProviderConfigAndTelemetryWithoutNpe() {
    final var config = anthropicConfig();
    final var request =
        new OutboundConnectorAgentRequestV2(
            config,
            new OutboundConnectorAgentRequestData(null, null, null, null, null, null, null));

    // reproduces exactly what AiAgentTaskV2.execute builds
    final var ctx =
        new OutboundConnectorAgentExecutionContext(
            mock(JobContext.class),
            request.data(),
            new LlmProviderChatModelApiConfiguration(request.configuration()),
            request.configuration().model(),
            request.configuration().providerType(),
            mock(ProcessDefinitionAdHocToolElementsResolver.class));

    assertThat(ctx.configuration().chatModelApiConfiguration())
        .isEqualTo(new LlmProviderChatModelApiConfiguration(config));
    assertThat(ctx.configuration().modelName()).isEqualTo("claude-sonnet-4-6");
    assertThat(ctx.configuration().modelProvider()).isEqualTo("anthropic");
  }

  @Test
  void v2LlmProviderConfigFailsLoudThroughRegistryUntilProviderFactoryExists() {
    final var registry =
        new ChatModelApiRegistryImpl(
            List.of(new Langchain4JChatModelApiFactory(mock(Langchain4JAiFrameworkAdapter.class))));

    assertThatThrownBy(
            () -> registry.resolve(new LlmProviderChatModelApiConfiguration(anthropicConfig())))
        .isInstanceOf(ConnectorException.class)
        .hasMessageContaining("No chat model registered for configuration");
  }
}
