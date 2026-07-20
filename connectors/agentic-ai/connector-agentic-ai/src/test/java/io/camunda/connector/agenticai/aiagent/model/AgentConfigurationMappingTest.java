/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import io.camunda.connector.agenticai.adhoctoolsschema.processdefinition.ProcessDefinitionAdHocToolElementsResolver;
import io.camunda.connector.agenticai.aiagent.framework.api.V1ChatModelApiConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.OutboundConnectorAgentRequest.OutboundConnectorAgentRequestData;
import io.camunda.connector.agenticai.aiagent.model.request.provider.AnthropicProviderConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.provider.AnthropicProviderConfiguration.AnthropicAuthentication;
import io.camunda.connector.agenticai.aiagent.model.request.provider.AnthropicProviderConfiguration.AnthropicConnection;
import io.camunda.connector.agenticai.aiagent.model.request.provider.AnthropicProviderConfiguration.AnthropicModel;
import io.camunda.connector.api.outbound.JobContext;
import org.junit.jupiter.api.Test;

class AgentConfigurationMappingTest {

  @Test
  void outboundContextCarriesWrappedProviderConfigAndTelemetryStrings() {
    final var provider =
        new AnthropicProviderConfiguration(
            new AnthropicConnection(
                null,
                new AnthropicAuthentication("sk-ant"),
                null,
                new AnthropicModel("claude-sonnet-4-6", null)));
    final var data =
        new OutboundConnectorAgentRequestData(null, null, null, null, null, null, null);

    final var ctx =
        new OutboundConnectorAgentExecutionContext(
            mock(JobContext.class),
            data,
            new V1ChatModelApiConfiguration(provider),
            provider.model(),
            provider.providerType(),
            mock(ProcessDefinitionAdHocToolElementsResolver.class));

    assertThat(ctx.configuration().chatModelApiConfiguration())
        .isEqualTo(new V1ChatModelApiConfiguration(provider));
    assertThat(ctx.configuration().modelName()).isEqualTo("claude-sonnet-4-6");
    assertThat(ctx.configuration().modelProvider()).isEqualTo("anthropic");
  }
}
