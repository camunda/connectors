/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.a2a.discovery.systemprompt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.connector.agenticai.a2a.discovery.A2aGatewayToolHandler;
import io.camunda.connector.agenticai.aiagent.model.AgentContext;
import io.camunda.connector.agenticai.aiagent.model.AgentExecutionContext;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class A2aSystemPromptContributorTest {

  @Test
  void shouldContributeWhenA2aToolsPresent() {
    A2aSystemPromptContributor contributor = new A2aSystemPromptContributor();

    AgentExecutionContext executionContext = mock(AgentExecutionContext.class);
    AgentContext agentContext = mock(AgentContext.class);

    when(agentContext.properties())
        .thenReturn(Map.of(A2aGatewayToolHandler.PROPERTY_A2A_CLIENTS, List.of("RemoteAgent")));

    String result = contributor.contributeSystemPrompt(executionContext, agentContext);

    assertThat(result).isNotNull();
    assertThat(result).contains("A2A");
  }

  @Test
  void shouldNotContributeWhenNoA2aTools() {
    A2aSystemPromptContributor contributor = new A2aSystemPromptContributor();

    AgentExecutionContext executionContext = mock(AgentExecutionContext.class);
    AgentContext agentContext = mock(AgentContext.class);

    when(agentContext.properties()).thenReturn(Map.of());

    String result = contributor.contributeSystemPrompt(executionContext, agentContext);

    assertThat(result).isNull();
  }

  @Test
  void shouldHaveCorrectOrder() {
    A2aSystemPromptContributor contributor = new A2aSystemPromptContributor();

    assertThat(contributor.getOrder()).isEqualTo(100);
  }
}
